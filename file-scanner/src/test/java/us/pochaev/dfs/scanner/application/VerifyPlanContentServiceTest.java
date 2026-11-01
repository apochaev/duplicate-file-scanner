package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.pochaev.dfs.scanner.domain.factory.ScannerFactories;
import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.KeepAndQuarantine;
import us.pochaev.dfs.scanner.domain.model.OperationCheck;
import us.pochaev.dfs.scanner.domain.model.OperationCheck.Outcome;
import us.pochaev.dfs.scanner.domain.model.PlanRequest;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.model.VerifyEvent;
import us.pochaev.dfs.scanner.domain.model.VerifyListener;
import us.pochaev.dfs.scanner.domain.model.VerifyReport;
import us.pochaev.dfs.scanner.domain.usecases.query.VerifyPlanContent;
import us.pochaev.dfs.scanner.infra.adapter.NioContentComparator;
import us.pochaev.dfs.scanner.infra.adapter.NioFileAttributesReader;
import us.pochaev.dfs.scanner.infra.port.ContentComparator;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Plan content verify (DFS-034) over real files in a temp folder. */
class VerifyPlanContentServiceTest {

    private static final int MIB3 = 3 * 1024 * 1024;
    private static final long TOTAL = MIB3 + 5;

    @TempDir
    Path t;

    private static VerifyPlanContent verifier() {
        return new VerifyPlanContentService(new NioFileAttributesReader(), new NioContentComparator());
    }

    private static byte[] bytes(int size) {
        byte[] b = new byte[size];
        for (int i = 0; i < size; i++) {
            b[i] = (byte) (i * 31 + 7);
        }
        return b;
    }

    private static void write(Path p, byte[] content) throws IOException {
        Files.createDirectories(p.getParent());
        Files.write(p, content);
    }

    private static FileEntry entry(Path p) throws IOException {
        BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return new FileEntry(p, a.size(), a.lastModifiedTime().toInstant());
    }

    private static DuplicateSet set(Path root, String digest, String name) throws IOException {
        return new DuplicateSet(Files.size(root.resolve("A").resolve(name)), digest,
                List.of(entry(root.resolve("A").resolve(name)), entry(root.resolve("B").resolve(name))));
    }

    @SuppressWarnings("unchecked")
    private static DeletionPlan plan(Path root, PlanRequest request, DuplicateSet... sets) {
        ScanResult scan = new ScanResult(List.of(root), List.of(), List.of(sets), List.of(), 0);
        return ((Result.Success<DeletionPlan>) ScannerFactories.previewDeletionPlan().preview(scan, request)).value();
    }

    private static PlanRequest keepAB(Path root) {
        return new PlanRequest(List.of(new KeepAndQuarantine(root.resolve("A"), root.resolve("B"))), List.of(),
                List.of());
    }

    /** A/x.jpg, B/x.jpg (5 bytes) and A/y.bin, B/y.bin (3 MiB), identical. */
    private DeletionPlan firstPlan() throws IOException {
        write(t.resolve("A/x.jpg"), bytes(5));
        write(t.resolve("B/x.jpg"), bytes(5));
        write(t.resolve("A/y.bin"), bytes(MIB3));
        write(t.resolve("B/y.bin"), bytes(MIB3));
        return plan(t, keepAB(t), set(t, "d1", "x.jpg"), set(t, "d2", "y.bin"));
    }

    private static final VerifyListener NOOP = e -> { };
    private static final BooleanSupplier NEVER = () -> false;

    @SuppressWarnings("unchecked")
    private static VerifyReport report(Result<VerifyReport> result) {
        return ((Result.Success<VerifyReport>) assertInstanceOf(Result.Success.class, result)).value();
    }

    private static OperationCheck check(Path candidate, Path keeper, Outcome outcome, String detail, String digest) {
        return new OperationCheck(digest, candidate, keeper, outcome, detail);
    }

