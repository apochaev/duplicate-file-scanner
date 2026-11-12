package us.pochaev.dfs.server;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The folder groups endpoint of a finished scan (DFS-067). */
@ExtendWith(OutputCaptureExtension.class)
class GroupApiTest {

    static final int KB = ResultApiTest.KB;
    static final int MIB = 1024 * 1024;

    private static String groups(String id, String query) {
        return "/api/scans/" + id + "/groups" + (query.isEmpty() ? "" : "?" + query);
    }

    private static List<String> folders(Path t, JsonNode group) {
        return OverlapApiTest.folders(t, group.path("folders"));
    }

    private static void folder(Path t, JsonNode f, String name, int copiesEach, long sharedFiles, long sharedBytes,
                               long folderFiles, long folderBytes, int otherGroups) {
        assertEquals(name, OverlapApiTest.rel(t, f.path("folder").asString()), f.toString());
        assertEquals(copiesEach, f.path("copiesEach").asInt(), f.toString());
        assertEquals(sharedFiles, f.path("sharedFiles").asLong(), f.toString());
        assertEquals(sharedBytes, f.path("sharedBytes").asLong(), f.toString());
        assertEquals(folderFiles, f.path("folderFiles").asLong(), f.toString());
        assertEquals(folderBytes, f.path("folderBytes").asLong(), f.toString());
        assertEquals(otherGroups, f.path("otherGroups").asInt(), f.toString());
    }

