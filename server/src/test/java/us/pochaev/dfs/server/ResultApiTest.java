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
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The scan result API: the folder tree and the duplicate sets of a finished scan (DFS-047). */
@ExtendWith(OutputCaptureExtension.class)
class ResultApiTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final int KB = 1024;
    static final Instant A_MTIME = Instant.parse("2024-01-02T03:04:05Z");

    static byte[] content(int kb, int seed) {
        return ScanApiTest.bytes(kb * KB, seed);
    }

    static void write(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    /** The ticket's background tree T (a real path, since the scan's roots are resolved); Empty/ holds no file. */
    static Path background(Path tmp) throws IOException {
        Path t = Files.createDirectories(tmp.resolve("data")).toRealPath();
        write(t.resolve("Photos/a.jpg"), content(300, 1));
        write(t.resolve("Photos/b.jpg"), content(200, 2));
        write(t.resolve("Backup/2019/a.jpg"), content(300, 1));
        write(t.resolve("Backup/2019/b.jpg"), content(200, 2));
        write(t.resolve("Backup/2019/c.jpg"), content(100, 3));
        write(t.resolve("Backup/old/a2.jpg"), content(300, 1));
        Files.createDirectories(t.resolve("Empty"));
        Files.setLastModifiedTime(t.resolve("Photos/a.jpg"), FileTime.from(A_MTIME));
        return t;
    }

    static String q(Path p) {
        return URLEncoder.encode(p.toString(), StandardCharsets.UTF_8);
    }

    /** Starts a scan of t and waits for COMPLETED. */
    static String completedScan(TestServer server, String cookie, Path t) throws Exception {
        String id = ScanApiTest.start(server, cookie, ScanApiTest.startBody(t));
        JsonNode done = ScanApiTest.awaitTerminal(server, cookie, id);
        assertEquals("COMPLETED", ScanApiTest.status(done), done.toString());
        return id;
    }

    static JsonNode ok(HttpResponse<String> r) {
        assertEquals(200, r.statusCode(), r.body());
        return ScanApiTest.body(r);
    }

    static List<String> names(JsonNode folders) {
        List<String> names = new ArrayList<>();
        folders.forEach(n -> names.add(n.path("name").asString()));
        return names;
    }

    static JsonNode byName(JsonNode folders, String name) {
        for (JsonNode n : folders) {
            if (name.equals(n.path("name").asString())) {
                return n;
            }
        }
        throw new AssertionError("no folder " + name + " in " + folders);
    }

    static List<String> linkPaths(JsonNode set) {
        List<String> paths = new ArrayList<>();
        set.path("copies").forEach(c -> c.path("links").forEach(l -> paths.add(l.path("path").asString())));
        return paths;
    }

    @Test
    @DisplayName("DFS-047.AC-1 The roots, then one level below with subtree and direct totals")
    void rootsAndOneLevel(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = background(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = completedScan(server, cookie, t);

            JsonNode roots = ok(server.get("/api/scans/" + id + "/folders", cookie));
            assertEquals(1, roots.size(), roots.toString());
            JsonNode root = roots.get(0);
            assertEquals(t.toString(), root.path("path").asString());
            assertEquals(6, root.path("files").asLong());
            assertEquals(1400L * KB, root.path("bytes").asLong());
            assertEquals(0, root.path("directFiles").asLong());
            assertEquals(0, root.path("directBytes").asLong());
            assertTrue(root.path("hasChildren").asBoolean());

            JsonNode level = ok(server.get("/api/scans/" + id + "/folders?parent=" + q(t), cookie));
            assertEquals(List.of("Backup", "Photos"), names(level));
            JsonNode backup = byName(level, "Backup");
            assertEquals(t.resolve("Backup").toString(), backup.path("path").asString());
            assertEquals(4, backup.path("files").asLong());
            assertEquals(900L * KB, backup.path("bytes").asLong());
            assertEquals(0, backup.path("directFiles").asLong());
            assertTrue(backup.path("hasChildren").asBoolean());
            JsonNode photos = byName(level, "Photos");
            assertEquals(2, photos.path("files").asLong());
            assertEquals(500L * KB, photos.path("bytes").asLong());
            assertEquals(2, photos.path("directFiles").asLong());
            assertEquals(500L * KB, photos.path("directBytes").asLong());
            assertFalse(photos.path("hasChildren").asBoolean());
        }
    }

    @Test
    @DisplayName("DFS-047.AC-2 Folders without counted files do not appear; folders holding only subfolders roll up")
    void emptyFoldersAreLeftOut(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = background(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = completedScan(server, cookie, t);

            JsonNode level = ok(server.get("/api/scans/" + id + "/folders?parent=" + q(t), cookie));
            assertFalse(names(level).contains("Empty"), level.toString());

            JsonNode backup = ok(server.get("/api/scans/" + id + "/folders?parent=" + q(t.resolve("Backup")), cookie));
            assertEquals(List.of("2019", "old"), names(backup));
            assertEquals(3, byName(backup, "2019").path("files").asLong());
            assertEquals(600L * KB, byName(backup, "2019").path("bytes").asLong());
            assertEquals(1, byName(backup, "old").path("files").asLong());
            assertEquals(300L * KB, byName(backup, "old").path("bytes").asLong());
        }
    }

    @Test
    @DisplayName("DFS-047.AC-3 Every set, largest reclaimable first, with copies, links and totals")
    void everySetLargestFirst(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = background(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = completedScan(server, cookie, t);

            JsonNode page = ok(server.get("/api/scans/" + id + "/sets", cookie));

            assertEquals(2, page.path("items").size(), page.toString());
            assertEquals(2, page.path("totalItems").asInt());
            assertEquals(800L * KB, page.path("totalReclaimableBytes").asLong());
            JsonNode first = page.path("items").get(0);
            assertEquals(300L * KB, first.path("size").asLong());
            assertEquals(600L * KB, first.path("reclaimableBytes").asLong());
            assertFalse(first.path("digest").asString().isEmpty());
            assertEquals(3, first.path("copies").size());
            assertEquals(List.of(t.resolve("Backup/2019/a.jpg").toString(), t.resolve("Backup/old/a2.jpg").toString(),
                    t.resolve("Photos/a.jpg").toString()), linkPaths(first));
            JsonNode second = page.path("items").get(1);
            assertEquals(200L * KB, second.path("size").asLong());
            assertEquals(200L * KB, second.path("reclaimableBytes").asLong());
            assertEquals(2, second.path("copies").size());
            for (JsonNode copy : first.path("copies")) {
                for (JsonNode link : copy.path("links")) {
                    assertNotNull(Instant.parse(link.path("modified").asString()));
                    if (link.path("path").asString().equals(t.resolve("Photos/a.jpg").toString())) {
                        assertEquals(A_MTIME, Instant.parse(link.path("modified").asString()));
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("DFS-047.AC-3 Equal reclaimable bytes are ordered by size, largest first")
    void equalReclaimableOrderedBySize(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t2 = Files.createDirectories(tmp.resolve("t2")).toRealPath();
        for (String n : List.of("a", "b", "c")) {
            write(t2.resolve("small/" + n + ".bin"), content(100, 4));
        }
        write(t2.resolve("big/a.bin"), content(200, 5));
        write(t2.resolve("big/b.bin"), content(200, 5));
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = completedScan(server, cookie, t2);

            JsonNode page = ok(server.get("/api/scans/" + id + "/sets", cookie));

            assertEquals(2, page.path("items").size(), page.toString());
            assertEquals(200L * KB, page.path("items").get(0).path("size").asLong());
            assertEquals(100L * KB, page.path("items").get(1).path("size").asLong());
            assertEquals(200L * KB, page.path("items").get(0).path("reclaimableBytes").asLong());
            assertEquals(200L * KB, page.path("items").get(1).path("reclaimableBytes").asLong());
        }
    }

    @Test
    @DisplayName("DFS-047.AC-4 A hard link is not a reclaimable copy")
    void hardLinkIsNotReclaimable(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t3 = Files.createDirectories(tmp.resolve("t3")).toRealPath();
        write(t3.resolve("x.bin"), content(100, 6));
        try {
            Files.createLink(t3.resolve("x-link.bin"), t3.resolve("x.bin"));
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            Assumptions.abort("hard links cannot be created here: " + e);
        }
        write(t3.resolve("copy/x.bin"), content(100, 6));
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = completedScan(server, cookie, t3);

            JsonNode page = ok(server.get("/api/scans/" + id + "/sets", cookie));

            assertEquals(1, page.path("items").size(), page.toString());
            JsonNode set = page.path("items").get(0);
            assertEquals(100L * KB, set.path("reclaimableBytes").asLong());
            assertEquals(2, set.path("copies").size());
            List<String> linked = null;
            for (JsonNode copy : set.path("copies")) {
                if (copy.path("links").size() == 2) {
                    linked = new ArrayList<>();
                    for (JsonNode l : copy.path("links")) {
                        linked.add(l.path("path").asString());
                    }
                }
            }
            assertEquals(List.of(t3.resolve("x-link.bin").toString(), t3.resolve("x.bin").toString()), linked);
        }
    }

    @Test
    @DisplayName("DFS-047.AC-5 Sets under a folder keep their copies outside it")
    void setsUnderAFolder(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = background(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = completedScan(server, cookie, t);

            JsonNode page = ok(server.get("/api/scans/" + id + "/sets?under=" + q(t.resolve("Backup/old")), cookie));

            assertEquals(1, page.path("items").size(), page.toString());
            assertEquals(1, page.path("totalItems").asInt());
            assertEquals(600L * KB, page.path("totalReclaimableBytes").asLong());
            List<String> paths = linkPaths(page.path("items").get(0));
            assertTrue(paths.contains(t.resolve("Photos/a.jpg").toString()), paths.toString());
            assertTrue(paths.contains(t.resolve("Backup/2019/a.jpg").toString()), paths.toString());
        }
    }

    @Test
    @DisplayName("DFS-047.AC-5 Paging: a page of the ordered list, and a page past the end is empty with the same totals")
    void paging(@TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = background(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = completedScan(server, cookie, t);

            JsonNode second = ok(server.get("/api/scans/" + id + "/sets?page=1&size=1", cookie));
            assertEquals(1, second.path("items").size(), second.toString());
            assertEquals(200L * KB, second.path("items").get(0).path("size").asLong());
            assertEquals(1, second.path("page").asInt());
            assertEquals(1, second.path("size").asInt());
            assertEquals(2, second.path("totalItems").asInt());

            JsonNode past = ok(server.get("/api/scans/" + id + "/sets?page=5&size=1", cookie));
            assertEquals(0, past.path("items").size(), past.toString());
            assertEquals(2, past.path("totalItems").asInt());
            assertEquals(800L * KB, past.path("totalReclaimableBytes").asLong());
        }
    }

    @ParameterizedTest(name = "DFS-047.AC-6 {0} {1} answers {2} {3}")
    @CsvSource(delimiter = '|', value = {
            "folders | parent=OUTSIDE | 403 | PATH_OUTSIDE_ROOTS",
            "sets    | under=OUTSIDE  | 403 | PATH_OUTSIDE_ROOTS",
            "folders | parent=DOTDOT  | 403 | PATH_OUTSIDE_ROOTS",
            "folders | parent=Photos  | 400 | RELATIVE_PATH",
            "sets    | under=Photos   | 400 | RELATIVE_PATH",
            "folders | parent=EMPTY   | 200 | NO_ITEMS",
            "sets    | under=MISSING  | 200 | NO_SETS",
    })
    @DisplayName("DFS-047.AC-6 Paths are checked against the scan's roots")
    void pathsAreChecked(String endpoint, String param, int status, String expect, @TempDir Path tmp,
                         CapturedOutput output) throws Exception {
        Path t = background(tmp);
        Path outside = Files.createDirectories(tmp.resolve("other")).toRealPath();
        String name = param.substring(0, param.indexOf('='));
        String raw = param.substring(param.indexOf('=') + 1);
        String value = switch (raw) {
            case "OUTSIDE" -> q(outside);
            case "DOTDOT" -> URLEncoder.encode(
                    t + File.separator + "Photos" + File.separator + ".." + File.separator + "..",
                    StandardCharsets.UTF_8);
            case "EMPTY" -> q(t.resolve("Empty"));
            case "MISSING" -> q(t.resolve("no-such-folder"));
            default -> raw;
        };
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = completedScan(server, cookie, t);

            HttpResponse<String> r = server.get("/api/scans/" + id + "/" + endpoint + "?" + name + "=" + value, cookie);

            assertEquals(status, r.statusCode(), r.body());
            JsonNode body = ScanApiTest.body(r);
            if (status != 200) {
                assertEquals(expect, body.path("code").asString(), r.body());
                assertFalse(body.path("message").asString().isEmpty(), r.body());
            } else if (expect.equals("NO_ITEMS")) {
                assertEquals(0, body.size(), r.body());
            } else {
                assertEquals(0, body.path("items").size(), r.body());
                assertEquals(0, body.path("totalItems").asInt(), r.body());
            }
        }
    }

    @ParameterizedTest(name = "DFS-047.AC-7 GET {0} answers 409 SCAN_NOT_COMPLETED for a queued scan")
    @CsvSource({"folders", "sets"})
    @DisplayName("DFS-047.AC-7 A scan that is not completed answers 409")
    void queuedScanIsRefused(String endpoint, @TempDir Path tmp, CapturedOutput output) throws Exception {
        Path t = background(tmp);
        try (TestServer server = TestServer.start(new Class<?>[]{HoldingExecutorConfiguration.class})) {
            String cookie = server.login(server.token(output.getOut()));
            String id = ScanApiTest.start(server, cookie, ScanApiTest.startBody(t));

            HttpResponse<String> r = server.get("/api/scans/" + id + "/" + endpoint, cookie);

            assertEquals(409, r.statusCode(), r.body());
            assertEquals("SCAN_NOT_COMPLETED", ScanApiTest.body(r).path("code").asString(), r.body());
            assertFalse(ScanApiTest.body(r).path("message").asString().isEmpty(), r.body());
        }
    }

    @ParameterizedTest(name = "DFS-047.AC-7 GET {0}{1} answers {2} {3}")
    @CsvSource(delimiter = '|', value = {
            "00000000-0000-0000-0000-000000000000 | /folders       | 404 | UNKNOWN_SCAN",
            "not-a-uuid                           | /sets          | 404 | UNKNOWN_SCAN",
            "S                                    | /sets?page=-1  | 400 | INVALID_PAGE",
            "S                                    | /sets?size=0   | 400 | INVALID_PAGE",
            "S                                    | /sets?size=501 | 400 | INVALID_PAGE",
    })
    @DisplayName("DFS-047.AC-7 Unknown scans and bad paging are refused")
    void unknownAndBadPaging(String scan, String rest, int status, String code, @TempDir Path tmp,
                             CapturedOutput output) throws Exception {
        Path t = background(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = scan.equals("S") ? completedScan(server, cookie, t) : scan;

            HttpResponse<String> r = server.get("/api/scans/" + id + rest, cookie);

            assertEquals(status, r.statusCode(), r.body());
            assertEquals(code, ScanApiTest.body(r).path("code").asString(), r.body());
        }
    }

    @ParameterizedTest(name = "DFS-047.AC-8 GET {0} {1} is refused with 403")
    @CsvSource(delimiter = '|', value = {
            "folders | without the cookie",
            "sets    | without the cookie",
            "folders | with a foreign Host",
    })
    @DisplayName("DFS-047.AC-8 The result endpoints are secured")
    void securedEndpoints(String endpoint, String condition, @TempDir Path tmp, CapturedOutput output)
            throws Exception {
        Path t = background(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = completedScan(server, cookie, t);
            String path = "/api/scans/" + id + "/" + endpoint;

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
    @DisplayName("DFS-047.AC-9 Both responses carry every required property of their schema in openapi.json")
    void responsesMatchTheCommittedSchemas(@TempDir Path tmp, CapturedOutput output) throws Exception {
        String file = System.getProperty("dfs.openapi.file");
        assertNotNull(file, "system property dfs.openapi.file is not set");
        JsonNode schemas = JSON.readTree(Files.readString(Path.of(file))).path("components").path("schemas");
        Path t = background(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = completedScan(server, cookie, t);

            JsonNode folders = ok(server.get("/api/scans/" + id + "/folders", cookie));
            JsonNode sets = ok(server.get("/api/scans/" + id + "/sets", cookie));

            assertHasRequired(schemas, "FolderNode", folders.get(0));
            assertHasRequired(schemas, "SetPage", sets);
            JsonNode set = sets.path("items").get(0);
            assertHasRequired(schemas, "DuplicateSetView", set);
            assertHasRequired(schemas, "CopyView", set.path("copies").get(0));
            assertHasRequired(schemas, "LinkView", set.path("copies").get(0).path("links").get(0));
            assertTrue(schemas.has("ApiError"));
        }
    }

    @Test
    @DisplayName("DFS-047.AC-10 CLAUDE.md's server entry and ui.md U3 and U10 name the two result endpoints")
    void docsNameTheResultEndpoints(@TempDir Path tmp, CapturedOutput output) throws Exception {
        String file = System.getProperty("dfs.openapi.file");
        assertNotNull(file, "system property dfs.openapi.file is not set");
        // server/frontend/openapi.json -> the repository root
        Path root = Path.of(file).toAbsolutePath().getParent().getParent().getParent();
        Path t = background(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String id = completedScan(server, cookie, t);
            ok(server.get("/api/scans/" + id + "/folders", cookie));
            ok(server.get("/api/scans/" + id + "/sets", cookie));
        }

        String folders = "/api/scans/{id}/folders";
        String sets = "/api/scans/{id}/sets";

        String serverEntry = Files.readAllLines(root.resolve("CLAUDE.md"), StandardCharsets.UTF_8).stream()
                .filter(line -> line.startsWith("- `server`:"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("CLAUDE.md has no `server` entry"));
        assertTrue(serverEntry.contains("GET " + folders), "CLAUDE.md's server entry lacks GET " + folders);
        assertTrue(serverEntry.contains("GET " + sets), "CLAUDE.md's server entry lacks GET " + sets);
        String afterEndpoints = serverEntry.substring(serverEntry.indexOf(folders));
        assertTrue(afterEndpoints.contains("403") && afterEndpoints.contains("roots"),
                "CLAUDE.md's server entry does not state the 403 for a path outside the roots after the endpoints");

        String ui = Files.readString(root.resolve("docs/design/ui.md"), StandardCharsets.UTF_8);
        for (String section : List.of("### U3.", "### U10.")) {
            String body = section(ui, section);
            assertTrue(body.contains(folders), "ui.md " + section + " lacks " + folders);
            assertTrue(body.contains(sets), "ui.md " + section + " lacks " + sets);
        }
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
        JsonNode required = schemas.path(schema).path("required");
        assertTrue(required.size() > 0, schema + " has no required properties in openapi.json");
        for (JsonNode property : required) {
            assertTrue(actual.has(property.asString()), schema + " response lacks " + property.asString() + ": " + actual);
        }
    }
}