    @Test
    @DisplayName("DFS-034.AC-1 Identical copies verify as identical")
    void identicalCopiesVerifyAsIdentical() throws IOException {
        DeletionPlan plan = firstPlan();

        VerifyReport report = report(verifier().verify(plan, NOOP, NEVER));

        assertEquals(plan.id(), report.planId());
        assertFalse(report.cancelled());
        assertEquals(List.of(
                check(t.resolve("B/x.jpg"), t.resolve("A/x.jpg"), Outcome.IDENTICAL, "", "d1"),
                check(t.resolve("B/y.bin"), t.resolve("A/y.bin"), Outcome.IDENTICAL, "", "d2")), report.checks());
    }

    @Test
    @DisplayName("DFS-034.AC-2 A difference is found and located, a size change is told apart")
    void differenceIsFoundAndLocated() throws IOException {
        DeletionPlan plan = firstPlan();
        byte[] changed = bytes(MIB3);
        changed[MIB3 - 1] ^= 0x55;
        Files.write(t.resolve("B/y.bin"), changed);
        Files.write(t.resolve("B/x.jpg"), bytes(6));

        VerifyReport report = report(verifier().verify(plan, NOOP, NEVER));

        assertEquals(List.of(
                check(t.resolve("B/x.jpg"), t.resolve("A/x.jpg"), Outcome.DIFFERS, "sizes differ: 6 vs 5", "d1"),
                check(t.resolve("B/y.bin"), t.resolve("A/y.bin"), Outcome.DIFFERS,
                        "first difference at byte 3145727", "d2")), report.checks());
    }

    @Test
    @DisplayName("DFS-034.AC-3 Missing files are reported and the rest still checked")
    void missingFilesAreReportedAndRestChecked() throws IOException {
        for (String n : List.of("x.jpg", "y.jpg", "z.jpg")) {
            write(t.resolve("A/" + n), bytes(5));
            write(t.resolve("B/" + n), bytes(5));
        }
        DeletionPlan plan = plan(t, keepAB(t), set(t, "d1", "x.jpg"), set(t, "d2", "y.jpg"), set(t, "d3", "z.jpg"));
        Files.delete(t.resolve("B/x.jpg"));
        Files.delete(t.resolve("A/y.jpg"));

        VerifyReport report = report(verifier().verify(plan, NOOP, NEVER));

        assertEquals(List.of(
                check(t.resolve("B/x.jpg"), t.resolve("A/x.jpg"), Outcome.CANDIDATE_MISSING, "", "d1"),
                check(t.resolve("B/y.jpg"), t.resolve("A/y.jpg"), Outcome.KEEPER_MISSING, "", "d2"),
                check(t.resolve("B/z.jpg"), t.resolve("A/z.jpg"), Outcome.IDENTICAL, "", "d3")), report.checks());
    }

    @Test
    @DisplayName("DFS-034.AC-4 A file replaced by a link or a folder is not followed")
    void linkOrFolderIsNotFollowed() throws IOException {
        DeletionPlan plan = firstPlan();
        Path target = t.resolve("target.bin");
        Files.write(target, bytes(5));
        Files.delete(t.resolve("B/x.jpg"));
        try {
            Files.createSymbolicLink(t.resolve("B/x.jpg"), target);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            assumeTrue(false, "symlinks not allowed here: " + e);
        }
        Files.delete(t.resolve("A/y.bin"));
        Files.createDirectory(t.resolve("A/y.bin"));

        VerifyReport report = report(verifier().verify(plan, NOOP, NEVER));

        assertEquals(2, report.checks().size());
        OperationCheck x = report.checks().get(0);
        OperationCheck y = report.checks().get(1);
        assertEquals(Outcome.NOT_A_REGULAR_FILE, x.outcome());
        assertTrue(x.detail().startsWith("candidate"), x.detail());
        assertEquals(Outcome.NOT_A_REGULAR_FILE, y.outcome());
        assertTrue(y.detail().startsWith("keeper"), y.detail());
    }

