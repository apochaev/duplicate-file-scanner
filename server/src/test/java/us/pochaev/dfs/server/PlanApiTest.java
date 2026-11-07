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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.KeepAndQuarantine;
import us.pochaev.dfs.scanner.domain.model.KeepCopy;
import us.pochaev.dfs.scanner.domain.model.PlanRequest;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanResult;
import us.pochaev.dfs.scanner.domain.usecases.query.PreviewDeletionPlan;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The plan preview API: rules, exceptions and picks previewed on a finished scan, kept and read back (DFS-054). */
@ExtendWith(OutputCaptureExtension.class)
class PlanApiTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final int KB = 1024;
    static final long MB = 1024L * 1024;
    static final String UNKNOWN = "00000000-0000-0000-0000-000000000000";

    /** A running server, a session and a COMPLETED scan of A, B and C. */
    record Env(TestServer server, String cookie, Path root, Path a, Path b, Path c, String id, String photo,
               String song) {

        String plans() {
            return "/api/scans/" + id + "/plans";
        }
    }

    /** The ticket's background: photo 2 MB in A and B, song 5 MB in A, B and C, note 1 KB only in B. */
    static void background(Path tmp) throws IOException {
        Path root = tmp.toRealPath();
        byte[] photo = ScanApiTest.bytes((int) (2 * MB), 1);
        byte[] song = ScanApiTest.bytes((int) (5 * MB), 2);
        Files.createDirectories(root.resolve("A"));
        Files.createDirectories(root.resolve("B"));
        Files.createDirectories(root.resolve("C"));
        Files.write(root.resolve("A/photo.jpg"), photo);
        Files.write(root.resolve("B/photo.jpg"), photo);
        Files.write(root.resolve("A/song.mp3"), song);
        Files.write(root.resolve("B/song.mp3"), song);
        Files.write(root.resolve("C/song.mp3"), song);
        Files.write(root.resolve("B/note.txt"), ScanApiTest.bytes(KB, 3));
    }

    static Env env(TestServer server, CapturedOutput output, Path tmp) throws Exception {
        return env(server, server.login(server.token(output.getOut())), tmp);
    }

    static Env env(TestServer server, String cookie, Path tmp) throws Exception {
        Path root = tmp.toRealPath();
        String id = ScanApiTest.start(server, cookie,
                ScanApiTest.startBody(root.resolve("A"), root.resolve("B"), root.resolve("C")));
        JsonNode done = ScanApiTest.awaitTerminal(server, cookie, id);
        assertEquals("COMPLETED", ScanApiTest.status(done), done.toString());
        JsonNode sets = ScanApiTest.body(server.get("/api/scans/" + id + "/sets", cookie));
        String photo = null;
        String song = null;
        for (JsonNode set : sets.path("items")) {
            if (set.path("size").asLong() == 2 * MB) {
                photo = set.path("digest").asString();
            } else if (set.path("size").asLong() == 5 * MB) {
                song = set.path("digest").asString();
            }
        }
        return new Env(server, cookie, root, root.resolve("A"), root.resolve("B"), root.resolve("C"), id, photo, song);
    }

    // ---- request bodies -------------------------------------------------------------------------------------

    static ObjectNode request() {
        ObjectNode n = JSON.createObjectNode();
        n.putArray("rules");
        n.putArray("exceptions");
        n.putArray("picks");
        return n;
    }

    static ObjectNode rule(ObjectNode request, Object keep, Object quarantineIn) {
        ObjectNode r = ((ArrayNode) request.get("rules")).addObject();
        r.put("keep", keep.toString());
        r.put("quarantineIn", quarantineIn.toString());
        return request;
    }

    static ObjectNode exception(ObjectNode request, Object folder) {
        ((ArrayNode) request.get("exceptions")).add(folder.toString());
        return request;
    }

    static ObjectNode pick(ObjectNode request, String digest, Object keep) {
        ObjectNode p = ((ArrayNode) request.get("picks")).addObject();
        p.put("digest", digest);
        p.put("keep", keep.toString());
        return request;
    }

    static ObjectNode keepAQuarantineB(Env e) {
        return rule(request(), e.a(), e.b());
    }

    static ObjectNode keepAQuarantineC(Env e) {
        return rule(request(), e.a(), e.c());
    }

    static ObjectNode crossing(Env e) {
        return rule(rule(request(), e.a(), e.b()), e.b(), e.a());
    }

    static HttpResponse<String> preview(Env e, ObjectNode body) throws Exception {
        return e.server().sendJson("POST", e.plans(), e.cookie(), e.server().origin(), body.toString());
    }

    static JsonNode created(Env e, ObjectNode body) throws Exception {
        HttpResponse<String> r = preview(e, body);
        assertEquals(201, r.statusCode(), r.body());
        JsonNode summary = ScanApiTest.body(r);
        assertEquals(e.plans() + "/" + summary.path("planId").asString(),
                r.headers().firstValue("Location").orElse(null));
        return summary;
    }

    static JsonNode ok(HttpResponse<String> r) {
        assertEquals(200, r.statusCode(), r.body());
        return ScanApiTest.body(r);
    }

    static JsonNode operations(Env e, String planId, String query) throws Exception {
        return ok(e.server().get(e.plans() + "/" + planId + "/operations" + query, e.cookie()));
    }

    static JsonNode conflicts(Env e, String planId, String query) throws Exception {
        return ok(e.server().get(e.plans() + "/" + planId + "/conflicts" + query, e.cookie()));
    }

    static void assertError(HttpResponse<String> r, int status, String code) {
        assertEquals(status, r.statusCode(), r.body());
        JsonNode body = ScanApiTest.body(r);
        assertEquals(code, body.path("code").asString(), r.body());
        assertFalse(body.path("message").asString().isEmpty(), r.body());
    }

    // ---- the core, for the same scan and request ------------------------------------------------------------

    static Result<DeletionPlan> core(Env e, PlanRequest request) {
        ScanResult scan = switch (e.server().context().getBean(GetScanResult.class)
                .result(new ScanId(UUID.fromString(e.id())))) {
            case Result.Success<ScanResult> s -> s.value();
            case Result.Failure<ScanResult> f -> throw new AssertionError("no result: " + f);
        };
        return e.server().context().getBean(PreviewDeletionPlan.class).preview(scan, request);
    }

    static String coreId(Env e, PlanRequest request) {
        return switch (core(e, request)) {
            case Result.Success<DeletionPlan> s -> s.value().id();
            case Result.Failure<DeletionPlan> f -> throw new AssertionError("core refused: " + f);
        };
    }

    static PlanRequest keepAQuarantineBCore(Env e) {
        return new PlanRequest(List.of(new KeepAndQuarantine(e.a(), e.b())), List.of(), List.of());
    }

    // ---- disk snapshot --------------------------------------------------------------------------------------

    static Map<String, String> snapshot(Path root) throws IOException {
        Map<String, String> m = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                m.put(p.toString(), Files.isDirectory(p) ? "dir" : Files.size(p) + "|" + Files.getLastModifiedTime(p));
            }
        }
        return m;
    }

    // ---- sets: the invariant check --------------------------------------------------------------------------

    /** Every set of the scan keeps at least one physical copy that no operation of the plan moves. */
    static void assertEverySetKeepsACopy(Env e, String planId) throws Exception {
        JsonNode ops = operations(e, planId, "?size=500");
        JsonNode sets = ok(e.server().get("/api/scans/" + e.id() + "/sets?size=500", e.cookie()));
        assertTrue(sets.path("items").size() > 0, sets.toString());
        for (JsonNode set : sets.path("items")) {
            Set<String> moved = new HashSet<>();
            for (JsonNode op : ops.path("items")) {
                if (op.path("digest").asString().equals(set.path("digest").asString())) {
                    op.path("links").forEach(l -> moved.add(l.asString()));
                }
            }
            boolean survives = false;
            for (JsonNode copy : set.path("copies")) {
                boolean copyMoved = false;
                for (JsonNode link : copy.path("links")) {
                    copyMoved |= moved.contains(link.path("path").asString());
                }
                survives |= !copyMoved;
            }
            assertTrue(survives, "set " + set.path("digest").asString() + " lost its last copy: " + ops);
        }
    }

    static List<Integer> ints(JsonNode array) {
        List<Integer> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asInt()));
        return out;
    }

    static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asString()));
        return out;
    }

    // ---- AC-1 -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("DFS-054.AC-1 A rule previews to a kept plan with 201, Location and the summary")
    void ruleCreatesPlan(@TempDir Path tmp, CapturedOutput output) throws Exception {
        background(tmp);
        try (TestServer server = TestServer.start()) {
            Env e = env(server, output, tmp);

            HttpResponse<String> r = preview(e, keepAQuarantineB(e));

            assertEquals(201, r.statusCode(), r.body());
            JsonNode s = ScanApiTest.body(r);
            String planId = s.path("planId").asString();
            assertEquals(e.plans() + "/" + planId, r.headers().firstValue("Location").orElse(null));
            assertEquals(coreId(e, keepAQuarantineBCore(e)), planId);
            assertEquals(2, s.path("totals").path("files").asLong());
            assertEquals(7 * MB, s.path("totals").path("bytes").asLong());
            assertEquals(2, s.path("totals").path("sets").asInt());
            assertEquals(1, s.path("leftBehind").size(), s.toString());
            JsonNode left = s.path("leftBehind").get(0);
            assertEquals(0, left.path("ruleIndex").asInt());
            assertEquals(e.b().toString(), left.path("folder").asString());
            assertEquals(1, left.path("files").asLong());
            assertEquals(KB, left.path("bytes").asLong());
            assertEquals(2, s.path("operationCount").asInt());
            assertEquals(0, s.path("conflictCount").asInt());
        }
    }

    // ---- AC-2 -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("DFS-054.AC-2 Every previewed plan stays readable, an earlier one after a later preview")
    void everyPlanStaysReadable(@TempDir Path tmp, CapturedOutput output) throws Exception {
        background(tmp);
        try (TestServer server = TestServer.start()) {
            Env e = env(server, output, tmp);
            JsonNode p1 = created(e, keepAQuarantineB(e));
            JsonNode p2 = created(e, keepAQuarantineC(e));
            assertNotEquals(p1.path("planId").asString(), p2.path("planId").asString());

            JsonNode got1 = ok(server.get(e.plans() + "/" + p1.path("planId").asString(), e.cookie()));
            JsonNode got2 = ok(server.get(e.plans() + "/" + p2.path("planId").asString(), e.cookie()));

            assertEquals(p1, got1);
            assertEquals(p2, got2);
        }
    }

    @Test
    @DisplayName("DFS-054.AC-2 The same request previews to the same plan")
    void sameRequestSamePlan(@TempDir Path tmp, CapturedOutput output) throws Exception {
        background(tmp);
        try (TestServer server = TestServer.start()) {
            Env e = env(server, output, tmp);

            JsonNode first = created(e, keepAQuarantineB(e));
            JsonNode second = created(e, keepAQuarantineB(e));

            assertEquals(first.path("planId").asString(), second.path("planId").asString());
            assertEquals(first, second);
        }
    }

    @Test
    @DisplayName("DFS-054.AC-2 A plan of another scan is unknown here (UNKNOWN_PLAN)")
    void planOfAnotherScanIsUnknown(@TempDir Path tmp, @TempDir Path tmp2, CapturedOutput output) throws Exception {
        background(tmp);
        background(tmp2);
        try (TestServer server = TestServer.start()) {
            Env e = env(server, output, tmp);
            Env other = env(server, e.cookie(), tmp2);
            String planId = created(e, keepAQuarantineB(e)).path("planId").asString();

            assertError(server.get(other.plans() + "/" + planId, e.cookie()), 404, "UNKNOWN_PLAN");
            assertError(server.get(e.plans() + "/never-previewed", e.cookie()), 404, "UNKNOWN_PLAN");
            ok(server.get(e.plans() + "/" + planId, e.cookie()));
        }
    }

    // ---- AC-3 -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("DFS-054.AC-3 Operations come largest first with their keeper, links and source")
    void operationsLargestFirst(@TempDir Path tmp, CapturedOutput output) throws Exception {
        background(tmp);
        try (TestServer server = TestServer.start()) {
            Env e = env(server, output, tmp);
            ObjectNode body = pick(keepAQuarantineB(e), e.song(), e.c().resolve("song.mp3"));
            String planId = created(e, body).path("planId").asString();

            JsonNode page = operations(e, planId, "");

            JsonNode items = page.path("items");
            assertEquals(3, items.size(), page.toString());
            assertEquals(3, page.path("totalItems").asInt());
            for (int i = 0; i < 2; i++) {
                JsonNode op = items.get(i);
                assertEquals(5 * MB, op.path("size").asLong());
                assertEquals(e.song(), op.path("digest").asString());
                assertEquals(e.c().resolve("song.mp3").toString(), op.path("keeper").asString());
                assertEquals("pick", op.path("source").path("kind").asString());
                assertEquals(e.song(), op.path("source").path("digest").asString());
            }
            Set<String> moved = new HashSet<>(List.of(items.get(0).path("links").get(0).asString(),
                    items.get(1).path("links").get(0).asString()));
            assertEquals(Set.of(e.a().resolve("song.mp3").toString(), e.b().resolve("song.mp3").toString()), moved);
            JsonNode last = items.get(2);
            assertEquals(2 * MB, last.path("size").asLong());
            assertEquals(e.photo(), last.path("digest").asString());
            assertEquals(List.of(e.b().resolve("photo.jpg").toString()), strings(last.path("links")));
            assertEquals(e.a().resolve("photo.jpg").toString(), last.path("keeper").asString());
            assertEquals("rule", last.path("source").path("kind").asString());
            assertEquals(0, last.path("source").path("ruleIndex").asInt());
        }
    }

    // ---- AC-4 and AC-6 --------------------------------------------------------------------------------------

    @Test
    @DisplayName("DFS-054.AC-4, DFS-054.AC-6 Crossing rules become conflicts, never a lost last copy")
    void crossingRulesConflict(@TempDir Path tmp, CapturedOutput output) throws Exception {
        background(tmp);
        try (TestServer server = TestServer.start()) {
            Env e = env(server, output, tmp);
            JsonNode summary = created(e, crossing(e));
            String planId = summary.path("planId").asString();

            JsonNode page = conflicts(e, planId, "");

            assertEquals(summary.path("conflictCount").asInt(), page.path("totalItems").asInt());
            assertTrue(page.path("totalItems").asInt() >= 1, page.toString());
            long previous = Long.MAX_VALUE;
            JsonNode photo = null;
            for (JsonNode c : page.path("items")) {
                assertTrue(c.path("size").asLong() <= previous, "largest first: " + page);
                previous = c.path("size").asLong();
                if (c.path("digest").asString().equals(e.photo())) {
                    photo = c;
                }
            }
            assertNotNull(photo, page.toString());
            assertEquals(2 * MB, photo.path("size").asLong());
            assertEquals(2, photo.path("copies").size());
            for (JsonNode copy : photo.path("copies")) {
                String link = copy.path("links").get(0).asString();
                if (link.equals(e.a().resolve("photo.jpg").toString())) {
                    assertEquals(List.of(1), ints(copy.path("quarantinedBy")));
                    assertEquals(List.of(0), ints(copy.path("keptBy")));
                } else {
                    assertEquals(e.b().resolve("photo.jpg").toString(), link);
                    assertEquals(List.of(0), ints(copy.path("quarantinedBy")));
                    assertEquals(List.of(1), ints(copy.path("keptBy")));
                }
            }
            JsonNode sources = photo.path("sources");
            assertEquals(2, sources.size(), photo.toString());
            assertEquals("rule", sources.get(0).path("kind").asString());
            assertEquals(0, sources.get(0).path("ruleIndex").asInt());
            assertEquals("rule", sources.get(1).path("kind").asString());
            assertEquals(1, sources.get(1).path("ruleIndex").asInt());

            JsonNode ops = operations(e, planId, "?size=500");
            for (JsonNode op : ops.path("items")) {
                assertNotEquals(e.photo(), op.path("digest").asString(), ops.toString());
            }
            assertEverySetKeepsACopy(e, planId);
        }
    }

    @Test
    @DisplayName("DFS-054.AC-6 Every plan the API answers leaves each set one copy no operation moves")
    void everyPlanKeepsACopy(@TempDir Path tmp, CapturedOutput output) throws Exception {
        background(tmp);
        try (TestServer server = TestServer.start()) {
            Env e = env(server, output, tmp);
            List<ObjectNode> requests = List.of(
                    keepAQuarantineB(e),
                    keepAQuarantineC(e),
                    rule(rule(request(), e.a(), e.b()), e.a(), e.c()),
                    pick(request(), e.song(), e.c().resolve("song.mp3")),
                    rule(rule(request(), e.c(), e.a()), e.c(), e.b()));
            for (ObjectNode body : requests) {
                String planId = created(e, body).path("planId").asString();
                assertEverySetKeepsACopy(e, planId);
            }
        }
    }

    // ---- AC-5 -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("DFS-054.AC-5 Operations and conflicts are paged like sets")
    void paging(@TempDir Path tmp, CapturedOutput output) throws Exception {
        background(tmp);
        try (TestServer server = TestServer.start()) {
            Env e = env(server, output, tmp);
            String planId = created(e, keepAQuarantineB(e)).path("planId").asString();
            JsonNode crossing = created(e, crossing(e));
            String crossingId = crossing.path("planId").asString();
            int conflictTotal = crossing.path("conflictCount").asInt();

            JsonNode first = operations(e, planId, "?page=0&size=1");
            assertEquals(1, first.path("items").size(), first.toString());
            assertEquals(2, first.path("totalItems").asInt());
            assertEquals(0, first.path("page").asInt());
            assertEquals(1, first.path("size").asInt());
            assertEquals(5 * MB, first.path("items").get(0).path("size").asLong());
            JsonNode second = operations(e, planId, "?page=1&size=1");
            assertEquals(2 * MB, second.path("items").get(0).path("size").asLong());
            JsonNode past = operations(e, planId, "?page=5&size=1");
            assertEquals(0, past.path("items").size(), past.toString());
            assertEquals(2, past.path("totalItems").asInt());

            JsonNode conflictFirst = conflicts(e, crossingId, "?page=0&size=1");
            assertEquals(1, conflictFirst.path("items").size(), conflictFirst.toString());
            assertEquals(conflictTotal, conflictFirst.path("totalItems").asInt());
            JsonNode conflictPast = conflicts(e, crossingId, "?page=5&size=1");
            assertEquals(0, conflictPast.path("items").size(), conflictPast.toString());
            assertEquals(conflictTotal, conflictPast.path("totalItems").asInt());

            for (String kind : List.of("operations", "conflicts")) {
                for (String bad : List.of("?size=0", "?size=501", "?page=-1")) {
                    assertError(server.get(e.plans() + "/" + planId + "/" + kind + bad, e.cookie()), 400,
                            "INVALID_PAGE");
                }
                assertError(server.get(e.plans() + "/never-previewed/" + kind, e.cookie()), 404, "UNKNOWN_PLAN");
            }
        }
    }

    // ---- AC-7 -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("DFS-054.AC-7 A hard-linked copy moves with all its links, and its keeper is another copy")
    void hardLinkedCopyMovesWithItsLinks(@TempDir Path tmp, CapturedOutput output) throws Exception {
        // The ticket puts the link in B; the core quarantines a copy only when EVERY link lies under quarantineIn
        // (PreviewDeletionPlanService.allUnder), so both links of the copy are in A here (see test-plan notes).
        background(tmp);
        Path root = tmp.toRealPath();
        Files.write(root.resolve("C/photo.jpg"), ScanApiTest.bytes((int) (2 * MB), 1));
        try {
            Files.createLink(root.resolve("A/photo-link.jpg"), root.resolve("A/photo.jpg"));
        } catch (IOException | UnsupportedOperationException | SecurityException ex) {
            Assumptions.abort("hard links cannot be created here: " + ex);
        }
        try (TestServer server = TestServer.start()) {
            Env e = env(server, output, tmp);
            String planId = created(e, rule(request(), e.c(), e.a())).path("planId").asString();

            JsonNode items = operations(e, planId, "?size=500").path("items");

            List<JsonNode> photoOps = new ArrayList<>();
            for (JsonNode op : items) {
                if (op.path("digest").asString().equals(e.photo())) {
                    photoOps.add(op);
                }
            }
            assertEquals(1, photoOps.size(), items.toString());
            JsonNode op = photoOps.get(0);
            assertEquals(List.of(e.a().resolve("photo-link.jpg").toString(), e.a().resolve("photo.jpg").toString()),
                    strings(op.path("links")).stream().sorted().toList());
            assertEquals(e.c().resolve("photo.jpg").toString(), op.path("keeper").asString());
            for (JsonNode any : items) {
                assertFalse(strings(any.path("links")).contains(any.path("keeper").asString()), any.toString());
            }
        }
    }

    // ---- AC-8 -----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("DFS-054.AC-8 Previewing, answered or refused, touches nothing on disk")
    void previewingTouchesNothing(@TempDir Path tmp, CapturedOutput output) throws Exception {
        background(tmp);
        try (TestServer server = TestServer.start()) {
            Env e = env(server, output, tmp);
            Map<String, String> before = snapshot(e.root());

            created(e, keepAQuarantineB(e));
            assertError(preview(e, rule(request(), "A", e.b())), 400, "RELATIVE_PATH");
            created(e, crossing(e));

            assertEquals(before, snapshot(e.root()));
        }
    }

    // ---- AC-9 -----------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "DFS-054.AC-9 {0} = {1} is refused with {2} {3}")
    @CsvSource(delimiter = '|', value = {
            "rules[0].keep         | RELATIVE | 400 | RELATIVE_PATH",
            "rules[0].quarantineIn | DOTDOT   | 403 | PATH_OUTSIDE_ROOTS",
            "exceptions[0]         | ELSEWHERE| 403 | PATH_OUTSIDE_ROOTS",
            "picks[0].keep         | RELATIVE | 400 | RELATIVE_PATH",
    })
    @DisplayName("DFS-054.AC-9 Request paths are checked first, the field named")
    void pathsAreChecked(String field, String kind, int status, String code, @TempDir Path tmp,
                         @TempDir Path elsewhere, CapturedOutput output) throws Exception {
        background(tmp);
        try (TestServer server = TestServer.start()) {
            Env e = env(server, output, tmp);
            String kept = created(e, keepAQuarantineB(e)).path("planId").asString();
            String bad = switch (kind) {
                case "RELATIVE" -> field.startsWith("picks") ? "song.mp3" : "A";
                case "DOTDOT" -> e.a() + File.separator + ".." + File.separator + ".." + File.separator + "other";
                default -> elsewhere.toRealPath().toString();
            };
            ObjectNode body = switch (field) {
                case "rules[0].keep" -> rule(request(), bad, e.b());
                case "rules[0].quarantineIn" -> rule(request(), e.a(), bad);
                case "exceptions[0]" -> exception(rule(request(), e.a(), e.b()), bad);
                default -> pick(request(), e.song(), bad);
            };

            HttpResponse<String> r = preview(e, body);

            assertError(r, status, code);
            assertTrue(ScanApiTest.body(r).path("message").asString().contains(field), r.body());
            assertTrue(r.headers().firstValue("Location").isEmpty(), "no Location on a refusal");
            ok(server.get(e.plans() + "/" + kept, e.cookie()));
        }
    }

    // ---- AC-10 ----------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "DFS-054.AC-10 {0} is refused by the core as {1}")
    @CsvSource({"invalid-rule,INVALID_RULE", "unknown-set,UNKNOWN_SET", "path-not-in-set,PATH_NOT_IN_SET",
            "duplicate-pick,DUPLICATE_PICK"})
    @DisplayName("DFS-054.AC-10 The core's refusals pass through with the same code and message")
    void coreRefusals(String scenario, String code, @TempDir Path tmp, CapturedOutput output) throws Exception {
        background(tmp);
        try (TestServer server = TestServer.start()) {
            Env e = env(server, output, tmp);
            ObjectNode body;
            PlanRequest core;
            switch (scenario) {
                case "invalid-rule" -> {
                    body = rule(request(), e.a(), e.a().resolve("sub"));
                    core = new PlanRequest(List.of(new KeepAndQuarantine(e.a(), e.a().resolve("sub"))), List.of(),
                            List.of());
                }
                case "unknown-set" -> {
                    body = pick(request(), "0000", e.a().resolve("photo.jpg"));
                    core = new PlanRequest(List.of(), List.of(), List.of(new KeepCopy("0000",
                            e.a().resolve("photo.jpg"))));
                }
                case "path-not-in-set" -> {
                    body = pick(request(), e.song(), e.b().resolve("note.txt"));
                    core = new PlanRequest(List.of(), List.of(), List.of(new KeepCopy(e.song(),
                            e.b().resolve("note.txt"))));
                }
                default -> {
                    body = pick(pick(request(), e.song(), e.a().resolve("song.mp3")), e.song(),
                            e.b().resolve("song.mp3"));
                    core = new PlanRequest(List.of(), List.of(), List.of(
                            new KeepCopy(e.song(), e.a().resolve("song.mp3")),
                            new KeepCopy(e.song(), e.b().resolve("song.mp3"))));
                }
            }
            Result.Failure<DeletionPlan> expected = switch (core(e, core)) {
                case Result.Failure<DeletionPlan> f -> f;
                case Result.Success<DeletionPlan> s -> throw new AssertionError("the core accepted " + core);
            };
            assertEquals(code, expected.code().name());

            HttpResponse<String> r = preview(e, body);

            assertError(r, 400, code);
            assertEquals(expected.message(), ScanApiTest.body(r).path("message").asString());
            assertTrue(r.headers().firstValue("Location").isEmpty());
        }
    }

    @Test
    @DisplayName("DFS-054.AC-10 Only the first problem is reported")
    void onlyTheFirstProblem(@TempDir Path tmp, CapturedOutput output) throws Exception {
        background(tmp);
        try (TestServer server = TestServer.start()) {
            Env e = env(server, output, tmp);
            ObjectNode body = pick(rule(request(), e.a(), e.a().resolve("sub")), "0000", e.a().resolve("photo.jpg"));

            assertError(preview(e, body), 400, "INVALID_RULE");
        }
    }

    @Test
    @DisplayName("DFS-054.AC-10 An empty request previews an empty plan")
    void emptyRequest(@TempDir Path tmp, CapturedOutput output) throws Exception {
        background(tmp);
        try (TestServer server = TestServer.start()) {
            Env e = env(server, output, tmp);

            JsonNode s = created(e, request());

            assertEquals(0, s.path("totals").path("files").asLong());
            assertEquals(0, s.path("totals").path("bytes").asLong());
            assertEquals(0, s.path("totals").path("sets").asInt());
            assertEquals(0, s.path("leftBehind").size());
            assertEquals(0, s.path("operationCount").asInt());
            assertEquals(0, s.path("conflictCount").asInt());
            assertEquals(0, operations(e, s.path("planId").asString(), "").path("totalItems").asInt());
            assertEquals(s, ok(server.get(e.plans() + "/" + s.path("planId").asString(), e.cookie())));
        }
    }

    // ---- AC-11 ----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("DFS-054.AC-11 A scan that is not COMPLETED answers 409 before the body or any parameter")
    void notCompletedScan(@TempDir Path tmp, CapturedOutput output) throws Exception {
        background(tmp);
        try (TestServer server = TestServer.start(new Class<?>[]{HoldingExecutorConfiguration.class})) {
            String cookie = server.login(server.token(output.getOut()));
            Path root = tmp.toRealPath();
            String id = ScanApiTest.start(server, cookie, ScanApiTest.startBody(root.resolve("A")));
            String plans = "/api/scans/" + id + "/plans";

            assertError(server.sendJson("POST", plans, cookie, server.origin(),
                    rule(request(), "A", "B").toString()), 409, "SCAN_NOT_COMPLETED");
            assertError(server.get(plans + "/x", cookie), 409, "SCAN_NOT_COMPLETED");
            assertError(server.get(plans + "/x/operations?page=-1", cookie), 409, "SCAN_NOT_COMPLETED");
            assertError(server.get(plans + "/x/conflicts?size=0", cookie), 409, "SCAN_NOT_COMPLETED");
        }
    }

    @ParameterizedTest(name = "DFS-054.AC-11 Scan ID {0} is unknown to all four endpoints")
    @CsvSource({"not-a-uuid", UNKNOWN})
    @DisplayName("DFS-054.AC-11 An unknown or malformed scan ID answers 404 before the body or any parameter")
    void unknownScan(String scan, @TempDir Path tmp, CapturedOutput output) throws Exception {
        background(tmp);
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String plans = "/api/scans/" + scan + "/plans";

            assertError(server.sendJson("POST", plans, cookie, server.origin(),
                    rule(request(), "A", "B").toString()), 404, "UNKNOWN_SCAN");
            assertError(server.get(plans + "/x", cookie), 404, "UNKNOWN_SCAN");
            assertError(server.get(plans + "/x/operations?page=-1", cookie), 404, "UNKNOWN_SCAN");
            assertError(server.get(plans + "/x/conflicts?size=0", cookie), 404, "UNKNOWN_SCAN");
        }
    }

    // ---- AC-12 ----------------------------------------------------------------------------------------------

    /** A POST over a plain socket, because HttpClient does not let a caller set Host; returns the status code. */
    static int rawPost(TestServer server, String path, String host, String cookie, String origin, String json)
            throws IOException {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        String head = "POST " + path + " HTTP/1.1\r\nHost: " + host + "\r\nCookie: " + cookie + "\r\nOrigin: " + origin
                + "\r\nContent-Type: application/json\r\nContent-Length: " + payload.length
                + "\r\nConnection: close\r\n\r\n";
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            OutputStream out = socket.getOutputStream();
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.write(payload);
            out.flush();
            String status = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)).readLine();
            return Integer.parseInt(status.split(" ")[1]);
        }
    }

    @Test
    @DisplayName("DFS-054.AC-12 The plan endpoints answer 403 without the cookie, with a foreign Host or Origin")
    void securedEndpoints(@TempDir Path tmp, CapturedOutput output) throws Exception {
        background(tmp);
        try (TestServer server = TestServer.start()) {
            Env e = env(server, output, tmp);
            String planId = created(e, keepAQuarantineB(e)).path("planId").asString();
            String foreignHost = "evil.example:" + server.port();
            String refusedBody = keepAQuarantineC(e).toString();
            String refusedId = coreId(e, new PlanRequest(List.of(new KeepAndQuarantine(e.a(), e.c())), List.of(),
                    List.of()));
            List<String> gets = List.of(e.plans() + "/" + planId, e.plans() + "/" + planId + "/operations",
                    e.plans() + "/" + planId + "/conflicts");

            for (String path : gets) {
                assertEquals(403, server.get(path, null).statusCode(), path);
                assertEquals(403, server.rawGet(path, foreignHost, e.cookie()), path);
            }
            assertEquals(403, server.sendJson("POST", e.plans(), null, server.origin(), refusedBody).statusCode());
            assertEquals(403, rawPost(server, e.plans(), foreignHost, e.cookie(), server.origin(), refusedBody));
            assertEquals(403, server.sendJson("POST", e.plans(), e.cookie(), "http://evil.example", refusedBody)
                    .statusCode());
            assertEquals(403, server.sendJson("POST", e.plans(), e.cookie(), null, refusedBody).statusCode());

            // The refused previews kept nothing; the guarded reads work with the cookie and the right Host.
            assertError(server.get(e.plans() + "/" + refusedId, e.cookie()), 404, "UNKNOWN_PLAN");
            for (String path : gets) {
                ok(server.get(path, e.cookie()));
            }
        }
    }

    // ---- AC-13 ----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("DFS-054.AC-13 Concurrent previews of different requests are each kept")
    void concurrentPreviews(@TempDir Path tmp, CapturedOutput output) throws Exception {
        background(tmp);
        try (TestServer server = TestServer.start()) {
            Env e = env(server, output, tmp);
            // Nothing is kept yet; this read, on the test thread, also surfaces a stub's exception here.
            assertError(server.get(e.plans() + "/none-yet", e.cookie()), 404, "UNKNOWN_PLAN");
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                List<Future<HttpResponse<String>>> futures = new ArrayList<>();
                for (ObjectNode body : List.of(keepAQuarantineB(e), keepAQuarantineC(e))) {
                    futures.add(pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        return preview(e, body);
                    }));
                }
                assertTrue(ready.await(30, TimeUnit.SECONDS));
                go.countDown();
                List<String> ids = new ArrayList<>();
                for (Future<HttpResponse<String>> f : futures) {
                    HttpResponse<String> r = join(f);
                    assertEquals(201, r.statusCode(), r.body());
                    ids.add(ScanApiTest.body(r).path("planId").asString());
                }
                assertNotEquals(ids.get(0), ids.get(1));
                for (String id : ids) {
                    assertEquals(id, ok(server.get(e.plans() + "/" + id, e.cookie())).path("planId").asString());
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }

    /** Waits for a task; rethrows what it threw (a stub's exception included), not an ExecutionException. */
    private static <T> T join(Future<T> f) throws Exception {
        try {
            return f.get(60, TimeUnit.SECONDS);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof Exception cause) {
                throw cause;
            }
            if (ex.getCause() instanceof Error cause) {
                throw cause;
            }
            throw ex;
        }
    }

    // ---- AC-14 ----------------------------------------------------------------------------------------------

    private static void assertHasRequired(JsonNode schemas, String schema, JsonNode actual) {
        JsonNode required = schemas.path(schema).path("required");
        assertTrue(required.size() > 0, schema + " has no required properties in openapi.json");
        for (JsonNode property : required) {
            assertTrue(actual.has(property.asString()), schema + " response lacks " + property.asString() + ": " + actual);
        }
    }

    @Test
    @DisplayName("DFS-054.AC-14 The committed spec describes the four plan operations and their schemas")
    void specDescribesPlans(@TempDir Path tmp, CapturedOutput output) throws Exception {
        String file = System.getProperty("dfs.openapi.file");
        assertNotNull(file, "system property dfs.openapi.file is not set");
        background(tmp);
        try (TestServer server = TestServer.start()) {
            Env e = env(server, output, tmp);
            JsonNode summary = created(e, crossing(e));
            String planId = summary.path("planId").asString();
            JsonNode ops = operations(e, planId, "");
            JsonNode conflicts = conflicts(e, planId, "");
            JsonNode created = created(e, keepAQuarantineB(e));
            JsonNode opsOfB = operations(e, created.path("planId").asString(), "");

            JsonNode spec = JSON.readTree(Files.readString(Path.of(file)));
            JsonNode schemas = spec.path("components").path("schemas");
            Map<String, String> operationIds = Map.of(
                    "post /api/scans/{id}/plans", "previewPlan",
                    "get /api/scans/{id}/plans/{planId}", "getPlan",
                    "get /api/scans/{id}/plans/{planId}/operations", "listPlanOperations",
                    "get /api/scans/{id}/plans/{planId}/conflicts", "listPlanConflicts");
            operationIds.forEach((key, operationId) -> {
                String[] parts = key.split(" ");
                JsonNode operation = spec.path("paths").path(parts[1]).path(parts[0]);
                assertEquals(operationId, operation.path("operationId").asString(), key);
                assertEquals("plans", operation.path("tags").path(0).asString(), key);
            });
            for (String schema : List.of("PreviewPlanRequest", "PlanRuleRequest", "PlanPickRequest", "PlanSummary",
                    "PlanTotalsView", "LeftBehindView", "PlanSource", "PlanOperation", "OperationPage",
                    "PlanConflict", "ConflictCopy", "ConflictPage")) {
                assertTrue(schemas.has(schema), "openapi.json lacks schema " + schema);
            }

            assertHasRequired(schemas, "PlanSummary", summary);
            assertHasRequired(schemas, "PlanTotalsView", summary.path("totals"));
            assertHasRequired(schemas, "OperationPage", ops);
            assertHasRequired(schemas, "PlanOperation", opsOfB.path("items").get(0));
            assertHasRequired(schemas, "ConflictPage", conflicts);
            JsonNode conflict = conflicts.path("items").get(0);
            assertHasRequired(schemas, "PlanConflict", conflict);
            assertHasRequired(schemas, "ConflictCopy", conflict.path("copies").get(0));
            assertHasRequired(schemas, "PlanSource", conflict.path("sources").get(0));
            assertHasRequired(schemas, "LeftBehindView", created.path("leftBehind").get(0));
        }
    }
}
