package us.pochaev.dfs.scanner.infra.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.pochaev.dfs.scanner.application.CheckPlanDriftService;
import us.pochaev.dfs.scanner.domain.factory.ScannerFactories;
import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.DriftKind;
import us.pochaev.dfs.scanner.domain.model.DriftReport;
import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.ErrorCode;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.KeepAndQuarantine;
import us.pochaev.dfs.scanner.domain.model.PathDrift;
import us.pochaev.dfs.scanner.domain.model.PathDrift.Role;
import us.pochaev.dfs.scanner.domain.model.PlanRequest;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.usecases.query.CheckPlanDrift;
import us.pochaev.dfs.scanner.infra.port.FileAttributesReader;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Plan drift check (DFS-033) over real files in a temp folder. */
class CheckPlanDriftTest {

    @TempDir
    Path t;

    private static CheckPlanDrift checker() {
        return new CheckPlanDriftService(new NioFileAttributesReader());
    }

    private static BasicFileAttributes attrs(Path p) throws IOException {
        return Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static void write(Path p, int size) throws IOException {
        Files.createDirectories(p.getParent());
        byte[] bytes = new byte[size];
        java.util.Arrays.fill(bytes, (byte) size);
        Files.write(p, bytes);
    }

    private static FileEntry entry(Path p) throws IOException {
        BasicFileAttributes a = attrs(p);
        return new FileEntry(p, a.size(), a.lastModifiedTime().toInstant());
    }

    /** A set from the same-named file under each folder, in the folder order given (ascending). */
    private static DuplicateSet set(Path root, String digest, String name, String... folders) throws IOException {
        List<FileEntry> entries = new ArrayList<>();
        for (String folder : folders) {
            entries.add(entry(root.resolve(folder).resolve(name)));
        }
        return new DuplicateSet(entries.get(0).size(), digest, entries);
    }

    private static ScanResult scan(Path root, DuplicateSet... sets) {
        return new ScanResult(List.of(root), List.of(), List.of(sets), List.of(), 0);
    }

    /** A/x.jpg, B/x.jpg (5 bytes, d1) and A/y.jpg, B/y.jpg (7 bytes, d2). */
    private static ScanResult basicScan(Path root) throws IOException {
        write(root.resolve("A/x.jpg"), 5);
        write(root.resolve("B/x.jpg"), 5);
        write(root.resolve("A/y.jpg"), 7);
        write(root.resolve("B/y.jpg"), 7);
        return scan(root, set(root, "d1", "x.jpg", "A", "B"), set(root, "d2", "y.jpg", "A", "B"));
    }

    @SuppressWarnings("unchecked")
    private static DeletionPlan plan(ScanResult scan, KeepAndQuarantine... rules) {
        return ((Result.Success<DeletionPlan>) ScannerFactories.previewDeletionPlan()
                .preview(scan, new PlanRequest(List.of(rules), List.of(), List.of()))).value();
    }

    private static KeepAndQuarantine rule(Path root, String keep, String delete) {
        return new KeepAndQuarantine(root.resolve(keep), root.resolve(delete));
    }

    @SuppressWarnings("unchecked")
    private static DriftReport report(Result<DriftReport> result) {
        return ((Result.Success<DriftReport>) assertInstanceOf(Result.Success.class, result)).value();
    }

    private static PathDrift drift(Path p, Role role, String digest, DriftKind kind) {
        return new PathDrift(p, role, digest, kind, "");
    }

    @Test
    @DisplayName("DFS-033.AC-1 An unchanged plan has no drift")
    void unchangedPlanHasNoDrift() throws IOException {
        ScanResult scan = basicScan(t);
        DeletionPlan plan = plan(scan, rule(t, "A", "B"));

        DriftReport report = report(checker().check(scan, plan));

        assertEquals(plan.id(), report.planId());
        assertEquals(4, report.checked());
        assertEquals(List.of(), report.drifts());
    }

    @Test
    @DisplayName("DFS-033.AC-2 A deleted candidate and a deleted keeper are both reported")
    void deletedCandidateAndKeeperAreReported() throws IOException {
        ScanResult scan = basicScan(t);
        DeletionPlan plan = plan(scan, rule(t, "A", "B"));
        Files.delete(t.resolve("B/x.jpg"));
        Files.delete(t.resolve("A/y.jpg"));

        DriftReport report = report(checker().check(scan, plan));

        assertEquals(4, report.checked());
        assertEquals(List.of(
                drift(t.resolve("B/x.jpg"), Role.CANDIDATE, "d1", DriftKind.MISSING),
                drift(t.resolve("A/y.jpg"), Role.KEEPER, "d2", DriftKind.MISSING)), report.drifts());
    }

    @Test
    @DisplayName("DFS-033.AC-3 Size and time changes are told apart")
    void sizeAndTimeChangesAreToldApart() throws IOException {
        write(t.resolve("A/x.jpg"), 5);
        write(t.resolve("B/x.jpg"), 5);
        write(t.resolve("A/y.jpg"), 7);
        write(t.resolve("B/y.jpg"), 7);
        write(t.resolve("A/z.jpg"), 9);
        write(t.resolve("B/z.jpg"), 9);
        ScanResult scan = scan(t, set(t, "d1", "x.jpg", "A", "B"), set(t, "d2", "y.jpg", "A", "B"),
                set(t, "d3", "z.jpg", "A", "B"));
        DeletionPlan plan = plan(scan, rule(t, "A", "B"));

        Path x = t.resolve("B/x.jpg");
        Instant recordedX = attrs(x).lastModifiedTime().toInstant();
        Files.write(x, new byte[]{1}, StandardOpenOption.APPEND);
        Files.setLastModifiedTime(x, FileTime.from(recordedX));

        Path y = t.resolve("B/y.jpg");
        Files.setLastModifiedTime(y, FileTime.from(attrs(y).lastModifiedTime().toInstant().plusSeconds(10)));

        Path z = t.resolve("B/z.jpg");
        Instant recordedZ = attrs(z).lastModifiedTime().toInstant();
        Files.write(z, new byte[]{1}, StandardOpenOption.APPEND);
        Files.setLastModifiedTime(z, FileTime.from(recordedZ.plusSeconds(10)));

        DriftReport report = report(checker().check(scan, plan));

        Map<Path, DriftKind> kinds = new TreeMap<>();
        report.drifts().forEach(d -> kinds.put(d.path(), d.kind()));
        assertEquals(Map.of(x, DriftKind.SIZE_CHANGED, y, DriftKind.MODIFIED_CHANGED, z, DriftKind.SIZE_CHANGED),
                kinds);
        assertEquals(3, report.drifts().size());
        assertEquals(6, report.checked());
    }

    @Test
    @DisplayName("DFS-033.AC-4 A file replaced by a symlink is not followed")
    void symlinkIsNotFollowed() throws IOException {
        ScanResult scan = basicScan(t);
        DeletionPlan plan = plan(scan, rule(t, "A", "B"));
        Path x = t.resolve("B/x.jpg");
        Instant recorded = attrs(x).lastModifiedTime().toInstant();
        Files.delete(x);
        Path target = t.resolve("outside/target.bin");
        Files.createDirectories(target.getParent());
        Files.write(target, new byte[]{9, 9, 9, 9, 9});
        Files.setLastModifiedTime(target, FileTime.from(recorded));
        Fixtures.createSymlinkOrSkip(x, target);

        DriftReport report = report(checker().check(scan, plan));

        assertEquals(List.of(drift(x, Role.CANDIDATE, "d1", DriftKind.NOT_A_REGULAR_FILE)), report.drifts());
    }

    @Test
    @DisplayName("DFS-033.AC-4 A file replaced by a folder is not a regular file")
    void folderIsNotARegularFile() throws IOException {
        ScanResult scan = basicScan(t);
        DeletionPlan plan = plan(scan, rule(t, "A", "B"));
        Path y = t.resolve("B/y.jpg");
        Files.delete(y);
        Files.createDirectory(y);

        DriftReport report = report(checker().check(scan, plan));

        assertEquals(List.of(drift(y, Role.CANDIDATE, "d2", DriftKind.NOT_A_REGULAR_FILE)), report.drifts());
    }

    @Test
    @DisplayName("DFS-033.AC-4 On Windows a candidate replaced by a junction is not a regular file")
    void junctionIsNotARegularFile() throws IOException {
        assumeTrue(Fixtures.isWindows(), "junctions exist on Windows only");
        ScanResult scan = basicScan(t);
        DeletionPlan plan = plan(scan, rule(t, "A", "B"));
        Path x = t.resolve("B/x.jpg");
        Path folder = t.resolve("somefolder");
        Files.createDirectory(folder);
        Files.delete(x);
        Fixtures.createJunction(x, folder);

        DriftReport report = report(checker().check(scan, plan));

        assertEquals(List.of(drift(x, Role.CANDIDATE, "d1", DriftKind.NOT_A_REGULAR_FILE)), report.drifts());
    }

    @Test
    @DisplayName("DFS-033.AC-5 An unreadable path does not stop the check")
    void unreadablePathDoesNotStopTheCheck() throws IOException {
        ScanResult scan = basicScan(t);
        DeletionPlan plan = plan(scan, rule(t, "A", "B"));
        Path denied = t.resolve("B/x.jpg");
        List<Path> read = new ArrayList<>();
        FileAttributesReader fake = p -> {
            if (p.equals(denied)) {
                throw new AccessDeniedException(p.toString(), null, "denied");
            }
            read.add(p);
            return attrs(p);
        };

        DriftReport report = report(new CheckPlanDriftService(fake).check(scan, plan));

        assertEquals(4, report.checked());
        assertEquals(1, report.drifts().size());
        PathDrift d = report.drifts().get(0);
        assertEquals(denied, d.path());
        assertEquals(Role.CANDIDATE, d.role());
        assertEquals("d1", d.digest());
        assertEquals(DriftKind.UNREADABLE, d.kind());
        assertFalse(d.detail().isBlank());
        assertEquals(List.of(t.resolve("A/x.jpg"), t.resolve("A/y.jpg"), t.resolve("B/y.jpg")),
                read.stream().sorted().toList());
    }

    @Test
    @DisplayName("DFS-033.AC-6 A shared keeper is reported once, in plan order")
    void sharedKeeperIsReportedOnce() throws IOException {
        write(t.resolve("K/x.jpg"), 5);
        write(t.resolve("B/x.jpg"), 5);
        write(t.resolve("C/x.jpg"), 5);
        ScanResult scan = scan(t, set(t, "d1", "x.jpg", "B", "C", "K"));
        DeletionPlan plan = plan(scan, rule(t, "K", "B"), rule(t, "K", "C"));
        Files.delete(t.resolve("K/x.jpg"));

        DriftReport report = report(checker().check(scan, plan));

        assertEquals(3, report.checked());
        assertEquals(List.of(drift(t.resolve("K/x.jpg"), Role.KEEPER, "d1", DriftKind.MISSING)), report.drifts());
    }

    @Test
    @DisplayName("DFS-033.AC-6 Drifts follow plan order")
    void driftsFollowPlanOrder() throws IOException {
        ScanResult scan = basicScan(t);
        DeletionPlan plan = plan(scan, rule(t, "A", "B"));
        Files.delete(t.resolve("B/y.jpg"));
        Files.delete(t.resolve("B/x.jpg"));

        DriftReport report = report(checker().check(scan, plan));

        assertEquals(List.of(
                drift(t.resolve("B/x.jpg"), Role.CANDIDATE, "d1", DriftKind.MISSING),
                drift(t.resolve("B/y.jpg"), Role.CANDIDATE, "d2", DriftKind.MISSING)), report.drifts());
    }

    @Test
    @DisplayName("DFS-033.AC-7 A plan from another scan is refused without reading the disk")
    void planFromAnotherScanIsRefused(@TempDir Path u) throws IOException {
        ScanResult scanT = basicScan(t);
        DeletionPlan plan = plan(scanT, rule(t, "A", "B"));
        ScanResult scanU = basicScan(u);
        List<Path> calls = new ArrayList<>();
        FileAttributesReader recorder = p -> {
            calls.add(p);
            return attrs(p);
        };

        Result<DriftReport> result = new CheckPlanDriftService(recorder).check(scanU, plan);

        Result.Failure<DriftReport> failure = assertInstanceOf(Result.Failure.class, result);
        assertEquals(ErrorCode.PLAN_NOT_FROM_SCAN, failure.code());
        assertTrue(failure.message().contains(t.resolve("B").resolve("x.jpg").toString()), failure.message());
        assertEquals(List.of(), calls);
    }

    @Test
    @DisplayName("DFS-033.AC-7 A digest that is not in the scan is refused without reading the disk")
    void unknownDigestIsRefused() throws IOException {
        ScanResult scan = basicScan(t);
        DeletionPlan plan = plan(scan, rule(t, "A", "B"));
        ScanResult other = scan(t, set(t, "other", "x.jpg", "A", "B"));
        List<Path> calls = new ArrayList<>();
        FileAttributesReader recorder = p -> {
            calls.add(p);
            return attrs(p);
        };

        Result<DriftReport> result = new CheckPlanDriftService(recorder).check(other, plan);

        Result.Failure<DriftReport> failure = assertInstanceOf(Result.Failure.class, result);
        assertEquals(ErrorCode.PLAN_NOT_FROM_SCAN, failure.code());
        assertTrue(failure.message().contains("d1") || failure.message().contains("d2"), failure.message());
        assertEquals(List.of(), calls);
    }

    @Test
    @DisplayName("DFS-033.AC-7 An empty plan has nothing to check and null inputs are rejected")
    void emptyPlanAndNulls() throws IOException {
        ScanResult scan = basicScan(t);
        DeletionPlan empty = plan(scan);
        CheckPlanDrift checker = checker();

        DriftReport report = report(checker.check(scan, empty));
        assertEquals(0, report.checked());
        assertEquals(List.of(), report.drifts());

        try {
            checker.check(null, empty);
            fail("expected NullPointerException for a null scan");
        } catch (NullPointerException expected) {
            // expected
        }
        try {
            checker.check(scan, null);
            fail("expected NullPointerException for a null plan");
        } catch (NullPointerException expected) {
            // expected
        }
    }

    private static Map<String, String> snapshot(Path root) throws IOException {
        Map<String, String> out = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                BasicFileAttributes a = attrs(p);
                out.put(root.relativize(p).toString(), a.size() + "@" + a.lastModifiedTime().toInstant());
            }
        }
        return out;
    }

    @Test
    @DisplayName("DFS-033.AC-8 Checking changes nothing on disk")
    void checkingChangesNothing() throws IOException {
        ScanResult scan = basicScan(t);
        DeletionPlan plan = plan(scan, rule(t, "A", "B"));
        Map<String, String> before = snapshot(t);

        report(checker().check(scan, plan));

        assertEquals(before, snapshot(t));
    }

    @Test
    @DisplayName("DFS-033.AC-9 The factory returns a working CheckPlanDrift")
    void factoryReturnsWorkingCheck() throws IOException {
        ScanResult scan = basicScan(t);
        DeletionPlan plan = plan(scan, rule(t, "A", "B"));

        DriftReport report = report(ScannerFactories.checkPlanDrift().check(scan, plan));

        assertEquals(4, report.checked());
        assertEquals(List.of(), report.drifts());
    }
}
