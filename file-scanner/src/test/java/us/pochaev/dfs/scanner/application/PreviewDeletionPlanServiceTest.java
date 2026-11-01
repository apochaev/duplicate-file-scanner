package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.pochaev.dfs.scanner.domain.model.Conflict;
import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.ErrorCode;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.FolderTotal;
import us.pochaev.dfs.scanner.domain.model.KeepAndQuarantine;
import us.pochaev.dfs.scanner.domain.model.KeepCopy;
import us.pochaev.dfs.scanner.domain.model.LeftBehind;
import us.pochaev.dfs.scanner.domain.model.Operation;
import us.pochaev.dfs.scanner.domain.model.PhysicalCopy;
import us.pochaev.dfs.scanner.domain.model.PlanRequest;
import us.pochaev.dfs.scanner.domain.model.PlanTotals;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.model.Source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Value tests of the deletion-plan preview (DFS-022): hand-built scans, no disk. */
class PreviewDeletionPlanServiceTest {

    private final PreviewDeletionPlanService service = new PreviewDeletionPlanService();

    // ---- builders ----

    private static Path p(String path) {
        return Path.of(path);
    }

    private static PhysicalCopy copy(long size, String... paths) {
        return new PhysicalCopy(Arrays.stream(paths).map(s -> new FileEntry(p(s), size, Instant.EPOCH)).toList());
    }

    private static DuplicateSet set(long size, String digest, PhysicalCopy... copies) {
        List<FileEntry> files = new ArrayList<>();
        for (PhysicalCopy c : copies) {
            files.addAll(c.links());
        }
        files.sort((a, b) -> a.path().compareTo(b.path()));
        return new DuplicateSet(size, digest, files, List.of(copies));
    }

    /** A set of single-link copies, one per path. */
    private static DuplicateSet plain(long size, String digest, String... paths) {
        return set(size, digest, Arrays.stream(paths).map(s -> copy(size, s)).toArray(PhysicalCopy[]::new));
    }

    private static ScanResult scan(List<FolderTotal> folders, DuplicateSet... sets) {
        return new ScanResult(List.of(), folders, List.of(sets), List.of(), 0);
    }

    private static ScanResult scan(DuplicateSet... sets) {
        return scan(List.of(), sets);
    }

    private static KeepAndQuarantine rule(String keep, String quarantineIn) {
        return new KeepAndQuarantine(p(keep), p(quarantineIn));
    }

    private static PlanRequest rules(KeepAndQuarantine... rules) {
        return new PlanRequest(List.of(rules), List.of(), List.of());
    }

    private static PlanRequest picks(KeepCopy... picks) {
        return new PlanRequest(List.of(), List.of(), List.of(picks));
    }

    private static Source.FromRule from(int index, KeepAndQuarantine rule) {
        return new Source.FromRule(index, rule);
    }

    private static Operation.Quarantine op(String digest, long size, List<String> links, String keeper, Source source) {
        return new Operation.Quarantine(digest, size, links.stream().map(PreviewDeletionPlanServiceTest::p).toList(),
                p(keeper), source);
    }

    private DeletionPlan plan(ScanResult scan, PlanRequest request) {
        Result<DeletionPlan> result = service.preview(scan, request);
        return assertInstanceOf(Result.Success.class, result, String.valueOf(result)) instanceof Result.Success<?> s
                ? (DeletionPlan) s.value() : null;
    }

    private Result.Failure<DeletionPlan> failure(ScanResult scan, PlanRequest request) {
        Result<DeletionPlan> result = service.preview(scan, request);
        @SuppressWarnings("unchecked")
        Result.Failure<DeletionPlan> failure = assertInstanceOf(Result.Failure.class, result, String.valueOf(result));
        return failure;
    }

    // ---- AC-1 ----

    @Test
    @DisplayName("DFS-022.AC-1 The same request on the same scan gives the same 64-character hex plan id")
    void sameRequestSameId() {
        ScanResult scan = scan(plain(10, "d1", "C:/A/x.jpg", "C:/B/x.jpg"));
        PlanRequest request = rules(rule("C:/A", "C:/B"));
        DeletionPlan first = plan(scan, request);
        DeletionPlan second = plan(scan, request);
        assertTrue(first.id().matches("[0-9a-f]{64}"), first.id());
        assertEquals(first.id(), second.id());
    }

