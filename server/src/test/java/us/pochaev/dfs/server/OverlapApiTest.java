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
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The overlaps API: folder summaries and overlap pairs of a finished scan (DFS-048). */
@ExtendWith(OutputCaptureExtension.class)
class OverlapApiTest {

    static final int KB = ResultApiTest.KB;

    /** The ticket background tree T (a real path, since the scan roots are resolved). */
    static Path tree(Path tmp) throws IOException {
        Path t = Files.createDirectories(tmp.resolve("data")).toRealPath();
        ResultApiTest.write(t.resolve("Photos/2019/a.jpg"), ResultApiTest.content(300, 1));
        ResultApiTest.write(t.resolve("Photos/2019/b.jpg"), ResultApiTest.content(200, 2));
        ResultApiTest.write(t.resolve("Photos/2019/u.jpg"), ResultApiTest.content(100, 3));
        ResultApiTest.write(t.resolve("Backup/2019/a.jpg"), ResultApiTest.content(300, 1));
        ResultApiTest.write(t.resolve("Backup/2019/b.jpg"), ResultApiTest.content(200, 2));
        ResultApiTest.write(t.resolve("Old/Pictures/a.jpg"), ResultApiTest.content(300, 1));
        Files.createDirectories(t.resolve("Empty"));
        return t;
    }

    /** A path as the API returned it, relative to t with forward slashes ("" for t itself). */
    static String rel(Path t, String path) {
        return t.relativize(Path.of(path)).toString().replace('\\', '/');
    }

    static List<String> folders(Path t, JsonNode items) {
        List<String> out = new ArrayList<>();
        items.forEach(i -> out.add(rel(t, i.path("folder").asString())));
        return out;
    }

    static JsonNode okGet(TestServer server, String cookie, String path) throws Exception {
        return ResultApiTest.ok(server.get(path, cookie));
    }

    static void side(Path t, JsonNode s, String folder, long withCopy, long files, long bytesWithCopy,
                     long bytes, boolean full) {
        assertEquals(folder, rel(t, s.path("folder").asString()), s.toString());
        assertEquals(withCopy, s.path("filesWithCopy").asLong(), s.toString());
        assertEquals(files, s.path("totalFiles").asLong(), s.toString());
        assertEquals(bytesWithCopy, s.path("bytesWithCopy").asLong(), s.toString());
        assertEquals(bytes, s.path("totalBytes").asLong(), s.toString());
        assertEquals(full, s.path("full").asBoolean(), s.toString());
    }

    /** Byte amounts in KB. */
    static void pair(Path t, JsonNode p, String a, long aWith, long aFiles, long aBytesWith, long aBytes,
                     boolean aFull, String b, long bWith, long bFiles, long bBytesWith, long bBytes, boolean bFull) {
        side(t, p.path("a"), a, aWith, aFiles, aBytesWith * KB, aBytes * KB, aFull);
        side(t, p.path("b"), b, bWith, bFiles, bBytesWith * KB, bBytes * KB, bFull);
    }

    static boolean absent(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull();
    }

    /** Replaces @T and @OUT in the values of a query and URL-encodes each value. */
    static String resolve(String query, Path t, Path outside) {
        List<String> parts = new ArrayList<>();
        for (String kv : query.split("&")) {
            int eq = kv.indexOf('=');
            String v = kv.substring(eq + 1).replace("@T", t.toString()).replace("@OUT", outside.toString());
            parts.add(kv.substring(0, eq + 1) + URLEncoder.encode(v, StandardCharsets.UTF_8));
        }
        return String.join("&", parts);
    }