    @Test
    @DisplayName("DFS-034.AC-4 A candidate replaced by a junction is not followed")
    void junctionIsNotFollowed() throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("win"), "junctions are Windows only");
        DeletionPlan plan = firstPlan();
        Path target = t.resolve("targetdir");
        Files.createDirectory(target);
        Files.delete(t.resolve("B/x.jpg"));
        Process p = new ProcessBuilder("cmd", "/c", "mklink", "/J", t.resolve("B/x.jpg").toString(),
                target.toString()).redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        assumeTrue(p.waitFor() == 0, "could not create a junction");

        VerifyReport report = report(verifier().verify(plan, NOOP, NEVER));

        OperationCheck x = report.checks().get(0);
        assertEquals(Outcome.NOT_A_REGULAR_FILE, x.outcome());
        assertTrue(x.detail().startsWith("candidate"), x.detail());
    }

    @Test
    @DisplayName("DFS-034.AC-5 An unreadable file does not stop the verify")
    void unreadableFileDoesNotStopVerify() throws IOException {
        DeletionPlan plan = firstPlan();
        NioContentComparator real = new NioContentComparator();
        ContentComparator fake = (candidate, keeper, progress, cancelled) -> {
            if (candidate.getFileName().toString().equals("x.jpg")) {
                throw new AccessDeniedException(candidate.toString());
            }
            return real.compare(candidate, keeper, progress, cancelled);
        };

        VerifyReport report = report(new VerifyPlanContentService(new NioFileAttributesReader(), fake)
                .verify(plan, NOOP, NEVER));

        assertEquals(2, report.checks().size());
        assertEquals(Outcome.UNREADABLE, report.checks().get(0).outcome());
        assertFalse(report.checks().get(0).detail().isBlank());
        assertEquals(Outcome.IDENTICAL, report.checks().get(1).outcome());
    }

    @Test
    @DisplayName("DFS-034.AC-6 Progress reaches the total")
    void progressReachesTotal() throws IOException {
        DeletionPlan plan = firstPlan();
        List<VerifyEvent> events = new ArrayList<>();

        report(verifier().verify(plan, events::add, NEVER));

        List<VerifyEvent.EntryChecked> entries = events.stream()
                .filter(VerifyEvent.EntryChecked.class::isInstance).map(VerifyEvent.EntryChecked.class::cast)
                .toList();
        assertEquals(2, entries.size());
        assertEquals(1, entries.get(0).done());
        assertEquals(2, entries.get(0).total());
        assertEquals(t.resolve("B/x.jpg"), entries.get(0).check().candidate());
        assertEquals(2, entries.get(1).done());
        assertEquals(2, entries.get(1).total());
        assertEquals(t.resolve("B/y.bin"), entries.get(1).check().candidate());
        List<VerifyEvent.BytesCompared> bytes = events.stream()
                .filter(VerifyEvent.BytesCompared.class::isInstance).map(VerifyEvent.BytesCompared.class::cast)
                .toList();
        assertFalse(bytes.isEmpty());
        long previous = 0;
        for (VerifyEvent.BytesCompared b : bytes) {
            assertTrue(b.bytesDone() >= previous);
            assertEquals(TOTAL, b.bytesTotal());
            previous = b.bytesDone();
        }
        assertEquals(TOTAL, bytes.get(bytes.size() - 1).bytesDone());
    }

    @Test
    @DisplayName("DFS-034.AC-6 A failing listener is detached")
    void failingListenerIsDetached() throws IOException {
        DeletionPlan plan = firstPlan();
        int[] calls = {0};

        VerifyReport report = report(verifier().verify(plan, e -> {
            calls[0]++;
            throw new IllegalStateException("boom");
        }, NEVER));

        assertEquals(2, report.checks().size());
        assertTrue(report.checks().stream().allMatch(c -> c.outcome() == Outcome.IDENTICAL));
        assertEquals("boom", report.listenerFailure());
        assertEquals(1, calls[0]);
    }

    @Test
    @DisplayName("DFS-034.AC-7 Cancel takes effect inside a large file")
    void cancelTakesEffectInsideLargeFile() throws IOException {
        DeletionPlan plan = firstPlan();
        AtomicBoolean cancel = new AtomicBoolean();
        List<VerifyEvent> events = new ArrayList<>();
        int[] entriesDone = {0};

        VerifyReport report = report(verifier().verify(plan, e -> {
            events.add(e);
            if (e instanceof VerifyEvent.EntryChecked) {
                entriesDone[0]++;
            } else if (e instanceof VerifyEvent.BytesCompared && entriesDone[0] == 1) {
                cancel.set(true);
            }
        }, cancel::get));

        assertTrue(report.cancelled());
        assertEquals(List.of(check(t.resolve("B/x.jpg"), t.resolve("A/x.jpg"), Outcome.IDENTICAL, "", "d1")),
                report.checks());
        VerifyEvent.BytesCompared last = null;
        for (VerifyEvent e : events) {
            if (e instanceof VerifyEvent.BytesCompared b) {
                last = b;
            }
        }
        assertTrue(last.bytesDone() < TOTAL, "bytes done " + last.bytesDone());
    }

    @Test
    @DisplayName("DFS-034.AC-7 Cancelled before the start")
    void cancelledBeforeStart() throws IOException {
        DeletionPlan plan = firstPlan();

        VerifyReport report = report(verifier().verify(plan, NOOP, () -> true));

        assertTrue(report.cancelled());
        assertEquals(List.of(), report.checks());
    }

    @Test
    @DisplayName("DFS-034.AC-8 An empty plan has no checks")
    void emptyPlanHasNoChecks() throws IOException {
        firstPlan();
        DeletionPlan empty = plan(t, new PlanRequest(List.of(), List.of(), List.of()));

        VerifyReport report = report(verifier().verify(empty, NOOP, NEVER));

        assertEquals(List.of(), report.checks());
        assertFalse(report.cancelled());
    }

    @Test
    @DisplayName("DFS-034.AC-8 Null inputs throw NullPointerException")
    void nullInputsThrow() throws IOException {
        DeletionPlan plan = firstPlan();
        VerifyPlanContent v = verifier();
        try {
            v.verify(null, NOOP, NEVER);
            fail("null plan");
        } catch (NullPointerException expected) {
            // expected
        }
        try {
            v.verify(plan, null, NEVER);
            fail("null listener");
        } catch (NullPointerException expected) {
            // expected
        }
        try {
            v.verify(plan, NOOP, null);
            fail("null cancel");
        } catch (NullPointerException expected) {
            // expected
        }
    }

    private Map<String, String> snapshot() throws IOException {
        Map<String, String> m = new TreeMap<>();
        try (Stream<Path> s = Files.walk(t)) {
            for (Path p : s.toList()) {
                BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                m.put(t.relativize(p).toString(), a.size() + "@" + a.lastModifiedTime().toMillis());
            }
        }
        return m;
    }

    @Test
    @DisplayName("DFS-034.AC-9 Verifying changes nothing")
    void verifyingChangesNothing() throws IOException {
        DeletionPlan plan = firstPlan();
        Map<String, String> before = snapshot();

        report(verifier().verify(plan, NOOP, NEVER));

        assertEquals(before, snapshot());
    }

    @Test
    @DisplayName("DFS-034.AC-10 The factory exposes a working verify")
    void factoryExposesVerify() throws IOException {
        DeletionPlan plan = firstPlan();

        VerifyReport report = report(ScannerFactories.verifyPlanContent().verify(plan, NOOP, NEVER));

        assertEquals(2, report.checks().size());
        assertTrue(report.checks().stream().allMatch(c -> c.outcome() == Outcome.IDENTICAL));
    }
}