    @Test
    @DisplayName("DFS-022.AC-1 A request that changes the plan's entries changes the plan id")
    void differentEntriesDifferentId() {
        ScanResult scan = scan(plain(10, "d1", "C:/A/x.jpg", "C:/B/x.jpg"));
        DeletionPlan keepA = plan(scan, rules(rule("C:/A", "C:/B")));
        DeletionPlan keepB = plan(scan, rules(rule("C:/B", "C:/A")));
        assertNotEquals(keepA.id(), keepB.id());
    }

    // ---- AC-2 ----

    @Test
    @DisplayName("DFS-022.AC-2 Preview reads only the scan result and leaves the disk alone")
    void previewTouchesNoDisk(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("bystander.txt"), "x");
        List<String> before = listing(tmp);
        ScanResult scan = scan(plain(10, "d1", "C:/A/x.jpg", "C:/B/x.jpg"));
        DeletionPlan plan = plan(scan, rules(rule("C:/A", "C:/B")));
        assertEquals(1, plan.operations().size());
        assertEquals(before, listing(tmp));
    }

    private static List<String> listing(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.map(Path::toString).sorted().toList();
        }
    }

    // ---- AC-3 ----

    @Test
    @DisplayName("DFS-022.AC-3 A rule quarantines the copies under B that have a copy under A")
    void ruleQuarantinesCopiesUnderB() {
        ScanResult scan = scan(
                plain(10, "d1", "C:/A/x.jpg", "C:/B/x.jpg", "C:/B/sub/x2.jpg"),
                plain(20, "d2", "C:/B/y.jpg", "C:/C/y.jpg"));
        KeepAndQuarantine r1 = rule("C:/A", "C:/B");
        DeletionPlan plan = plan(scan, rules(r1));
        assertEquals(List.of(
                op("d1", 10, List.of("C:/B/sub/x2.jpg"), "C:/A/x.jpg", from(0, r1)),
                op("d1", 10, List.of("C:/B/x.jpg"), "C:/A/x.jpg", from(0, r1))), plan.operations());
        assertTrue(plan.conflicts().isEmpty());
    }

    @Test
    @DisplayName("DFS-022.AC-3 A rule folder covers whole path components only")
    void ruleCoversWholeComponentsOnly() {
        ScanResult scan = scan(plain(10, "d1", "C:/A/x.jpg", "C:/AB/x.jpg"));
        KeepAndQuarantine keepA = rule("C:/A", "C:/AB");
        assertEquals(List.of(op("d1", 10, List.of("C:/AB/x.jpg"), "C:/A/x.jpg", from(0, keepA))),
                plan(scan, rules(keepA)).operations());
        KeepAndQuarantine keepAB = rule("C:/AB", "C:/A");
        assertEquals(List.of(op("d1", 10, List.of("C:/A/x.jpg"), "C:/AB/x.jpg", from(0, keepAB))),
                plan(scan, rules(keepAB)).operations());
    }

    // ---- AC-4 ----

    @Test
    @DisplayName("DFS-022.AC-4 An exception folder keeps a file a rule would quarantine")
    void exceptionFolderKeepsFile() {
        ScanResult scan = scan(plain(10, "d1", "C:/A/x.jpg", "C:/B/x.jpg", "C:/B/old/x.jpg"));
        KeepAndQuarantine r = rule("C:/A", "C:/B");
        PlanRequest request = new PlanRequest(List.of(r), List.of(p("C:/B/old")), List.of());
        assertEquals(List.of(op("d1", 10, List.of("C:/B/x.jpg"), "C:/A/x.jpg", from(0, r))),
                plan(scan, request).operations());
    }

    @Test
    @DisplayName("DFS-022.AC-4 An exception naming the file itself")
    void exceptionNamingTheFile() {
        ScanResult scan = scan(plain(10, "d1", "C:/A/x.jpg", "C:/B/x.jpg"));
        PlanRequest request = new PlanRequest(List.of(rule("C:/A", "C:/B")), List.of(p("C:/B/x.jpg")), List.of());
        assertTrue(plan(scan, request).operations().isEmpty());
    }

    // ---- AC-5 ----

    @Test
    @DisplayName("DFS-022.AC-5 A pick overrides the rules and exceptions for its set")
    void pickOverridesRules() {
        ScanResult scan = scan(plain(10, "d1", "C:/A/x.jpg", "C:/B/x.jpg", "D:/x.jpg"));
        KeepCopy pick = new KeepCopy("d1", p("D:/x.jpg"));
        PlanRequest request = new PlanRequest(List.of(rule("C:/A", "C:/B")), List.of(p("C:/B/x.jpg")), List.of(pick));
        DeletionPlan plan = plan(scan, request);
        Source source = new Source.FromPick(pick);
        assertEquals(List.of(
                op("d1", 10, List.of("C:/A/x.jpg"), "D:/x.jpg", source),
                op("d1", 10, List.of("C:/B/x.jpg"), "D:/x.jpg", source)), plan.operations());
        assertTrue(plan.conflicts().isEmpty());
    }

    // ---- AC-6 ----

    @Test
    @DisplayName("DFS-022.AC-6 Two rules in opposite directions conflict instead of removing both copies")
    void oppositeRulesConflict() {
        ScanResult scan = scan(
                plain(10, "d1", "C:/A/x.jpg", "C:/B/x.jpg"),
                plain(20, "d2", "C:/A/y.jpg", "C:/C/y.jpg"));
        KeepAndQuarantine r1 = rule("C:/A", "C:/B");
        KeepAndQuarantine r2 = rule("C:/B", "C:/A");
        KeepAndQuarantine r3 = rule("C:/A", "C:/C");
        DeletionPlan plan = plan(scan, rules(r1, r2, r3));
        assertEquals(List.of(op("d2", 20, List.of("C:/C/y.jpg"), "C:/A/y.jpg", from(2, r3))), plan.operations());
        assertEquals(List.of(new Conflict("d1", List.of(from(0, r1), from(1, r2)))), plan.conflicts());
    }

    @Test
    @DisplayName("DFS-022.AC-6 A keeper quarantined by another rule is a conflict even if a third copy survives")
    void quarantinedKeeperConflictsDespiteThirdCopy() {
        ScanResult scan = scan(plain(10, "d1", "C:/A/x.jpg", "C:/B/x.jpg", "D:/x.jpg"));
        KeepAndQuarantine r1 = rule("C:/A", "C:/B");
        KeepAndQuarantine r2 = rule("D:/", "C:/A");
        DeletionPlan plan = plan(scan, rules(r1, r2));
        assertTrue(plan.operations().isEmpty());
        assertEquals(List.of(new Conflict("d1", List.of(from(0, r1), from(1, r2)))), plan.conflicts());
    }

    // ---- AC-7 ----

    @Test
    @DisplayName("DFS-022.AC-7 Two rules quarantining the same copy give one entry, from the earliest rule")
    void sameCopyOneEntry() {
        ScanResult scan = scan(plain(10, "d1", "C:/A/x.jpg", "D:/A/x.jpg", "C:/B/x.jpg"));
        KeepAndQuarantine r1 = rule("C:/A", "C:/B");
        KeepAndQuarantine r2 = rule("D:/A", "C:/B");
        DeletionPlan plan = plan(scan, rules(r1, r2));
        assertEquals(List.of(op("d1", 10, List.of("C:/B/x.jpg"), "C:/A/x.jpg", from(0, r1))), plan.operations());
        assertTrue(plan.conflicts().isEmpty());
    }

    // ---- AC-8 ----

    @Test
    @DisplayName("DFS-022.AC-8 Hard links of one copy are quarantined together and counted once")
    void hardLinksTogether() {
        ScanResult scan = scan(set(5000, "d1", copy(5000, "C:/A/x.jpg"),
                copy(5000, "C:/B/x.jpg", "C:/B/sub/x-link.jpg")));
        KeepAndQuarantine r = rule("C:/A", "C:/B");
        DeletionPlan plan = plan(scan, rules(r));
        assertEquals(List.of(op("d1", 5000, List.of("C:/B/sub/x-link.jpg", "C:/B/x.jpg"), "C:/A/x.jpg", from(0, r))),
                plan.operations());
        assertEquals(5000, plan.totals().bytes());
    }

    @Test
    @DisplayName("DFS-022.AC-8 A copy only partly inside the quarantine folder is left alone")
    void partlyInsideCopyLeftAlone() {
        ScanResult scan = scan(set(10, "d1", copy(10, "C:/A/x.jpg"), copy(10, "C:/B/x.jpg", "C:/Other/x.jpg")));
        assertTrue(plan(scan, rules(rule("C:/A", "C:/B"))).operations().isEmpty());
    }

    // ---- AC-9 ----

    @Test
    @DisplayName("DFS-022.AC-9 The keeper is the first qualifying copy by path")
    void keeperIsFirstByPath() {
        ScanResult scan = scan(plain(10, "d1", "C:/A/z.jpg", "C:/A/sub/a.jpg", "C:/B/x.jpg"));
        KeepAndQuarantine r = rule("C:/A", "C:/B");
        assertEquals(List.of(op("d1", 10, List.of("C:/B/x.jpg"), "C:/A/sub/a.jpg", from(0, r))),
                plan(scan, rules(r)).operations());
    }

    // ---- AC-10 ----

    @Test
    @DisplayName("DFS-022.AC-10 Operations are ordered by source then first link and the totals add up")
    void orderingAndTotals() {
        ScanResult scan = scan(
                plain(100, "d1", "C:/A/x.jpg", "C:/B/x.jpg", "C:/B/y/x.jpg"),
                plain(40, "d2", "D:/k.txt", "C:/C/k.txt"));
        KeepAndQuarantine r0 = rule("C:/Z", "C:/C");
        KeepAndQuarantine r1 = rule("C:/A", "C:/B");
        KeepCopy pick = new KeepCopy("d2", p("D:/k.txt"));
        DeletionPlan plan = plan(scan, new PlanRequest(List.of(r0, r1), List.of(), List.of(pick)));
        assertEquals(List.of(
                op("d1", 100, List.of("C:/B/x.jpg"), "C:/A/x.jpg", from(1, r1)),
                op("d1", 100, List.of("C:/B/y/x.jpg"), "C:/A/x.jpg", from(1, r1)),
                op("d2", 40, List.of("C:/C/k.txt"), "D:/k.txt", new Source.FromPick(pick))), plan.operations());
        assertEquals(new PlanTotals(3, 240, 2), plan.totals());
    }

    // ---- AC-11 ----

    private static final List<FolderTotal> TOTALS = List.of(
            new FolderTotal(p("C:/A"), 1, 100),
            new FolderTotal(p("C:/B"), 3, 300),
            new FolderTotal(p("C:/B/sub"), 2, 50));

    @Test
    @DisplayName("DFS-022.AC-11 Left behind counts what stays in the quarantine folder")
    void leftBehindCounts() {
        ScanResult scan = scan(TOTALS, plain(100, "d1", "C:/A/x.jpg", "C:/B/x.jpg"));
        DeletionPlan plan = plan(scan, rules(rule("C:/A", "C:/B")));
        assertEquals(List.of(new LeftBehind(0, p("C:/B"), 4, 250)), plan.leftBehind());
    }

    @Test
    @DisplayName("DFS-022.AC-11 A rule folder without scanned files leaves nothing behind")
    void leftBehindEmptyFolder() {
        ScanResult scan = scan(TOTALS, plain(100, "d1", "C:/A/x.jpg", "C:/B/x.jpg"));
        DeletionPlan plan = plan(scan, rules(rule("C:/A", "C:/B"), rule("C:/A", "C:/Empty")));
        assertEquals(List.of(new LeftBehind(0, p("C:/B"), 4, 250), new LeftBehind(1, p("C:/Empty"), 0, 0)),
                plan.leftBehind());
    }

    // ---- AC-12 ----

    @Test
    @DisplayName("DFS-022.AC-12 An empty request gives an empty plan with a stable id")
    void emptyRequest() {
        ScanResult scan = scan(plain(10, "d1", "C:/A/x.jpg", "C:/B/x.jpg"));
        PlanRequest empty = new PlanRequest(List.of(), List.of(), List.of());
        DeletionPlan first = plan(scan, empty);
        DeletionPlan second = plan(scan, empty);
        assertTrue(first.operations().isEmpty());
        assertTrue(first.conflicts().isEmpty());
        assertTrue(first.leftBehind().isEmpty());
        assertEquals(new PlanTotals(0, 0, 0), first.totals());
        assertEquals(first.id(), second.id());
    }

    @Test
    @DisplayName("DFS-022.AC-12 A null scan is a caller bug")
    void nullScan() {
        try {
            service.preview(null, new PlanRequest(List.of(), List.of(), List.of()));
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
    }

    @Test
    @DisplayName("DFS-022.AC-12 A null request is a caller bug")
    void nullRequest() {
        try {
            service.preview(scan(), null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
    }

    @Test
    @DisplayName("DFS-022.AC-12 A null element in the rule list is a caller bug")
    void nullRuleElement() {
        try {
            service.preview(scan(), new PlanRequest(Arrays.asList((KeepAndQuarantine) null), List.of(), List.of()));
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
    }

    @Test
    @DisplayName("DFS-022.AC-12 A null list in the request is a caller bug")
    void nullList() {
        try {
            service.preview(scan(), new PlanRequest(null, List.of(), List.of()));
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
    }

    // ---- AC-13 ----

    @Test
    @DisplayName("DFS-022.AC-13 A rule that could never quarantine anything is refused")
    void invalidRule() {
        ScanResult scan = scan(plain(10, "d1", "C:/A/x.jpg", "C:/B/x.jpg"));
        Result.Failure<DeletionPlan> same = failure(scan, rules(rule("C:/A", "C:/A")));
        assertEquals(ErrorCode.INVALID_RULE, same.code());
        assertTrue(same.message().contains("rule 0"), same.message());
        assertEquals(ErrorCode.INVALID_RULE, failure(scan, rules(rule("C:/A", "C:/A/sub"))).code());
    }

    @Test
    @DisplayName("DFS-022.AC-13 Keeping a subfolder of the quarantine folder is valid")
    void keepUnderQuarantineIsValid() {
        ScanResult scan = scan(plain(10, "d1", "C:/B/best/x.jpg", "C:/B/x.jpg"));
        KeepAndQuarantine r = rule("C:/B/best", "C:/B");
        assertEquals(List.of(op("d1", 10, List.of("C:/B/x.jpg"), "C:/B/best/x.jpg", from(0, r))),
                plan(scan, rules(r)).operations());
    }

    @Test
    @DisplayName("DFS-022.AC-13 Picks that do not match the scan are refused")
    void invalidPicks() {
        ScanResult scan = scan(plain(10, "d1", "C:/A/x.jpg", "C:/B/x.jpg"));
        Result.Failure<DeletionPlan> unknown = failure(scan, picks(new KeepCopy("d9", p("C:/A/x.jpg"))));
        assertEquals(ErrorCode.UNKNOWN_SET, unknown.code());
        assertTrue(unknown.message().contains("pick d9"), unknown.message());
        assertEquals(ErrorCode.PATH_NOT_IN_SET, failure(scan, picks(new KeepCopy("d1", p("C:/C/x.jpg")))).code());
        assertEquals(ErrorCode.DUPLICATE_PICK, failure(scan,
                picks(new KeepCopy("d1", p("C:/A/x.jpg")), new KeepCopy("d1", p("C:/B/x.jpg")))).code());
    }

    @Test
    @DisplayName("DFS-022.AC-13 The first problem in request order is reported")
    void firstProblemWins() {
        ScanResult scan = scan(plain(10, "d1", "C:/A/x.jpg", "C:/B/x.jpg"));
        PlanRequest request = new PlanRequest(List.of(rule("C:/A", "C:/B"), rule("C:/C", "C:/C")), List.of(),
                List.of(new KeepCopy("d9", p("C:/A/x.jpg"))));
        Result.Failure<DeletionPlan> failure = failure(scan, request);
        assertEquals(ErrorCode.INVALID_RULE, failure.code());
        assertTrue(failure.message().contains("rule 1"), failure.message());
    }
}