    @Test
    @DisplayName("DFS-048.AC-1 Every listed summary, in order")
    void everyListedSummary(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = tree(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t);

            JsonNode page = okGet(server, cookie, "/api/scans/" + id + "/summaries");

            assertTrue(absent(page.get("folder")), page.toString());
            assertEquals(7, page.path("totalItems").asInt(), page.toString());
            assertEquals(List.of("Backup", "Backup/2019", "Photos", "Photos/2019", "Old", "Old/Pictures", ""),
                    folders(t, page.path("items")));
            JsonNode photos2019 = page.path("items").get(3);
            assertEquals(3, photos2019.path("totalFiles").asLong());
            assertEquals(600L * KB, photos2019.path("totalBytes").asLong());
            assertEquals(2, photos2019.path("filesWithCopyElsewhere").asLong());
            assertEquals(500L * KB, photos2019.path("bytesWithCopyElsewhere").asLong());
            assertEquals(0, photos2019.path("internalDuplicateFiles").asLong());
            JsonNode top = page.path("items").get(6);
            assertEquals(6, top.path("totalFiles").asLong());
            assertEquals(0, top.path("filesWithCopyElsewhere").asLong());
            assertEquals(3, top.path("internalDuplicateFiles").asLong());
            assertEquals(800L * KB, top.path("internalBytes").asLong());
        }
    }

    @Test
    @DisplayName("DFS-048.AC-1 Summaries under a folder, with the own summary of that folder")
    void summariesUnderAFolder(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = tree(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t);

            JsonNode backup = okGet(server, cookie,
                    "/api/scans/" + id + "/summaries?under=" + ResultApiTest.q(t.resolve("Backup")));
            assertEquals(List.of("Backup/2019"), folders(t, backup.path("items")));
            assertEquals("Backup", rel(t, backup.path("folder").path("folder").asString()));
            assertEquals(2, backup.path("folder").path("totalFiles").asLong());
            assertEquals(2, backup.path("folder").path("filesWithCopyElsewhere").asLong());
            assertEquals(500L * KB, backup.path("folder").path("totalBytes").asLong());

            JsonNode leaf = okGet(server, cookie,
                    "/api/scans/" + id + "/summaries?under=" + ResultApiTest.q(t.resolve("Photos/2019")));
            assertEquals(0, leaf.path("items").size(), leaf.toString());
            assertEquals("Photos/2019", rel(t, leaf.path("folder").path("folder").asString()));
            assertEquals(3, leaf.path("folder").path("totalFiles").asLong());
            assertEquals(2, leaf.path("folder").path("filesWithCopyElsewhere").asLong());
        }
    }

    @Test
    @DisplayName("DFS-048.AC-2 Every listed pair, in order")
    void everyListedPair(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = tree(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t);

            JsonNode page = okGet(server, cookie, "/api/scans/" + id + "/pairs");

            assertEquals(3, page.path("totalItems").asInt(), page.toString());
            JsonNode items = page.path("items");
            assertEquals(3, items.size(), page.toString());
            pair(t, items.get(0), "Backup/2019", 2, 2, 500, 500, true, "Photos/2019", 2, 3, 500, 600, false);
            pair(t, items.get(1), "Backup/2019", 1, 2, 300, 500, false, "Old/Pictures", 1, 1, 300, 300, true);
            pair(t, items.get(2), "Old/Pictures", 1, 1, 300, 300, true, "Photos/2019", 1, 3, 300, 600, false);
        }
    }

    @Test
    @DisplayName("DFS-048.AC-2 Pairs under a folder keep their other side")
    void pairsUnderAFolder(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = tree(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t);

            JsonNode page = okGet(server, cookie,
                    "/api/scans/" + id + "/pairs?under=" + ResultApiTest.q(t.resolve("Old")));

            JsonNode items = page.path("items");
            assertEquals(2, items.size(), page.toString());
            pair(t, items.get(0), "Backup/2019", 1, 2, 300, 500, false, "Old/Pictures", 1, 1, 300, 300, true);
            pair(t, items.get(1), "Old/Pictures", 1, 1, 300, 300, true, "Photos/2019", 1, 3, 300, 600, false);
        }
    }

    @Test
    @DisplayName("DFS-048.AC-3 The pair of two folders, counted recursively, in either order")
    void pairOfTwoFolders(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = tree(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t);
            String photos = ResultApiTest.q(t.resolve("Photos"));
            String backup = ResultApiTest.q(t.resolve("Backup"));

            JsonNode first = okGet(server, cookie, "/api/scans/" + id + "/pair?a=" + photos + "&b=" + backup);
            JsonNode second = okGet(server, cookie, "/api/scans/" + id + "/pair?a=" + backup + "&b=" + photos);

            pair(t, first, "Backup", 2, 2, 500, 500, true, "Photos", 2, 3, 500, 600, false);
            assertEquals(first, second);
        }
    }

    @Test
    @DisplayName("DFS-048.AC-3 A folder the scan holds nothing for is a side of zeros")
    void folderWithNothing(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = tree(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t);

            JsonNode p = okGet(server, cookie, "/api/scans/" + id + "/pair?a="
                    + ResultApiTest.q(t.resolve("Empty")) + "&b=" + ResultApiTest.q(t.resolve("Photos")));

            pair(t, p, "Empty", 0, 0, 0, 0, false, "Photos", 0, 3, 0, 600, false);
        }
    }

    @ParameterizedTest(name = "DFS-048.AC-4 pair?{0} is refused with {1} {2}")
    @CsvSource(delimiter = '|', value = {
            "a=@T/Photos                       | 400 | MISSING_FOLDER     | -",
            "b=@T/Photos                       | 400 | MISSING_FOLDER     | -",
            "a=Photos&b=@T/Backup              | 400 | RELATIVE_PATH      | -",
            "a=@OUT&b=@T/Backup                | 403 | PATH_OUTSIDE_ROOTS | -",
            "a=@T/Photos/../..&b=@T/Backup     | 403 | PATH_OUTSIDE_ROOTS | -",
            "a=@T/Photos&b=@T/Photos           | 400 | NESTED_FOLDERS     | both folders are",
            "a=@T/Photos&b=@T/Photos/2019      | 400 | NESTED_FOLDERS     | contains",
            "a=@T/Photos/2019&b=@T/Photos      | 400 | NESTED_FOLDERS     | contains",
    })
    @DisplayName("DFS-048.AC-4 Refused pair queries")
    void refusedPairs(String query, int status, String code, String fragment, @TempDir Path tmp,
                      CapturedOutput output) throws Exception {
        Path t = tree(tmp);
        Path outside = Files.createDirectories(tmp.resolve("other")).toRealPath();
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t);

            HttpResponse<String> r = server.get("/api/scans/" + id + "/pair?" + resolve(query, t, outside), cookie);

            assertEquals(status, r.statusCode(), r.body());
            JsonNode body = ScanApiTest.body(r);
            assertEquals(code, body.path("code").asString(), r.body());
            assertFalse(body.path("message").asString().isEmpty(), r.body());
            if (!fragment.equals("-")) {
                assertTrue(body.path("message").asString().contains(fragment), r.body());
            }
        }
    }

    @ParameterizedTest(name = "DFS-048.AC-5 GET {0}?{1} answers {2} {3}")
    @CsvSource(delimiter = '|', value = {
            "summaries | under=@OUT                 | 403 | PATH_OUTSIDE_ROOTS",
            "pairs     | under=@T/Photos/../..      | 403 | PATH_OUTSIDE_ROOTS",
            "pairs     | under=Photos               | 400 | RELATIVE_PATH",
            "pairs     | under=@T/Empty             | 200 | NO_ITEMS",
            "summaries | under=@T/Empty             | 200 | EMPTY_ZEROS",
    })
    @DisplayName("DFS-048.AC-5 The folder filter follows the roots of the scan")
    void underIsChecked(String endpoint, String query, int status, String expect, @TempDir Path tmp,
                        CapturedOutput output) throws Exception {
        Path t = tree(tmp);
        Path outside = Files.createDirectories(tmp.resolve("other")).toRealPath();
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t);

            HttpResponse<String> r = server.get("/api/scans/" + id + "/" + endpoint + "?"
                    + resolve(query, t, outside), cookie);

            assertEquals(status, r.statusCode(), r.body());
            JsonNode body = ScanApiTest.body(r);
            if (status != 200) {
                assertEquals(expect, body.path("code").asString(), r.body());
                assertFalse(body.path("message").asString().isEmpty(), r.body());
            } else {
                assertEquals(0, body.path("items").size(), r.body());
                assertEquals(0, body.path("totalItems").asInt(), r.body());
                if (expect.equals("EMPTY_ZEROS")) {
                    JsonNode own = body.path("folder");
                    assertEquals("Empty", rel(t, own.path("folder").asString()), r.body());
                    assertEquals(0, own.path("totalFiles").asLong(), r.body());
                    assertEquals(0, own.path("totalBytes").asLong(), r.body());
                    assertEquals(0, own.path("filesWithCopyElsewhere").asLong(), r.body());
                    assertEquals(0, own.path("internalDuplicateFiles").asLong(), r.body());
                }
            }
        }
    }

    @Test
    @DisplayName("DFS-048.AC-5 A page of the ordered list; a page past the end is empty with the same total")
    void paging(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = tree(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t);

            JsonNode second = okGet(server, cookie, "/api/scans/" + id + "/pairs?page=1&size=2");
            assertEquals(1, second.path("items").size(), second.toString());
            pair(t, second.path("items").get(0), "Old/Pictures", 1, 1, 300, 300, true,
                    "Photos/2019", 1, 3, 300, 600, false);
            assertEquals(1, second.path("page").asInt());
            assertEquals(2, second.path("size").asInt());
            assertEquals(3, second.path("totalItems").asInt());

            JsonNode past = okGet(server, cookie, "/api/scans/" + id + "/summaries?page=9&size=5");
            assertEquals(0, past.path("items").size(), past.toString());
            assertEquals(7, past.path("totalItems").asInt());
        }
    }

    @ParameterizedTest(name = "DFS-048.AC-5 GET {0}?{1} answers 400 INVALID_PAGE")
    @CsvSource(delimiter = '|', value = {
            "pairs     | page=-1",
            "summaries | size=0",
            "summaries | size=501",
    })
    @DisplayName("DFS-048.AC-5 Bad paging is refused")
    void badPaging(String endpoint, String query, @TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = tree(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t);

            HttpResponse<String> r = server.get("/api/scans/" + id + "/" + endpoint + "?" + query, cookie);

            assertEquals(400, r.statusCode(), r.body());
            assertEquals("INVALID_PAGE", ScanApiTest.body(r).path("code").asString(), r.body());
        }
    }

    @ParameterizedTest(name = "DFS-048.AC-6 GET a queued scan {0} answers 409 SCAN_NOT_COMPLETED")
    @CsvSource({"summaries", "pairs", "pair"})
    @DisplayName("DFS-048.AC-6 A scan that is not completed answers 409")
    void queuedScanIsRefused(String endpoint, @TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = tree(tmp);
        try (TestServer server = TestServer.start(new Class<?>[]{HoldingExecutorConfiguration.class})) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ScanApiTest.start(server, cookie, ScanApiTest.startBody(t));

            HttpResponse<String> r = server.get("/api/scans/" + id + "/" + endpoint, cookie);

            assertEquals(409, r.statusCode(), r.body());
            assertEquals("SCAN_NOT_COMPLETED", ScanApiTest.body(r).path("code").asString(), r.body());
        }
    }

    @ParameterizedTest(name = "DFS-048.AC-6 GET /api/scans/{0}{1} answers 404 UNKNOWN_SCAN")
    @CsvSource(delimiter = '|', value = {
            "00000000-0000-0000-0000-000000000000 | /summaries",
            "not-a-uuid                           | /pairs",
            "not-a-uuid                           | /pair?a=relative",
    })
    @DisplayName("DFS-048.AC-6 Unknown or malformed scan IDs answer 404")
    void unknownScan(String scan, String rest, @TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = tree(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            // Reach a stub first so the test is red against the stubs: a completed scan answers 200 once implemented.
            String id = ResultApiTest.completedScan(server, cookie, t);
            okGet(server, cookie, "/api/scans/" + id + "/summaries");

            HttpResponse<String> r = server.get("/api/scans/" + scan + rest, cookie);

            assertEquals(404, r.statusCode(), r.body());
            assertEquals("UNKNOWN_SCAN", ScanApiTest.body(r).path("code").asString(), r.body());
        }
    }

    @Test
    @DisplayName("DFS-048.AC-7 Hard links are not copies through the API")
    void hardLinksAreNotCopies(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t2 = Files.createDirectories(tmp.resolve("t2")).toRealPath();
        ResultApiTest.write(t2.resolve("A/x.bin"), ResultApiTest.content(100, 7));
        Files.createDirectories(t2.resolve("B"));
        try {
            Files.createLink(t2.resolve("B/x.bin"), t2.resolve("A/x.bin"));
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            Assumptions.abort("hard links cannot be created here: " + e);
        }
        ResultApiTest.write(t2.resolve("C/x.bin"), ResultApiTest.content(100, 7));
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t2);

            JsonNode pairs = okGet(server, cookie, "/api/scans/" + id + "/pairs");
            assertEquals(2, pairs.path("items").size(), pairs.toString());
            List<String> listed = new ArrayList<>();
            pairs.path("items").forEach(p -> listed.add(rel(t2, p.path("a").path("folder").asString()) + "+"
                    + rel(t2, p.path("b").path("folder").asString())));
            assertEquals(List.of("A+C", "B+C"), listed);

            JsonNode ab = okGet(server, cookie, "/api/scans/" + id + "/pair?a=" + ResultApiTest.q(t2.resolve("A"))
                    + "&b=" + ResultApiTest.q(t2.resolve("B")));
            assertEquals(0, ab.path("a").path("filesWithCopy").asLong(), ab.toString());
            assertEquals(0, ab.path("b").path("filesWithCopy").asLong(), ab.toString());
        }
    }

    @ParameterizedTest(name = "DFS-048.AC-9 GET {0} {1} is refused with 403")
    @CsvSource(delimiter = '|', value = {
            "summaries | without the cookie",
            "pairs     | without the cookie",
            "pair      | without the cookie",
            "pairs     | with a foreign Host",
    })
    @DisplayName("DFS-048.AC-9 The overlaps endpoints are secured")
    void securedEndpoints(String endpoint, String condition, @TempDir Path tmp, CapturedOutput output)
            throws Exception {
        Path t = tree(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t);
            String path = "/api/scans/" + id + "/" + endpoint + (endpoint.equals("pair")
                    ? "?a=" + ResultApiTest.q(t.resolve("Photos")) + "&b=" + ResultApiTest.q(t.resolve("Old")) : "");

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
    @DisplayName("DFS-048.AC-10 openapi.json describes the three overlaps operations and five schemas, and the responses carry their required properties")
    void specDescribesTheOverlapsApi(@TempDir Path tmp, CapturedOutput output) throws Exception {
        String file = System.getProperty("dfs.openapi.file");
        assertNotNull(file, "system property dfs.openapi.file is not set");
        Path specFile = Path.of(file).toAbsolutePath();
        JsonNode spec = ResultApiTest.JSON.readTree(Files.readString(specFile));
        JsonNode schemas = spec.path("components").path("schemas");
        Path t = tree(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t);

            JsonNode summaries = okGet(server, cookie,
                    "/api/scans/" + id + "/summaries?under=" + ResultApiTest.q(t.resolve("Backup")));
            JsonNode pairs = okGet(server, cookie, "/api/scans/" + id + "/pairs");
            JsonNode pair = okGet(server, cookie, "/api/scans/" + id + "/pair?a="
                    + ResultApiTest.q(t.resolve("Photos")) + "&b=" + ResultApiTest.q(t.resolve("Backup")));

            assertHasRequired(schemas, "SummaryPage", summaries);
            assertHasRequired(schemas, "FolderSummaryView", summaries.path("folder"));
            assertHasRequired(schemas, "FolderSummaryView", summaries.path("items").get(0));
            assertHasRequired(schemas, "PairPage", pairs);
            assertHasRequired(schemas, "FolderPairView", pairs.path("items").get(0));
            assertHasRequired(schemas, "PairSideView", pairs.path("items").get(0).path("a"));
            assertHasRequired(schemas, "FolderPairView", pair);
            assertHasRequired(schemas, "PairSideView", pair.path("b"));
        }

        for (String[] op : new String[][]{
                {"/api/scans/{id}/summaries", "listSummaries"},
                {"/api/scans/{id}/pairs", "listPairs"},
                {"/api/scans/{id}/pair", "getPair"}}) {
            JsonNode get = spec.path("paths").path(op[0]).path("get");
            assertEquals(op[1], get.path("operationId").asString(), "operation of GET " + op[0]);
            List<String> tags = new ArrayList<>();
            get.path("tags").forEach(tag -> tags.add(tag.asString()));
            assertEquals(List.of("overlaps"), tags, "tags of " + op[1]);
        }
        for (String schema : List.of("SummaryPage", "FolderSummaryView", "PairPage", "FolderPairView", "PairSideView")) {
            assertTrue(schemas.has(schema), "openapi.json lacks schema " + schema);
        }

        // server/frontend/openapi.json -> server/frontend/src/app/api/services/overlaps.service.ts (ng-openapi-gen)
        Path service = specFile.getParent().resolve("src/app/api/services/overlaps.service.ts");
        assertTrue(Files.isRegularFile(service), "the generated client has no " + service);
        assertTrue(Files.readString(service).contains("class OverlapsService"), service + " has no OverlapsService");
    }

    @Test
    @DisplayName("DFS-048.AC-11 CLAUDE.md's server entry and ui.md U3 name the three overlaps endpoints, and U5b names the analysis package and OverlapAnalysis.of")
    void docsNameTheOverlapsEndpoints(@TempDir Path tmp, CapturedOutput output) throws Exception {
        String file = System.getProperty("dfs.openapi.file");
        assertNotNull(file, "system property dfs.openapi.file is not set");
        // server/frontend/openapi.json -> the repository root
        Path root = Path.of(file).toAbsolutePath().getParent().getParent().getParent();
        Path t = tree(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ResultApiTest.completedScan(server, cookie, t);
            okGet(server, cookie, "/api/scans/" + id + "/summaries");
            okGet(server, cookie, "/api/scans/" + id + "/pairs");
            okGet(server, cookie, "/api/scans/" + id + "/pair?a="
                    + ResultApiTest.q(t.resolve("Photos")) + "&b=" + ResultApiTest.q(t.resolve("Backup")));
        }

        // "/pair" must not be satisfied by "/pairs".
        List<Pattern> endpoints = List.of(
                Pattern.compile(Pattern.quote("/api/scans/{id}/summaries")),
                Pattern.compile(Pattern.quote("/api/scans/{id}/pairs")),
                Pattern.compile(Pattern.quote("/api/scans/{id}/pair") + "(?!s)"));

        String serverEntry = Files.readAllLines(root.resolve("CLAUDE.md"), StandardCharsets.UTF_8).stream()
                .filter(line -> line.startsWith("- `server`:"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("CLAUDE.md has no `server` entry"));
        for (Pattern endpoint : endpoints) {
            assertTrue(endpoint.matcher(serverEntry).find(), "CLAUDE.md's server entry lacks " + endpoint);
        }

        String ui = Files.readString(root.resolve("docs/design/ui.md"), StandardCharsets.UTF_8);
        String u3 = section(ui, "### U3.");
        for (Pattern endpoint : endpoints) {
            assertTrue(endpoint.matcher(u3).find(), "ui.md U3 lacks " + endpoint);
        }
        String u5b = section(ui, "### U5b.");
        assertTrue(u5b.contains("us.pochaev.dfs.server.analysis"), "ui.md U5b lacks us.pochaev.dfs.server.analysis");
        assertTrue(u5b.contains("OverlapAnalysis.of"), "ui.md U5b lacks OverlapAnalysis.of");
    }

    /** The text of a ui.md section, from its heading to the next heading of level 2 or 3. */
    private static String section(String markdown, String heading) {
        int start = markdown.indexOf(heading);
        assertTrue(start >= 0, "ui.md has no section " + heading);
        int from = markdown.indexOf('\n', start);
        int end = markdown.length();
        for (String next : List.of("\n### ", "\n## ")) {
            int i = markdown.indexOf(next, from);
            if (i >= 0 && i < end) {
                end = i;
            }
        }
        return markdown.substring(from, end);
    }

    private static void assertHasRequired(JsonNode schemas, String schema, JsonNode actual) {
        assertNotNull(actual, schema + " response is missing");
        JsonNode required = schemas.path(schema).path("required");
        assertTrue(required.size() > 0, schema + " has no required properties in openapi.json");
        for (JsonNode property : required) {
            assertTrue(actual.has(property.asString()), schema + " response lacks " + property.asString() + ": " + actual);
        }
    }
}
