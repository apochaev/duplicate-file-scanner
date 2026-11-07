package us.pochaev.dfs.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The scan API: start, list, get, cancel and the progress stream (DFS-040). */
@ExtendWith(OutputCaptureExtension.class)
class ScanApiTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final int KB300 = 300 * 1024;
    static final Set<String> TERMINAL = Set.of("COMPLETED", "CANCELLED", "FAILED");
    static final String UNKNOWN = "00000000-0000-0000-0000-000000000000";

    /** T: a.bin and b.bin identical, c.bin unique, sub/d.bin a copy of a.bin; 300 KB each. */
    static Path fixture(Path t) throws IOException {
        byte[] dup = bytes(KB300, 1);
        Files.write(t.resolve("a.bin"), dup);
        Files.write(t.resolve("b.bin"), dup);
        Files.write(t.resolve("c.bin"), bytes(KB300, 2));
        Files.createDirectories(t.resolve("sub"));
        Files.write(t.resolve("sub/d.bin"), dup);
        return t;
    }

    static byte[] bytes(int size, int seed) {
        byte[] b = new byte[size];
        for (int i = 0; i < size; i++) {
            b[i] = (byte) (i * 31 + seed * 17 + (i >> 8));
        }
        return b;
    }

    static String json(String s) {
        return JSON.writeValueAsString(s);
    }

    static String startBody(Path... locations) {
        return "{\"locations\": [" + String.join(", ", Arrays.stream(locations).map(p -> json(p.toString())).toList()) + "]}";
    }

    static JsonNode body(HttpResponse<String> r) {
        return JSON.readTree(r.body());
    }

    /** Starts a scan and returns its ID, asserting 201. */
    static String start(TestServer server, String cookie, String body) throws Exception {
        HttpResponse<String> r = server.sendJson("POST", "/api/scans", cookie, server.origin(), body);
        assertEquals(201, r.statusCode(), r.body());
        return body(r).path("id").asString();
    }

    /** Polls GET /api/scans/{id} until its status is terminal; returns the last entry. */
    static JsonNode awaitTerminal(TestServer server, String cookie, String id) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (true) {
            JsonNode entry = body(server.get("/api/scans/" + id, cookie));
            if (TERMINAL.contains(entry.path("progress").path("status").asString())) {
                return entry;
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("scan " + id + " did not finish: " + entry);
            }
            Thread.sleep(50);
        }
    }

    record Event(long atMillis, JsonNode data) {
    }

    /** Reads GET /api/scans/{id}/events to its end: each "progress" event with its arrival time. */
    static List<Event> stream(TestServer server, String cookie, String id, StringBuilder contentType) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(server.base() + "/api/scans/" + id + "/events"))
                .header("Cookie", cookie).header("Accept", "text/event-stream").timeout(Duration.ofSeconds(90)).GET().build();
        List<Event> events = new ArrayList<>();
        long t0 = System.nanoTime();
        try (HttpClient c = TestServer.client()) {
            HttpResponse<Stream<String>> r = c.send(request, HttpResponse.BodyHandlers.ofLines());
            assertEquals(200, r.statusCode());
            contentType.append(r.headers().firstValue("Content-Type").orElse(""));
            String name = null;
            for (String line : (Iterable<String>) r.body()::iterator) {
                if (line.startsWith("event:")) {
                    name = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    assertEquals("progress", name, "event name");
                    events.add(new Event((System.nanoTime() - t0) / 1_000_000, JSON.readTree(line.substring(5).trim())));
                }
            }
        }
        return events;
    }

    static String status(JsonNode entryOrProgress) {
        JsonNode p = entryOrProgress.has("progress") ? entryOrProgress.path("progress") : entryOrProgress;
        return p.path("status").asString();
    }

    /** Writes n files of the given size: pairs of identical content, so every file reaches the hashing stages. */
    static void tree(Path dir, int n, int size) throws IOException {
        Files.createDirectories(dir);
        for (int i = 0; i < n; i++) {
            Files.write(dir.resolve("f" + i + ".bin"), bytes(size, i / 2));
        }
    }

    @Test
    @DisplayName("DFS-040.AC-1 A scan starts with 201 and Location, and completes with the tree's counts")
    void scanStartsAndCompletes(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = fixture(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));

            HttpResponse<String> r = server.sendJson("POST", "/api/scans", cookie, server.origin(), startBody(t));

            assertEquals(201, r.statusCode(), r.body());
            JsonNode entry = body(r);
            String id = entry.path("id").asString();
            assertEquals("/api/scans/" + id, r.headers().firstValue("Location").orElse(""));
            assertEquals(t.toString(), entry.path("locations").path(0).asString());
            assertFalse(entry.path("startedAt").asString().isEmpty(), entry.toString());
            assertFalse(status(entry).isEmpty(), entry.toString());

            JsonNode done = awaitTerminal(server, cookie, id);
            JsonNode p = done.path("progress");
            assertEquals("COMPLETED", p.path("status").asString(), done.toString());
            assertEquals(2, p.path("directories").asLong(), done.toString());
            assertEquals(4, p.path("files").asLong(), done.toString());
            assertEquals(4L * KB300, p.path("bytes").asLong(), done.toString());
            assertEquals(1, p.path("groupsTotal").asInt(), done.toString());
        }
    }

    @ParameterizedTest(name = "DFS-040.AC-2 {0} is refused with 400 {1}")
    @CsvSource(delimiter = '|', value = {
            "relative path      | RELATIVE_LOCATION",
            "minSize 0          | INVALID_MIN_SIZE",
            "no locations       | NO_LOCATIONS",
            "missing folder     | LOCATION_NOT_FOUND",
            "a file             | NOT_A_DIRECTORY",
    })
    @DisplayName("DFS-040.AC-2 Bad input is refused with 400 and its code, and starts nothing")
    void badInputIsRefused(String kind, String code, @TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = fixture(tmp);
        String body = switch (kind) {
            case "relative path" -> "{\"locations\": [\"relative/dir\"]}";
            case "minSize 0" -> "{\"locations\": [" + json(t.toString()) + "], \"minSize\": 0}";
            case "no locations" -> "{\"locations\": []}";
            case "missing folder" -> startBody(t.resolve("missing"));
            case "a file" -> startBody(t.resolve("c.bin"));
            default -> throw new IllegalArgumentException(kind);
        };
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));

            HttpResponse<String> r = server.sendJson("POST", "/api/scans", cookie, server.origin(), body);

            assertEquals(400, r.statusCode(), r.body());
            assertEquals(code, body(r).path("code").asString(), r.body());
            assertFalse(body(r).path("message").asString().isEmpty(), r.body());
            assertEquals(0, body(server.get("/api/scans", cookie)).size());
        }
    }

    @Test
    @DisplayName("DFS-040.AC-2 minSize and defaultExcludes reach the scan")
    void settingsReachTheScan(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = fixture(tmp);
        byte[] meta = bytes(4096, 9);
        Files.write(t.resolve(".DS_Store"), meta);
        Files.write(t.resolve("sub/.DS_Store"), meta);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String loc = json(t.toString());

            String big = start(server, cookie, "{\"locations\": [" + loc + "], \"minSize\": 400000}");
            assertEquals(0, awaitTerminal(server, cookie, big).path("progress").path("groupsTotal").asInt());

            String all = start(server, cookie, "{\"locations\": [" + loc + "], \"defaultExcludes\": false}");
            assertEquals(2, awaitTerminal(server, cookie, all).path("progress").path("groupsTotal").asInt());

            String defaults = start(server, cookie, "{\"locations\": [" + loc + "]}");
            assertEquals(1, awaitTerminal(server, cookie, defaults).path("progress").path("groupsTotal").asInt());
        }
    }

    @Test
    @DisplayName("DFS-040.AC-3 This session's scans are listed newest first")
    void scansAreListedNewestFirst(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = fixture(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            HttpResponse<String> empty = server.get("/api/scans", cookie);
            assertEquals(200, empty.statusCode(), empty.body());
            assertEquals(0, body(empty).size(), empty.body());

            String first = start(server, cookie, startBody(t));
            String second = start(server, cookie, startBody(t.resolve("sub")));

            JsonNode list = body(server.get("/api/scans", cookie));
            assertEquals(2, list.size(), list.toString());
            assertEquals(second, list.path(0).path("id").asString());
            assertEquals(first, list.path(1).path("id").asString());
            assertEquals(t.resolve("sub").toString(), list.path(0).path("locations").path(0).asString());
            for (JsonNode e : list) {
                assertFalse(e.path("startedAt").asString().isEmpty(), e.toString());
                assertFalse(status(e).isEmpty(), e.toString());
            }
        }
    }

    @Test
    @DisplayName("DFS-040.AC-4 The stream sends progress at once, at most every 250 ms, and ends at the terminal status")
    void streamFollowsAScan(@TempDir Path tmp, CapturedOutput output) throws Exception {
        tree(tmp.resolve("big"), 2000, 2048);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = start(server, cookie, startBody(tmp.resolve("big")));

            StringBuilder type = new StringBuilder();
            List<Event> events = stream(server, cookie, id, type);

            assertTrue(type.toString().startsWith("text/event-stream"), type.toString());
            assertFalse(events.isEmpty(), "no events");
            assertTrue(events.getFirst().atMillis() < 1000, "first event after " + events.getFirst().atMillis() + " ms");
            for (int i = 1; i < events.size(); i++) {
                long gap = events.get(i).atMillis() - events.get(i - 1).atMillis();
                assertTrue(gap >= 200, "events " + (i - 1) + " and " + i + " only " + gap + " ms apart");
            }
            assertEquals("COMPLETED", status(events.getLast().data()));
            for (int i = 0; i < events.size() - 1; i++) {
                assertFalse(TERMINAL.contains(status(events.get(i).data())), "terminal event before the last: " + i);
            }
        }
    }

    @Test
    @DisplayName("DFS-040.AC-4 The stream of a finished scan sends one COMPLETED event and ends")
    void streamOfAFinishedScan(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = fixture(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = start(server, cookie, startBody(t));
            awaitTerminal(server, cookie, id);

            List<Event> events = stream(server, cookie, id, new StringBuilder());

            assertEquals(1, events.size(), events.toString());
            assertEquals("COMPLETED", status(events.getFirst().data()));
        }
    }

    @Test
    @DisplayName("DFS-040.AC-5 Cancelling a QUEUED scan makes it CANCELLED at once")
    void cancelQueuedScan(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = fixture(tmp);
        try (TestServer server = TestServer.start(new Class<?>[]{HoldingExecutorConfiguration.class})) {
            String cookie = server.login(server.token(output.getOut()));
            String id = start(server, cookie, startBody(t));
            assertEquals("QUEUED", status(body(server.get("/api/scans/" + id, cookie))));

            HttpResponse<String> r = server.sendJson("POST", "/api/scans/" + id + "/cancel", cookie, server.origin(), "");

            assertEquals(200, r.statusCode(), r.body());
            assertEquals("CANCELLED", status(body(r)), r.body());
        }
    }

    @Test
    @DisplayName("DFS-040.AC-5 Cancelling a RUNNING scan makes it CANCELLING, then CANCELLED, also on the stream")
    void cancelRunningScan(@TempDir Path tmp, CapturedOutput output) throws Exception {
        tree(tmp.resolve("large"), 24, 16 * 1024 * 1024);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = start(server, cookie, startBody(tmp.resolve("large")));
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (!"RUNNING".equals(status(body(server.get("/api/scans/" + id, cookie))))) {
                assertTrue(System.nanoTime() < deadline, "the scan never ran");
                Thread.sleep(5);
            }

            HttpResponse<String> r = server.sendJson("POST", "/api/scans/" + id + "/cancel", cookie, server.origin(), "");

            assertEquals(200, r.statusCode(), r.body());
            assertTrue(Set.of("CANCELLING", "CANCELLED").contains(status(body(r))), r.body());
            List<Event> events = stream(server, cookie, id, new StringBuilder());
            assertEquals("CANCELLED", status(events.getLast().data()), events.toString());
        }
    }

    @Test
    @DisplayName("DFS-040.AC-5 Cancelling a finished scan changes nothing")
    void cancelFinishedScan(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = fixture(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = start(server, cookie, startBody(t));
            awaitTerminal(server, cookie, id);

            HttpResponse<String> r = server.sendJson("POST", "/api/scans/" + id + "/cancel", cookie, server.origin(), "");

            assertEquals(200, r.statusCode(), r.body());
            assertEquals("COMPLETED", status(body(r)), r.body());
        }
    }

    @ParameterizedTest(name = "DFS-040.AC-6 {0} {1} answers 404 UNKNOWN_SCAN")
    @CsvSource({
            "GET, /api/scans/" + UNKNOWN,
            "POST, /api/scans/" + UNKNOWN + "/cancel",
            "GET, /api/scans/" + UNKNOWN + "/events",
            "GET, /api/scans/not-a-uuid",
    })
    @DisplayName("DFS-040.AC-6 An unknown or malformed scan ID answers 404 UNKNOWN_SCAN")
    void unknownScan(String method, String path, CapturedOutput output) throws Exception {
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));

            HttpResponse<String> r = method.equals("GET")
                    ? server.get(path, cookie)
                    : server.sendJson(method, path, cookie, server.origin(), "");

            assertEquals(404, r.statusCode(), r.body());
            assertEquals("UNKNOWN_SCAN", body(r).path("code").asString(), r.body());
        }
    }

    @ParameterizedTest(name = "DFS-040.AC-7 {0} {1} {2} answers 403")
    @CsvSource(delimiter = '|', value = {
            "POST | /api/scans          | no cookie",
            "GET  | /api/scans          | no cookie",
            "GET  | /api/scans/ID       | no cookie",
            "GET  | /api/scans/ID/events | no cookie",
            "POST | /api/scans/ID/cancel | no cookie",
            "POST | /api/scans          | no origin",
            "POST | /api/scans          | evil origin",
            "POST | /api/scans/ID/cancel | evil origin",
            "GET  | /api/scans          | evil host",
    })
    @DisplayName("DFS-040.AC-7 The scan endpoints need the cookie, the Host and, for POST, the Origin")
    void scanEndpointsAreSecured(String method, String pathTemplate, String condition, @TempDir Path tmp,
                                 CapturedOutput output) throws Exception {
        Path t = fixture(tmp);
        try (TestServer server = TestServer.start(new Class<?>[]{HoldingExecutorConfiguration.class})) {
            String cookie = server.login(server.token(output.getOut()));
            String id = start(server, cookie, startBody(t));
            String path = pathTemplate.replace("ID", id);
            String body = path.equals("/api/scans") && method.equals("POST") ? startBody(t) : "";

            int status = switch (condition) {
                case "no cookie" -> method.equals("GET")
                        ? server.get(path, null).statusCode()
                        : server.sendJson(method, path, null, server.origin(), body).statusCode();
                case "no origin" -> server.sendJson(method, path, cookie, null, body).statusCode();
                case "evil origin" -> server.sendJson(method, path, cookie, "http://evil.example", body).statusCode();
                case "evil host" -> server.rawGet(path, "evil.example:" + server.port(), cookie);
                default -> throw new IllegalArgumentException(condition);
            };

            assertEquals(403, status);
            JsonNode list = body(server.get("/api/scans", cookie));
            assertEquals(1, list.size(), "a scan was started: " + list);
            assertEquals("QUEUED", status(list.path(0)), "the scan was cancelled: " + list);
        }
    }

    @Test
    @DisplayName("DFS-052.AC-6 A location in a quarantine is refused with 400 LOCATION_IN_QUARANTINE and starts no scan")
    void locationInAQuarantineIsRefused(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path location = Files.createDirectories(tmp.resolve(".dfs-quarantine/run-1"));
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));

            HttpResponse<String> r = server.sendJson("POST", "/api/scans", cookie, server.origin(), startBody(location));

            assertEquals(400, r.statusCode(), r.body());
            assertEquals("LOCATION_IN_QUARANTINE", body(r).path("code").asString(), r.body());
            assertTrue(body(r).path("message").asString().contains(location.toString()), r.body());
            assertEquals(0, body(server.get("/api/scans", cookie)).size());
        }
    }

    @Test
    @DisplayName("DFS-040.AC-8 The spec describes the five scan operations and their schemas")
    void specDescribesTheScanApi() throws Exception {
        JsonNode spec = JSON.readTree(Files.readString(Path.of(System.getProperty("dfs.openapi.file"))));
        List<String> operations = new ArrayList<>();
        spec.path("paths").forEach(path -> path.forEach(op -> {
            if (contains(op.path("tags"), "scans")) {
                operations.add(op.path("operationId").asString());
            }
        }));
        assertEquals(Set.of("startScan", "listScans", "getScan", "cancelScan", "streamScanEvents"), Set.copyOf(operations),
                operations.toString());
        for (String schema : List.of("StartScanRequest", "ScanEntry", "ScanProgress", "ApiError")) {
            assertFalse(spec.path("components").path("schemas").path(schema).isMissingNode(), "no schema " + schema);
        }
        assertTrue(spec.path("paths").path("/api/scans/{id}/events").path("get").path("responses").path("200")
                .path("content").has("text/event-stream"), "the events operation is not text/event-stream");
    }

    private static boolean contains(JsonNode array, String value) {
        for (JsonNode n : array) {
            if (value.equals(n.asString())) {
                return true;
            }
        }
        return false;
    }
}