    /** Overlap tree T has two groups: a.jpg in three folders (600 KB), b.jpg in two (200 KB). */
    @Test
    @DisplayName("DFS-067.AC-1, DFS-067.AC-2, DFS-067.AC-3, DFS-067.AC-5 Every group of the tree, its folders and figures, heaviest first")
    void everyGroup(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = OverlapApiTest.tree(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t);

            JsonNode page = OverlapApiTest.okGet(server, cookie, groups(id, "minBytes=0"));

            assertEquals(2, page.path("totalItems").asInt(), page.toString());
            assertEquals(0, page.path("hiddenSmall").asInt(), page.toString());
            assertEquals(0, page.path("page").asInt(), page.toString());
            assertEquals(50, page.path("size").asInt(), page.toString());
            JsonNode items = page.path("items");
            assertEquals(2, items.size(), page.toString());

            JsonNode g1 = items.get(0);
            assertEquals(1, g1.path("files").asLong(), g1.toString());
            assertEquals(3, g1.path("copies").asLong(), g1.toString());
            assertEquals(600L * KB, g1.path("reclaimableBytes").asLong(), g1.toString());
            assertEquals(1, g1.path("totalSets").asInt(), g1.toString());
            assertEquals(List.of("Backup/2019", "Old/Pictures", "Photos/2019"), folders(t, g1));
            folder(t, g1.path("folders").get(0), "Backup/2019", 1, 1, 300L * KB, 2, 500L * KB, 1);
            folder(t, g1.path("folders").get(1), "Old/Pictures", 1, 1, 300L * KB, 1, 300L * KB, 0);
            folder(t, g1.path("folders").get(2), "Photos/2019", 1, 1, 300L * KB, 3, 600L * KB, 1);
            assertEquals(1, g1.path("sets").size(), g1.toString());
            JsonNode set = g1.path("sets").get(0);
            assertEquals("a.jpg", set.path("name").asString(), set.toString());
            assertEquals(300L * KB, set.path("size").asLong(), set.toString());
            assertEquals(3, set.path("copies").asInt(), set.toString());
            assertTrue(set.path("digest").asString().matches("[0-9a-f]{64}"), set.toString());

            JsonNode g2 = items.get(1);
            assertEquals(1, g2.path("files").asLong(), g2.toString());
            assertEquals(2, g2.path("copies").asLong(), g2.toString());
            assertEquals(200L * KB, g2.path("reclaimableBytes").asLong(), g2.toString());
            assertEquals(List.of("Backup/2019", "Photos/2019"), folders(t, g2));
            folder(t, g2.path("folders").get(0), "Backup/2019", 1, 1, 200L * KB, 2, 500L * KB, 1);
            folder(t, g2.path("folders").get(1), "Photos/2019", 1, 1, 200L * KB, 3, 600L * KB, 1);
            assertEquals("b.jpg", g2.path("sets").get(0).path("name").asString(), g2.toString());
        }
    }

    @Test
    @DisplayName("DFS-067.AC-1, DFS-067.AC-2, DFS-067.AC-3 Copies side by side in one folder are a one-folder group")
    void sideBySideCopies(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = Files.createDirectories(tmp.resolve("data")).toRealPath();
        ResultApiTest.write(t.resolve("Photos/IMG_001.jpg"), ResultApiTest.content(300, 1));
        ResultApiTest.write(t.resolve("Photos/IMG_001 (1).jpg"), ResultApiTest.content(300, 1));
        ResultApiTest.write(t.resolve("Photos/other.jpg"), ResultApiTest.content(100, 2));
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t);

            JsonNode page = OverlapApiTest.okGet(server, cookie, groups(id, "minBytes=0"));

            assertEquals(1, page.path("items").size(), page.toString());
            JsonNode g = page.path("items").get(0);
            assertEquals(1, g.path("files").asLong(), g.toString());
            assertEquals(2, g.path("copies").asLong(), g.toString());
            assertEquals(300L * KB, g.path("reclaimableBytes").asLong(), g.toString());
            assertEquals(1, g.path("folders").size(), g.toString());
            folder(t, g.path("folders").get(0), "Photos", 2, 2, 600L * KB, 3, 700L * KB, 0);
        }
    }

    @Test
    @DisplayName("DFS-067.AC-4 Hard links add no copy: a copy with links in two folders is listed in both")
    void hardLinksAreNotCopies(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = Files.createDirectories(tmp.resolve("t2")).toRealPath();
        ResultApiTest.write(t.resolve("A/f"), ResultApiTest.content(100, 7));
        Files.createDirectories(t.resolve("B"));
        try {
            Files.createLink(t.resolve("B/f"), t.resolve("A/f"));
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            Assumptions.abort("hard links cannot be created here: " + e);
        }
        ResultApiTest.write(t.resolve("C/f"), ResultApiTest.content(100, 7));
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t);

            JsonNode page = OverlapApiTest.okGet(server, cookie, groups(id, "minBytes=0"));

            assertEquals(1, page.path("items").size(), page.toString());
            JsonNode g = page.path("items").get(0);
            assertEquals(2, g.path("copies").asLong(), g.toString());
            assertEquals(100L * KB, g.path("reclaimableBytes").asLong(), g.toString());
            assertEquals(List.of("A", "B", "C"), folders(t, g));
            for (JsonNode f : g.path("folders")) {
                assertEquals(1, f.path("copiesEach").asInt(), f.toString());
            }
        }
    }

    @Test
    @DisplayName("DFS-067.AC-5 A page of the ordered list; a page past the end is empty with the same total")
    void paging(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = OverlapApiTest.tree(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t);

            JsonNode second = OverlapApiTest.okGet(server, cookie, groups(id, "minBytes=0&page=1&size=1"));
            assertEquals(1, second.path("items").size(), second.toString());
            assertEquals(List.of("Backup/2019", "Photos/2019"), folders(t, second.path("items").get(0)));
            assertEquals(1, second.path("page").asInt());
            assertEquals(1, second.path("size").asInt());
            assertEquals(2, second.path("totalItems").asInt());

            JsonNode past = OverlapApiTest.okGet(server, cookie, groups(id, "minBytes=0&page=9&size=5"));
            assertEquals(0, past.path("items").size(), past.toString());
            assertEquals(2, past.path("totalItems").asInt());
        }
    }

    @Test
    @DisplayName("DFS-067.AC-5 Concurrent and repeated requests answer the same groups")
    void concurrentRequestsAgree(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = OverlapApiTest.tree(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t);
            // One request first, alone: TestServer surfaces a stub's exception reliably only for one request at a time.
            JsonNode warm = OverlapApiTest.okGet(server, cookie, groups(id, "minBytes=0"));
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
            try {
                List<java.util.concurrent.Future<JsonNode>> calls = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    calls.add(pool.submit(() -> OverlapApiTest.okGet(server, cookie, groups(id, "minBytes=0"))));
                }
                JsonNode first = warm;
                for (var call : calls) {
                    JsonNode answer;
                    try {
                        answer = call.get(60, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (java.util.concurrent.ExecutionException e) {
                        if (e.getCause() instanceof RuntimeException re) {
                            throw re;
                        }
                        throw e;
                    }
                    assertEquals(2, answer.path("totalItems").asInt(), answer.toString());
                    if (first == null) {
                        first = answer;
                    }
                    assertEquals(first, answer);
                }
                assertEquals(first, OverlapApiTest.okGet(server, cookie, groups(id, "minBytes=0")));
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @ParameterizedTest(name = "DFS-067.AC-6 groups?{0} answers {1} groups, totalItems {2}, hiddenSmall {3}")
    @CsvSource(delimiter = '|', value = {
            "under=@T/Old&minBytes=0            | 1 | 1 | 0",
            "under=@T/Old/Pictures&minBytes=0   | 1 | 1 | 0",
            "under=@T/Photos&minBytes=0         | 2 | 2 | 0",
            "under=@T&minBytes=0                | 2 | 2 | 0",
            "under=@T/Empty&minBytes=0          | 0 | 0 | 0",
            "under=@T/Old                       | 0 | 0 | 1",
            "under=@T/Photos/2019               | 0 | 0 | 2",
            "minBytes=0                         | 2 | 2 | 0",
            "-                                  | 0 | 0 | 2",
    })
    @DisplayName("DFS-067.AC-6 Groups under a folder, with totalItems and hiddenSmall counted over them only")
    void underAFolder(String query, int items, int totalItems, int hiddenSmall, @TempDir Path tmp,
                      CapturedOutput output) throws Exception {
        Path t = OverlapApiTest.tree(tmp);
        Path outside = Files.createDirectories(tmp.resolve("other")).toRealPath();
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t);

            String resolved = query.equals("-") ? "" : OverlapApiTest.resolve(query, t, outside);
            JsonNode page = OverlapApiTest.okGet(server, cookie, groups(id, resolved));

            assertEquals(items, page.path("items").size(), page.toString());
            assertEquals(totalItems, page.path("totalItems").asInt(), page.toString());
            assertEquals(hiddenSmall, page.path("hiddenSmall").asInt(), page.toString());
        }
    }

    /** One 2 MiB group (A, B) and three small ones, each in folders of its own. */
    private static Path minBytesTree(Path tmp) throws IOException {
        Path t = Files.createDirectories(tmp.resolve("big")).toRealPath();
        byte[] big = ScanApiTest.bytes(2 * MIB, 1);
        ResultApiTest.write(t.resolve("A/big.bin"), big);
        ResultApiTest.write(t.resolve("B/big.bin"), big);
        for (int i = 0; i < 3; i++) {
            byte[] small = ResultApiTest.content(100 + i, 10 + i);
            ResultApiTest.write(t.resolve("S" + i + "a/s.bin"), small);
            ResultApiTest.write(t.resolve("S" + i + "b/s.bin"), small);
        }
        return t;
    }

    @ParameterizedTest(name = "DFS-067.AC-7 groups?{0} answers {1} groups and hiddenSmall {2}")
    @CsvSource(delimiter = '|', value = {
            "-                    | 1 | 3",
            "minBytes=0           | 4 | 0",
            "minBytes=1048576     | 1 | 3",
            "minBytes=2097152     | 1 | 3",
            "minBytes=2097153     | 0 | 4",
    })
    @DisplayName("DFS-067.AC-7 Groups below minBytes are left out and counted")
    void smallGroupsAreHidden(String query, int items, int hiddenSmall, @TempDir Path tmp, CapturedOutput output)
            throws Exception {
        Path t = minBytesTree(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t);

            JsonNode page = OverlapApiTest.okGet(server, cookie, groups(id, query.equals("-") ? "" : query));

            assertEquals(items, page.path("items").size(), page.toString());
            assertEquals(items, page.path("totalItems").asInt(), page.toString());
            assertEquals(hiddenSmall, page.path("hiddenSmall").asInt(), page.toString());
            if (items > 0) {
                assertEquals(2L * MIB, page.path("items").get(0).path("reclaimableBytes").asLong(), page.toString());
            }
        }
    }

    @ParameterizedTest(name = "DFS-067.AC-9 GET groups?{0} answers {1} {2}")
    @CsvSource(delimiter = '|', value = {
            "under=Photos                  | 400 | RELATIVE_PATH",
            "under=@OUT                    | 403 | PATH_OUTSIDE_ROOTS",
            "under=@T/Photos/../..         | 403 | PATH_OUTSIDE_ROOTS",
            "page=-1                       | 400 | INVALID_PAGE",
            "size=0                        | 400 | INVALID_PAGE",
            "size=501                      | 400 | INVALID_PAGE",
            "page=-1&minBytes=-1           | 400 | INVALID_PAGE",
            "under=Photos&minBytes=-1      | 400 | RELATIVE_PATH",
            "under=@OUT&minBytes=-1        | 403 | PATH_OUTSIDE_ROOTS",
    })
    @DisplayName("DFS-067.AC-9 Refused group queries")
    void refused(String query, int status, String code, @TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = OverlapApiTest.tree(tmp);
        Path outside = Files.createDirectories(tmp.resolve("other")).toRealPath();
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t);

            HttpResponse<String> r = server.get(groups(id, OverlapApiTest.resolve(query, t, outside)), cookie);

            assertEquals(status, r.statusCode(), r.body());
            JsonNode body = ScanApiTest.body(r);
            assertEquals(code, body.path("code").asString(), r.body());
            assertFalse(body.path("message").asString().isEmpty(), r.body());
        }
    }

    @ParameterizedTest(name = "DFS-067.AC-9 GET a queued scan's groups?{0} answers 409 SCAN_NOT_COMPLETED")
    @CsvSource(delimiter = '|', value = {"-", "minBytes=0", "minBytes=-1", "under=relative"})
    @DisplayName("DFS-067.AC-9 A scan that is not completed answers 409")
    void queuedScanIsRefused(String query, @TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = OverlapApiTest.tree(tmp);
        try (TestServer server = TestServer.start(new Class<?>[]{HoldingExecutorConfiguration.class})) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ScanApiTest.start(server, cookie, ScanApiTest.startBody(t));

            HttpResponse<String> r = server.get(groups(id, query.equals("-") ? "" : query), cookie);

            assertEquals(409, r.statusCode(), r.body());
            assertEquals("SCAN_NOT_COMPLETED", ScanApiTest.body(r).path("code").asString(), r.body());
        }
    }

    @ParameterizedTest(name = "DFS-067.AC-9 GET /api/scans/{0}/groups answers 404 UNKNOWN_SCAN")
    @CsvSource({"00000000-0000-0000-0000-000000000000", "not-a-uuid"})
    @DisplayName("DFS-067.AC-9 Unknown or malformed scan IDs answer 404")
    void unknownScan(String scan, @TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = OverlapApiTest.tree(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            // Reach a stub first so the test is red against the stubs: a completed scan answers 200 once implemented.
            String id = ResultApiTest.completedScan(server, cookie, t);
            OverlapApiTest.okGet(server, cookie, groups(id, "minBytes=0"));

            HttpResponse<String> r = server.get("/api/scans/" + scan + "/groups", cookie);

            assertEquals(404, r.statusCode(), r.body());
            assertEquals("UNKNOWN_SCAN", ScanApiTest.body(r).path("code").asString(), r.body());
        }
    }

    @ParameterizedTest(name = "DFS-067.AC-9 GET groups {0} is refused with 403")
    @CsvSource({"without the cookie", "with a foreign Host"})
    @DisplayName("DFS-067.AC-9 The groups endpoint is secured")
    void secured(String condition, @TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = OverlapApiTest.tree(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t);
            String path = groups(id, "minBytes=0");

            if (condition.startsWith("without")) {
                assertEquals(403, server.get(path, null).statusCode());
            } else {
                assertEquals(403, server.rawGet(path, "evil.example:" + server.port(), cookie));
            }

            // Positive control: the same request with the cookie and the right Host succeeds.
            assertEquals(200, server.get(path, cookie).statusCode());
        }
    }

    @Test
    @DisplayName("DFS-067.AC-10 A negative minBytes is refused with INVALID_MIN_BYTES; 0 is accepted")
    void negativeMinBytes(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = OverlapApiTest.tree(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t);

            HttpResponse<String> refused = server.get(groups(id, "minBytes=-1"), cookie);
            assertEquals(400, refused.statusCode(), refused.body());
            JsonNode body = ScanApiTest.body(refused);
            assertEquals("INVALID_MIN_BYTES", body.path("code").asString(), refused.body());
            assertTrue(body.path("message").asString().contains("minBytes"), refused.body());

            JsonNode accepted = OverlapApiTest.okGet(server, cookie, groups(id, "minBytes=0"));
            assertEquals(2, accepted.path("items").size(), accepted.toString());
        }
    }

    @Test
    @DisplayName("DFS-067.AC-8 A group of 25 sets answers its first 20 sets through the API")
    void twentySetsThroughTheApi(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = Files.createDirectories(tmp.resolve("many")).toRealPath();
        for (int i = 0; i < 25; i++) {
            byte[] content = ResultApiTest.content(10 + i, 100 + i);
            ResultApiTest.write(t.resolve("A/s" + i + ".bin"), content);
            ResultApiTest.write(t.resolve("B/s" + i + ".bin"), content);
        }
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t);

            JsonNode page = OverlapApiTest.okGet(server, cookie, groups(id, "minBytes=0"));

            assertEquals(1, page.path("items").size(), page.toString());
            JsonNode g = page.path("items").get(0);
            assertEquals(25, g.path("files").asLong(), g.toString());
            assertEquals(25, g.path("totalSets").asInt(), g.toString());
            JsonNode sets = g.path("sets");
            assertEquals(20, sets.size(), g.toString());
            List<String> names = new ArrayList<>();
            sets.forEach(s -> names.add(s.path("name").asString()));
            assertEquals("s24.bin", names.get(0));
            assertEquals("s5.bin", names.get(19));
            assertEquals((10L + 24) * KB, sets.get(0).path("size").asLong());
        }
    }
}
