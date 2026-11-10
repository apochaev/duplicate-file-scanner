package us.pochaev.dfs.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static us.pochaev.dfs.server.ScanApiTest.JSON;
import static us.pochaev.dfs.server.ScanApiTest.KB300;
import static us.pochaev.dfs.server.ScanApiTest.awaitTerminal;
import static us.pochaev.dfs.server.ScanApiTest.body;
import static us.pochaev.dfs.server.ScanApiTest.fixture;
import static us.pochaev.dfs.server.ScanApiTest.start;
import static us.pochaev.dfs.server.ScanApiTest.startBody;

/** Phase totals and the finish time over HTTP (DFS-064). The registry's clock and phase logic is in ScanRegistryPhasesTest. */
@ExtendWith(OutputCaptureExtension.class)
class ScanPhasesApiTest {

    @Test
    @DisplayName("DFS-064.AC-5 A queued scan shows phases [] and finishedAt null on GET, on the list and in the first progress event")
    void aQueuedScanHasAnEmptyPhasesListAndNoFinishedAt(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = fixture(tmp);
        try (TestServer server = TestServer.start(new Class<?>[]{HoldingExecutorConfiguration.class})) {
            String cookie = server.login(server.token(output.getOut()));
            String id = start(server, cookie, startBody(t));

            JsonNode one = body(server.get("/api/scans/" + id, cookie));
            JsonNode listed = body(server.get("/api/scans", cookie)).path(0);

            for (JsonNode entry : List.of(one, listed)) {
                assertEquals("QUEUED", entry.path("progress").path("status").asString(), entry.toString());
                assertTrue(entry.has("finishedAt"), "finishedAt missing: " + entry);
                assertTrue(entry.path("finishedAt").isNull(), entry.toString());
                assertTrue(entry.path("progress").path("phases").isArray(), entry.toString());
                assertEquals(0, entry.path("progress").path("phases").size(), entry.toString());
            }

            JsonNode first = firstEvent(server, cookie, id);
            assertTrue(first.path("phases").isArray(), "phases missing from the event: " + first);
            assertEquals(0, first.path("phases").size(), first.toString());
        }
    }

    @Test
    @DisplayName("DFS-064.AC-2, DFS-064.AC-4, DFS-064.AC-5 A real scan lists its four phases finished in run order, with one fixed finishedAt")
    void aRealScanListsItsPhasesAndFinishes(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = fixture(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = start(server, cookie, startBody(t));

            JsonNode done = awaitTerminal(server, cookie, id);

            assertEquals("COMPLETED", done.path("progress").path("status").asString(), done.toString());
            JsonNode phases = done.path("progress").path("phases");
            assertEquals(List.of("WALK", "PARTIAL_HASH", "FULL_HASH", "HARD_LINKS"),
                    phases.valueStream().map(p -> p.path("phase").asString()).toList(), phases.toString());
            assertTrue(phases.valueStream().allMatch(p -> p.path("finished").asBoolean()), phases.toString());
            JsonNode walk = phases.path(0);
            assertEquals(2, walk.path("directories").asLong(), phases.toString());
            assertEquals(4, walk.path("files").asLong(), phases.toString());
            assertEquals(4L * KB300, walk.path("bytes").asLong(), phases.toString());

            // finishedAt: set, not before startedAt, and identical on every later read and on the list
            Instant started = Instant.parse(done.path("startedAt").asString());
            assertFalse(done.path("finishedAt").isNull(), done.toString());
            Instant finished = Instant.parse(done.path("finishedAt").asString());
            assertFalse(finished.isBefore(started), done.toString());
            Thread.sleep(150);
            assertEquals(done.path("finishedAt"), body(server.get("/api/scans/" + id, cookie)).path("finishedAt"));
            assertEquals(done.path("finishedAt"), body(server.get("/api/scans", cookie)).path(0).path("finishedAt"));
        }
    }

    @Test
    @DisplayName("DFS-064.AC-2 A real scan's hash phases keep their files and bytes and have no groups")
    void aRealScansHashPhasesKeepTheirTotals(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = fixture(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = start(server, cookie, startBody(t));

            JsonNode done = awaitTerminal(server, cookie, id);

            JsonNode phases = done.path("progress").path("phases");
            assertEquals("COMPLETED", done.path("progress").path("status").asString(), done.toString());
            for (int i : new int[]{1, 2}) {
                JsonNode p = phases.path(i);
                assertTrue(p.path("files").asLong() >= 3, "files of " + p);
                assertTrue(p.path("bytes").asLong() >= 3L * KB300, "bytes of " + p);
                assertEquals(0, p.path("groups").asLong(), "groups of " + p);
            }
        }
    }

    /** The first "progress" event of the stream, then the stream is closed (a queued scan never ends). */
    private static JsonNode firstEvent(TestServer server, String cookie, String id) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(server.base() + "/api/scans/" + id + "/events"))
                .header("Cookie", cookie).header("Accept", "text/event-stream").timeout(Duration.ofSeconds(20)).GET().build();
        try (HttpClient c = TestServer.client()) {
            HttpResponse<Stream<String>> r = c.send(request, HttpResponse.BodyHandlers.ofLines());
            assertEquals(200, r.statusCode());
            try (Stream<String> lines = r.body()) {
                Iterator<String> it = lines.iterator();
                while (it.hasNext()) {
                    String line = it.next();
                    if (line.startsWith("data:")) {
                        return JSON.readTree(line.substring(5).trim());
                    }
                }
            }
        }
        throw new AssertionError("the stream sent no event");
    }
}
