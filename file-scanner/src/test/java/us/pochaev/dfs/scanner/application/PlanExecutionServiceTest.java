package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import us.pochaev.dfs.scanner.domain.factory.PlanExecutionUseCases;
import us.pochaev.dfs.scanner.domain.factory.ScannerFactories;
import us.pochaev.dfs.scanner.domain.model.Conflict;
import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.ErrorCode;
import us.pochaev.dfs.scanner.domain.model.ExecutionConfig;
import us.pochaev.dfs.scanner.domain.model.ExecutionEvent;
import us.pochaev.dfs.scanner.domain.model.ExecutionListener;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.JobStatus;
import us.pochaev.dfs.scanner.domain.model.KeepAndQuarantine;
import us.pochaev.dfs.scanner.domain.model.Operation;
import us.pochaev.dfs.scanner.domain.model.OperationOutcome;
import us.pochaev.dfs.scanner.domain.model.PhysicalCopy;
import us.pochaev.dfs.scanner.domain.model.PlanExecutionId;
import us.pochaev.dfs.scanner.domain.model.PlanExecutionReport;
import us.pochaev.dfs.scanner.domain.model.PlanExecutionSnapshot;
import us.pochaev.dfs.scanner.domain.model.PlanRequest;
import us.pochaev.dfs.scanner.domain.model.PlanTotals;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.model.SkipCause;
import us.pochaev.dfs.scanner.infra.adapter.ExecutionLockProbe;
import us.pochaev.dfs.scanner.infra.adapter.FileChannelExecutionLock;
import us.pochaev.dfs.scanner.infra.adapter.InMemoryJobStore;
import us.pochaev.dfs.scanner.infra.adapter.JsonLinesManifestWriter;
import us.pochaev.dfs.scanner.infra.adapter.NioContentComparator;
import us.pochaev.dfs.scanner.infra.adapter.NioFileAttributesReader;
import us.pochaev.dfs.scanner.infra.adapter.NioQuarantineDeleter;
import us.pochaev.dfs.scanner.infra.port.ContentComparator;
import us.pochaev.dfs.scanner.infra.port.ExecutionLock;
import us.pochaev.dfs.scanner.infra.port.FileDeleter;
import us.pochaev.dfs.scanner.infra.port.JobStore;
import us.pochaev.dfs.scanner.infra.port.ManifestLine;
import us.pochaev.dfs.scanner.infra.port.ManifestWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Plan execution jobs (DFS-051): real NIO attributes, deleter and manifest writer over a {@code @TempDir}, wrapped in
 * spies that inject the failures; a manual executor; no sleeping.
 */
class PlanExecutionServiceTest {

    private static final byte[] PHOTO = "abcdefghijklmnopqrstuvwxyz!".getBytes(StandardCharsets.UTF_8); // 27 bytes
    private static final Instant T0 = Instant.parse("2024-03-01T10:00:00Z");

    @TempDir
    Path tmp;

    private Path lib() {
        return tmp.resolve("lib");
    }

    private Path manifests() {
        return tmp.resolve("manifests");
    }

    private final List<Rig> rigs = new CopyOnWriteArrayList<>();

    /** Frees every lease a test left: a locked file blocks {@code @TempDir} cleanup on Windows. */
    @AfterEach
    void cancelEveryRunThatWasLeftUnfinished() {
        for (Rig rig : rigs) {
            for (PlanExecutionId id : rig.store.created) {
                rig.service.cancel(id);
            }
        }
    }

    // ---- builders ----

    private Path file(String rel, byte[] content) throws IOException {
        Path p = lib().resolve(rel);
        Files.createDirectories(p.getParent());
        Files.write(p, content);
        Files.setLastModifiedTime(p, FileTime.from(T0));
        return p;
    }

    private static FileEntry entry(Path p) throws IOException {
        BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class,
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
        return new FileEntry(p, a.size(), a.lastModifiedTime().toInstant());
    }

    /** A set whose every file is its own physical copy. */
    private static DuplicateSet set(String digest, Path... files) throws IOException {
        List<FileEntry> entries = new ArrayList<>();
        for (Path f : files) {
            entries.add(entry(f));
        }
        return new DuplicateSet(entries.get(0).size(), digest, entries);
    }

    /** A set with the given copies, each a list of hard-linked paths. */
    private static DuplicateSet setOfCopies(String digest, List<List<Path>> copies) throws IOException {
        List<FileEntry> all = new ArrayList<>();
        List<PhysicalCopy> physical = new ArrayList<>();
        for (List<Path> links : copies) {
            List<FileEntry> entries = new ArrayList<>();
            for (Path p : links) {
                entries.add(entry(p));
            }
            all.addAll(entries);
            physical.add(new PhysicalCopy(entries));
        }
        return new DuplicateSet(all.get(0).size(), digest, all, physical);
    }

    private ScanResult scan(DuplicateSet... sets) {
        return new ScanResult(List.of(lib()), List.of(), List.of(sets), List.of(), 0);
    }

    private KeepAndQuarantine rule(String keep, String quarantineIn) {
        return new KeepAndQuarantine(lib().resolve(keep), lib().resolve(quarantineIn));
    }

    private static DeletionPlan plan(ScanResult scan, KeepAndQuarantine... rules) {
        return ok(ScannerFactories.previewDeletionPlan().preview(scan,
                new PlanRequest(List.of(rules), List.of(), List.of())));
    }

    /** A/photo.jpg and B/photo.jpg with the same 27 bytes; keep A, quarantine B. */
    private record Fx(Path a, Path b, ScanResult scan, DeletionPlan plan) {
    }

    private Fx basic() throws IOException {
        Path a = file("A/photo.jpg", PHOTO);
        Path b = file("B/photo.jpg", PHOTO);
        ScanResult scan = scan(set("d-photo", a, b));
        return new Fx(a, b, scan, plan(scan, rule("A", "B")));
    }

    @SuppressWarnings("unchecked")
    private static <T> T ok(Result<T> result) {
        return ((Result.Success<T>) assertInstanceOf(Result.Success.class, result, String.valueOf(result))).value();
    }

    @SuppressWarnings("unchecked")
    private static Result.Failure<?> failed(Result<?> result) {
        return assertInstanceOf(Result.Failure.class, result, String.valueOf(result));
    }

    private Path quarantined(PlanExecutionId id, Path original) {
        return tmp.resolve(".dfs-quarantine").resolve(id.value().toString()).resolve(tmp.relativize(original));
    }

    private Path manifestOf(PlanExecutionId id) {
        return manifests().resolve(id.value() + ".jsonl");
    }

    private List<Path> allQuarantinedFiles() throws IOException {
        Path root = tmp.resolve(".dfs-quarantine");
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(Files::isRegularFile).toList();
        }
    }

    // ---- manifest reading ----

    private static List<String> lines(Path manifest) throws IOException {
        return Files.readAllLines(manifest, StandardCharsets.UTF_8).stream().filter(l -> !l.isBlank()).toList();
    }

    private static String type(String line) {
        Matcher m = Pattern.compile("\"type\"\\s*:\\s*\"(\\w+)\"").matcher(line);
        assertTrue(m.find(), line);
        return m.group(1);
    }

    private static List<String> types(Path manifest) throws IOException {
        return lines(manifest).stream().map(PlanExecutionServiceTest::type).toList();
    }

    /** A string field of a manifest line, unescaped. */
    private static String str(String line, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(line);
        assertTrue(m.find(), field + " in " + line);
        String raw = m.group(1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char n = raw.charAt(++i);
            switch (n) {
                case 'u' -> {
                    out.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                default -> out.append(n);
            }
        }
        return out.toString();
    }

    private static List<String> linesOfType(Path manifest, String type) throws IOException {
        return lines(manifest).stream().filter(l -> type(l).equals(type)).toList();
    }

    // ---- fakes ----

    /** Queues tasks until the test runs them, each on its own thread. */
    static final class ManualExecutor implements Executor {
        private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        volatile Thread lastThread;
        volatile Throwable thrown;

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        int pending() {
            return tasks.size();
        }

        /** Starts the next task on a new thread and returns it without waiting. */
        Thread startNext() {
            Runnable r = tasks.poll();
            if (r == null) {
                throw new IllegalStateException("no queued task");
            }
            thrown = null;
            Thread t = new Thread(() -> {
                try {
                    r.run();
                } catch (Throwable e) {
                    thrown = e;
                }
            });
            lastThread = t;
            t.start();
            return t;
        }

        /** Runs the next task on its own thread, waits for it, and returns what it threw (null if nothing). */
        Throwable runNext() throws InterruptedException {
            Thread t = startNext();
            t.join(30_000);
            assertFalse(t.isAlive(), "task still running");
            return thrown;
        }
    }

    static final class CountingStore<I, R> implements JobStore<I, R> {
        final InMemoryJobStore<I, R> inner = new InMemoryJobStore<>();
        final List<I> created = new CopyOnWriteArrayList<>();

        @Override
        public void create(I id, R record) {
            inner.create(id, record);
            created.add(id);
        }

        @Override
        public Optional<I> createUnless(I id, R record, Predicate<? super R> blocking) {
            Optional<I> blocker = inner.createUnless(id, record, blocking);
            if (blocker.isEmpty()) {
                created.add(id);
            }
            return blocker;
        }

        @Override
        public Optional<R> find(I id) {
            return inner.find(id);
        }

        @Override
        public Optional<R> update(I id, UnaryOperator<R> change) {
            return inner.update(id, change);
        }
    }

    /** A fake lock that counts acquires and live leases, and can answer held elsewhere or fail. */
    static final class CountingLock implements ExecutionLock {
        final AtomicInteger acquires = new AtomicInteger();
        final AtomicInteger live = new AtomicInteger();
        volatile boolean heldElsewhere;
        volatile IOException failure;

        @Override
        public Acquisition acquire(Path manifestFolder) throws IOException {
            acquires.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            if (heldElsewhere) {
                return new Acquisition.HeldElsewhere();
            }
            live.incrementAndGet();
            AtomicBoolean released = new AtomicBoolean();
            return new Acquisition.Acquired(() -> {
                if (released.compareAndSet(false, true)) {
                    live.decrementAndGet();
                }
            });
        }
    }

    /** The real comparator, recording candidates and able to run a hook first. */
    static final class SpyComparator implements ContentComparator {
        private final ContentComparator real = new NioContentComparator();
        final List<Path> compared = new CopyOnWriteArrayList<>();
        volatile Runnable before = () -> { };

        @Override
        public Comparison compare(Path candidate, Path keeper, LongConsumer bytesCompared, BooleanSupplier cancelled)
                throws IOException {
            compared.add(candidate);
            before.run();
            return real.compare(candidate, keeper, bytesCompared, cancelled);
        }
    }

    /** The real quarantine adapter, able to fail chosen moves and to run a hook before each. */
    static final class SpyDeleter implements FileDeleter {
        final FileDeleter real;
        final Map<Path, String> failMove = new ConcurrentHashMap<>();
        final Set<Path> notWritable = ConcurrentHashMap.newKeySet();
        volatile Consumer<Path> onMove = p -> { };

        SpyDeleter(FileDeleter real) {
            this.real = real;
        }

        @Override
        public Path quarantinePath(Path file, PlanExecutionId run) throws IOException {
            return real.quarantinePath(file, run);
        }

        @Override
        public MoveResult move(Path file, Path quarantinePath) {
            onMove.accept(file);
            if (notWritable.contains(file)) {
                return new MoveResult.QuarantineNotWritable("read-only volume");
            }
            String failure = failMove.get(file);
            if (failure != null) {
                return new MoveResult.Failed(failure);
            }
            return real.move(file, quarantinePath);
        }
    }

    /** The real manifest writer, able to fail the append of a chosen line. */
    static final class SpyManifests implements ManifestWriter {
        private final ManifestWriter real = new JsonLinesManifestWriter();
        volatile Predicate<ManifestLine> failOn = l -> false;

        @Override
        public Path create(Path folder, ManifestLine.Run header) throws IOException {
            return real.create(folder, header);
        }

        @Override
        public Session open(Path manifest) throws IOException {
            Session inner = real.open(manifest);
            return new Session() {
                @Override
                public void append(ManifestLine line) throws IOException {
                    if (failOn.test(line)) {
                        throw new IOException("disk full");
                    }
                    inner.append(line);
                }

                @Override
                public void close() throws IOException {
                    inner.close();
                }
            };
        }
    }

    /** One service on spies over real files in the temp folder. */
    final class Rig {
        final ManualExecutor manual = new ManualExecutor();
        final CountingStore<PlanExecutionId, Job<PlanExecutionProgress, PlanExecutionReport>> store =
                new CountingStore<>();
        final SpyComparator comparator = new SpyComparator();
        final SpyDeleter deleter = new SpyDeleter(new NioQuarantineDeleter(List.of(tmp)));
        final SpyManifests manifestWriter = new SpyManifests();
        final PlanExecutionService service;

        Rig() {
            this(null, null);
        }

        Rig(Executor executor) {
            this(executor, null);
        }

        /** A null executor means the manual one; a null lock means a real {@link FileChannelExecutionLock}. */
        Rig(Executor executor, ExecutionLock lock) {
            service = new PlanExecutionService(new NioFileAttributesReader(), comparator, deleter, manifestWriter,
                    lock == null ? new FileChannelExecutionLock() : lock, store, executor == null ? manual : executor,
                    new ExecutionConfig(manifests()));
            rigs.add(this);
        }

        PlanExecutionId start(ScanResult scan, DeletionPlan plan) {
            return ok(service.start(scan, plan));
        }

        /** Starts the plan and runs the queued task to its end on its own thread. */
        PlanExecutionId run(ScanResult scan, DeletionPlan plan) throws InterruptedException {
            PlanExecutionId id = start(scan, plan);
            assertNull(manual.runNext());
            return id;
        }

        PlanExecutionReport report(PlanExecutionId id) {
            return ok(service.result(id));
        }

        PlanExecutionSnapshot status(PlanExecutionId id) {
            return ok(service.status(id));
        }
    }

    private void assertNoTrace(Rig rig) throws IOException {
        assertTrue(rig.store.created.isEmpty(), "jobs recorded: " + rig.store.created);
        assertEquals(0, rig.manual.pending());
        if (Files.exists(manifests())) {
            try (Stream<Path> s = Files.list(manifests())) {
                assertEquals(List.of(), s.toList());
            }
        }
        assertEquals(List.of(), allQuarantinedFiles());
    }

    // ---- AC-1, AC-9, AC-18 ----

    @Test
    @DisplayName("DFS-051.AC-1, DFS-051.AC-9, DFS-051.AC-18 A plan on unchanged files moves the candidate into quarantine")
    void planOnUnchangedFilesMovesTheCandidate() throws Exception {
        Fx fx = basic();
        Rig rig = new Rig();

        PlanExecutionId id = rig.run(fx.scan(), fx.plan());

        assertEquals(JobStatus.COMPLETED, rig.status(id).status());
        assertFalse(Files.exists(fx.b()));
        assertEquals(27, Files.size(quarantined(id, fx.b())));
        assertTrue(java.util.Arrays.equals(PHOTO, Files.readAllBytes(quarantined(id, fx.b()))));
        assertTrue(java.util.Arrays.equals(PHOTO, Files.readAllBytes(fx.a())));
        assertEquals(List.of("run", "moving", "moved", "end"), types(manifestOf(id)));

        PlanExecutionReport report = rig.report(id);
        assertEquals(id, report.runId());
        assertEquals(manifestOf(id), report.manifest());
        assertEquals(1, report.outcomes().size());
        OperationOutcome.Moved moved = assertInstanceOf(OperationOutcome.Moved.class, report.outcomes().get(0));
        assertEquals(List.of(new us.pochaev.dfs.scanner.domain.model.MovedFile(fx.b(), quarantined(id, fx.b()))),
                moved.moved());
        assertEquals(1, report.filesMoved());
        assertEquals(27, report.bytesMoved());
        assertEquals(0, report.filesSkipped());
        assertEquals(0, report.bytesSkipped());
    }

    @Test
    @DisplayName("DFS-051.AC-9 The moving line is on disk before the rename")
    void movingLineIsOnDiskBeforeTheRename() throws Exception {
        Fx fx = basic();
        Rig rig = new Rig();
        AtomicReference<List<String>> seen = new AtomicReference<>();
        rig.deleter.onMove = p -> {
            if (p.equals(fx.b())) {
                try (Stream<Path> s = Files.list(manifests())) {
                    seen.set(lines(s.filter(m -> m.getFileName().toString().endsWith(".jsonl")).findFirst()
                            .orElseThrow()));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };

        rig.run(fx.scan(), fx.plan());

        List<String> atMove = seen.get();
        assertTrue(atMove != null && !atMove.isEmpty(), "move was never asked for");
        String last = atMove.get(atMove.size() - 1);
        assertEquals("moving", type(last));
        assertEquals(fx.b().toString(), str(last, "path"));
        assertEquals(fx.a().toString(), str(last, "keeper"));
    }

    // ---- AC-2, AC-3, AC-22, AC-23 ----

    @Test
    @DisplayName("DFS-051.AC-2 A plan with a conflict is refused")
    void planWithConflictIsRefused() throws Exception {
        Fx fx = basic();
        DeletionPlan conflicted = plan(fx.scan(), rule("A", "B"), rule("B", "A"));
        assertFalse(conflicted.conflicts().isEmpty());
        Rig rig = new Rig();

        Result.Failure<?> failure = failed(rig.service.start(fx.scan(), conflicted));

        assertEquals(ErrorCode.PLAN_HAS_CONFLICTS, failure.code());
        assertNoTrace(rig);
        assertTrue(Files.exists(fx.a()) && Files.exists(fx.b()));
    }

    @Test
    @DisplayName("DFS-051.AC-3 A plan from another scan is refused")
    void planFromAnotherScanIsRefused() throws Exception {
        Fx fx = basic();
        Path otherA = file("A/other.jpg", "zzzzzzzzzz".getBytes(StandardCharsets.UTF_8));
        Path otherB = file("B/other.jpg", "zzzzzzzzzz".getBytes(StandardCharsets.UTF_8));
        ScanResult otherScan = scan(set("d-other", otherA, otherB));
        DeletionPlan otherPlan = plan(otherScan, rule("A", "B"));
        Rig rig = new Rig();

        Result.Failure<?> failure = failed(rig.service.start(fx.scan(), otherPlan));

        assertEquals(ErrorCode.PLAN_NOT_FROM_SCAN, failure.code());
        assertNoTrace(rig);
    }

    @Test
    @DisplayName("DFS-051.AC-22 A plan edited after its preview is refused")
    void editedPlanIsRefused() throws Exception {
        Fx fx = basic();
        DeletionPlan conflicted = plan(fx.scan(), rule("A", "B"), rule("B", "A"));
        assertFalse(conflicted.conflicts().isEmpty());
        DeletionPlan edited = new DeletionPlan(conflicted.id(), conflicted.operations(), List.of(),
                conflicted.totals(), conflicted.leftBehind());
        Rig rig = new Rig();

        Result.Failure<?> failure = failed(rig.service.start(fx.scan(), edited));

        assertEquals(ErrorCode.PLAN_EDITED, failure.code());
        assertNoTrace(rig);
        assertTrue(Files.exists(fx.a()) && Files.exists(fx.b()));
    }

    @Test
    @DisplayName("DFS-051.AC-22 PlanIds recomputes the ID the preview gave, for operations and for a conflict")
    void planIdsMatchThePreview() throws Exception {
        Fx fx = basic();
        DeletionPlan conflicted = plan(fx.scan(), rule("A", "B"), rule("B", "A"));

        assertEquals(fx.plan().id(), PlanIds.of(fx.plan().operations(), fx.plan().conflicts()));
        assertEquals(conflicted.id(), PlanIds.of(conflicted.operations(), conflicted.conflicts()));
        assertNotEquals(PlanIds.of(fx.plan().operations(), fx.plan().conflicts()),
                PlanIds.of(conflicted.operations(), conflicted.conflicts()));
    }

    @ParameterizedTest(name = "DFS-051.AC-23 An operation whose keeper is not a separate copy is refused: {0}")
    @DisplayName("DFS-051.AC-23 An operation whose keeper is not a separate copy is refused")
    @EnumSource(BadKeeper.class)
    void keeperNotASeparateCopyIsRefused(BadKeeper bad) throws Exception {
        Path a = file("A/photo.jpg", PHOTO);
        Path b = file("B/photo.jpg", PHOTO);
        Path old = file("B/old/photo.jpg", PHOTO);
        ScanResult scan = scan(setOfCopies("d-photo", List.of(List.of(a), List.of(b, old))));
        DeletionPlan previewed = plan(scan, rule("A", "B"));
        Operation.Quarantine op = (Operation.Quarantine) previewed.operations().get(0);
        Path keeper = switch (bad) {
            case OWN_LINK -> b;
            case SAME_PHYSICAL_FILE -> old;
            case IN_NO_SET -> lib().resolve("C/other.jpg");
        };
        Operation.Quarantine rebuilt = new Operation.Quarantine(op.digest(), op.size(), op.links(), keeper,
                op.source());
        List<Operation> ops = List.of(rebuilt);
        DeletionPlan edited = new DeletionPlan(PlanIds.of(ops, List.of()), ops, List.of(), previewed.totals(),
                previewed.leftBehind());
        Rig rig = new Rig();

        Result.Failure<?> failure = failed(rig.service.start(scan, edited));

        assertEquals(ErrorCode.KEEPER_NOT_SEPARATE_COPY, failure.code());
        assertTrue(failure.message().contains("d-photo"), failure.message());
        assertNoTrace(rig);
        assertTrue(Files.exists(a) && Files.exists(b));
    }

    enum BadKeeper {
        OWN_LINK, SAME_PHYSICAL_FILE, IN_NO_SET
    }

    // ---- AC-4, AC-5, AC-6 ----

    enum Drift {
        CANDIDATE_REWRITTEN_28_BYTES, CANDIDATE_NEW_MODIFIED_TIME, KEEPER_DELETED, CANDIDATE_DELETED
    }

    @ParameterizedTest(name = "DFS-051.AC-4 A path that drifted since the scan is skipped: {0}")
    @DisplayName("DFS-051.AC-4 A path that drifted since the scan is skipped")
    @EnumSource(Drift.class)
    void driftedPathIsSkipped(Drift drift) throws Exception {
        Fx fx = basic();
        SkipCause cause;
        Path subject;
        switch (drift) {
            case CANDIDATE_REWRITTEN_28_BYTES -> {
                Files.write(fx.b(), "abcdefghijklmnopqrstuvwxyz!!".getBytes(StandardCharsets.UTF_8));
                cause = SkipCause.SIZE_CHANGED;
                subject = fx.b();
            }
            case CANDIDATE_NEW_MODIFIED_TIME -> {
                Files.setLastModifiedTime(fx.b(), FileTime.from(T0.plusSeconds(3600)));
                cause = SkipCause.MODIFIED_CHANGED;
                subject = fx.b();
            }
            case KEEPER_DELETED -> {
                Files.delete(fx.a());
                cause = SkipCause.MISSING;
                subject = fx.a();
            }
            default -> {
                Files.delete(fx.b());
                cause = SkipCause.MISSING;
                subject = fx.b();
            }
        }
        Rig rig = new Rig();

        PlanExecutionId id = rig.run(fx.scan(), fx.plan());

        OperationOutcome.Skipped skipped = assertInstanceOf(OperationOutcome.Skipped.class,
                rig.report(id).outcomes().get(0));
        assertEquals(1, skipped.skipped().size());
        assertEquals(fx.b(), skipped.skipped().get(0).path());
        assertEquals(cause, skipped.skipped().get(0).cause());
        assertEquals(subject, skipped.skipped().get(0).subject());
        assertEquals(List.of(), allQuarantinedFiles());
        if (drift != Drift.CANDIDATE_DELETED) {
            assertTrue(Files.exists(fx.b()));
        }
        List<String> kinds = types(manifestOf(id));
        assertTrue(kinds.contains("skipped"), kinds.toString());
        assertFalse(kinds.contains("moved"), kinds.toString());
        assertEquals(fx.b().toString(), str(linesOfType(manifestOf(id), "skipped").get(0), "path"));
        assertEquals(JobStatus.COMPLETED, rig.status(id).status());
    }

    @Test
    @DisplayName("DFS-051.AC-5 A candidate whose bytes now differ is skipped")
    void candidateWhoseBytesDifferIsSkipped() throws Exception {
        Fx fx = basic();
        byte[] other = "ABCDEFGHIJKLMNOPQRSTUVWXYZ!".getBytes(StandardCharsets.UTF_8);
        Files.write(fx.b(), other);
        Files.setLastModifiedTime(fx.b(), FileTime.from(T0));
        Rig rig = new Rig();

        PlanExecutionId id = rig.run(fx.scan(), fx.plan());

        OperationOutcome.Skipped skipped = assertInstanceOf(OperationOutcome.Skipped.class,
                rig.report(id).outcomes().get(0));
        assertEquals(SkipCause.DIFFERS, skipped.skipped().get(0).cause());
        assertTrue(java.util.Arrays.equals(other, Files.readAllBytes(fx.b())));
        assertEquals(List.of(), allQuarantinedFiles());
    }

    @Test
    @DisplayName("DFS-051.AC-6 A candidate that became a symlink is neither read nor moved")
    void candidateThatBecameASymlinkIsNeitherReadNorMoved() throws Exception {
        Fx fx = basic();
        Path copy = file("target/photo.jpg", PHOTO);
        Files.delete(fx.b());
        try {
            Files.createSymbolicLink(fx.b(), copy);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("cannot create a symlink here: " + e);
        }
        Rig rig = new Rig();

        PlanExecutionId id = rig.run(fx.scan(), fx.plan());

        OperationOutcome.Skipped skipped = assertInstanceOf(OperationOutcome.Skipped.class,
                rig.report(id).outcomes().get(0));
        assertEquals(SkipCause.NOT_A_REGULAR_FILE, skipped.skipped().get(0).cause());
        assertEquals(fx.b(), skipped.skipped().get(0).subject());
        assertTrue(Files.isSymbolicLink(fx.b()));
        assertEquals(List.of(), rig.comparator.compared);
        assertEquals(List.of(), allQuarantinedFiles());
    }

    @Test
    @DisplayName("DFS-051.AC-6 A keeper that became a symlink is neither read nor moved")
    void keeperThatBecameASymlinkIsNeitherReadNorMoved() throws Exception {
        Fx fx = basic();
        Path copy = file("target/photo.jpg", PHOTO);
        Files.delete(fx.a());
        try {
            Files.createSymbolicLink(fx.a(), copy);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("cannot create a symlink here: " + e);
        }
        Rig rig = new Rig();

        PlanExecutionId id = rig.run(fx.scan(), fx.plan());

        OperationOutcome.Skipped skipped = assertInstanceOf(OperationOutcome.Skipped.class,
                rig.report(id).outcomes().get(0));
        assertEquals(SkipCause.NOT_A_REGULAR_FILE, skipped.skipped().get(0).cause());
        assertEquals(fx.a(), skipped.skipped().get(0).subject());
        assertTrue(Files.exists(fx.b()));
        assertEquals(List.of(), rig.comparator.compared);
        assertEquals(List.of(), allQuarantinedFiles());
    }

    // ---- AC-7, AC-8, AC-11 ----

    @Test
    @DisplayName("DFS-051.AC-7 A move that fails is skipped and the run continues")
    void failedMoveIsSkippedAndTheRunContinues() throws Exception {
        Path a = file("A/photo.jpg", PHOTO);
        Path b = file("B/photo.jpg", PHOTO);
        Path docA = file("A/doc.txt", "0123456789".getBytes(StandardCharsets.UTF_8));
        Path docC = file("C/doc.txt", "0123456789".getBytes(StandardCharsets.UTF_8));
        ScanResult scan = scan(set("d-photo", a, b), set("d-doc", docA, docC));
        DeletionPlan plan = plan(scan, rule("A", "B"), rule("A", "C"));
        Rig rig = new Rig();
        rig.deleter.failMove.put(b, "access denied");

        PlanExecutionId id = rig.run(scan, plan);

        PlanExecutionReport report = rig.report(id);
        assertEquals(JobStatus.COMPLETED, rig.status(id).status());
        assertEquals(2, report.outcomes().size());
        for (OperationOutcome outcome : report.outcomes()) {
            if (outcome.keeper().equals(a)) {
                OperationOutcome.Skipped skipped = assertInstanceOf(OperationOutcome.Skipped.class, outcome);
                assertEquals(SkipCause.MOVE_FAILED, skipped.skipped().get(0).cause());
                assertEquals("access denied", skipped.skipped().get(0).detail());
            } else {
                assertInstanceOf(OperationOutcome.Moved.class, outcome);
            }
        }
        assertTrue(Files.exists(b));
        assertFalse(Files.exists(docC));
        assertTrue(Files.exists(quarantined(id, docC)));
    }

    private record HardLinked(Path a, Path b, Path old, ScanResult scan, DeletionPlan plan) {
    }

    /** B/photo.jpg and B/old/photo.jpg are declared one physical copy (separate identical files on disk). */
    private HardLinked hardLinked() throws IOException {
        Path a = file("A/photo.jpg", PHOTO);
        Path b = file("B/photo.jpg", PHOTO);
        Path old = file("B/old/photo.jpg", PHOTO);
        ScanResult scan = scan(setOfCopies("d-photo", List.of(List.of(a), List.of(b, old))));
        DeletionPlan plan = plan(scan, rule("A", "B"));
        assertEquals(1, plan.operations().size());
        assertEquals(2, ((Operation.Quarantine) plan.operations().get(0)).links().size());
        return new HardLinked(a, b, old, scan, plan);
    }

    @Test
    @DisplayName("DFS-051.AC-8 Every hard link of an operation moves and the content is compared once")
    void everyHardLinkMovesAndContentIsComparedOnce() throws Exception {
        HardLinked h = hardLinked();
        Rig rig = new Rig();

        PlanExecutionId id = rig.run(h.scan(), h.plan());

        assertFalse(Files.exists(h.b()));
        assertFalse(Files.exists(h.old()));
        assertTrue(Files.exists(quarantined(id, h.b())));
        assertTrue(Files.exists(quarantined(id, h.old())));
        assertEquals(1, rig.comparator.compared.size());
        OperationOutcome.Moved moved = assertInstanceOf(OperationOutcome.Moved.class,
                rig.report(id).outcomes().get(0));
        assertEquals(2, moved.moved().size());
        assertEquals(2, rig.report(id).filesMoved());
    }

    @Test
    @DisplayName("DFS-051.AC-8 A link that fails leaves the operation partly moved")
    void linkThatFailsLeavesTheOperationPartlyMoved() throws Exception {
        HardLinked h = hardLinked();
        List<Path> links = ((Operation.Quarantine) h.plan().operations().get(0)).links();
        Path first = links.get(0);
        Path second = links.get(1);
        Rig rig = new Rig();
        rig.deleter.failMove.put(second, "locked");

        PlanExecutionId id = rig.run(h.scan(), h.plan());

        assertTrue(Files.exists(quarantined(id, first)));
        assertFalse(Files.exists(first));
        assertTrue(Files.exists(second));
        OperationOutcome.PartlyMoved partly = assertInstanceOf(OperationOutcome.PartlyMoved.class,
                rig.report(id).outcomes().get(0));
        assertEquals(List.of(first), partly.moved().stream().map(m -> m.original()).toList());
        assertEquals(List.of(second), partly.skipped().stream().map(s -> s.path()).toList());
        assertEquals(SkipCause.MOVE_FAILED, partly.skipped().get(0).cause());
        assertEquals(1, rig.comparator.compared.size());
    }

    @Test
    @DisplayName("DFS-051.AC-11 A volume that cannot hold the quarantine folder is skipped")
    void unwritableQuarantineIsSkipped() throws Exception {
        Fx fx = basic();
        Rig rig = new Rig();
        rig.deleter.notWritable.add(fx.b());

        PlanExecutionId id = rig.run(fx.scan(), fx.plan());

        OperationOutcome.Skipped skipped = assertInstanceOf(OperationOutcome.Skipped.class,
                rig.report(id).outcomes().get(0));
        assertEquals(SkipCause.QUARANTINE_NOT_WRITABLE, skipped.skipped().get(0).cause());
        assertTrue(Files.exists(fx.b()));
        assertEquals(List.of(), allQuarantinedFiles());
    }

    // ---- AC-10 ----

    @Test
    @DisplayName("DFS-051.AC-10 A manifest folder that cannot be written refuses the run")
    void unwritableManifestFolderRefusesTheRun() throws Exception {
        Fx fx = basic();
        Files.write(manifests(), new byte[] {1});
        Rig rig = new Rig();

        Result.Failure<?> failure = failed(rig.service.start(fx.scan(), fx.plan()));

        assertEquals(ErrorCode.MANIFEST_UNWRITABLE, failure.code());
        assertTrue(rig.store.created.isEmpty());
        assertEquals(0, rig.manual.pending());
        assertTrue(Files.exists(fx.a()) && Files.exists(fx.b()));
        assertEquals(List.of(), allQuarantinedFiles());
    }

    @Test
    @DisplayName("DFS-051.AC-10 A manifest line that cannot be written stops the run")
    void unwritableManifestLineStopsTheRun() throws Exception {
        Path a = file("A/photo.jpg", PHOTO);
        Path b = file("B/photo.jpg", PHOTO);
        Path docA = file("A/doc.txt", "0123456789".getBytes(StandardCharsets.UTF_8));
        Path docC = file("C/doc.txt", "0123456789".getBytes(StandardCharsets.UTF_8));
        ScanResult scan = scan(set("d-photo", a, b), set("d-doc", docA, docC));
        DeletionPlan plan = plan(scan, rule("A", "B"), rule("A", "C"));
        Path firstLink = ((Operation.Quarantine) plan.operations().get(0)).links().get(0);
        Path secondLink = ((Operation.Quarantine) plan.operations().get(1)).links().get(0);
        Rig rig = new Rig();
        rig.manifestWriter.failOn = line -> line instanceof ManifestLine.Moving m && m.path().equals(secondLink);

        PlanExecutionId id = rig.start(scan, plan);
        rig.manual.runNext();

        PlanExecutionSnapshot snapshot = rig.status(id);
        assertEquals(JobStatus.FAILED, snapshot.status());
        assertTrue(snapshot.failure().contains("disk full"), snapshot.failure());
        assertTrue(Files.exists(quarantined(id, firstLink)));
        assertTrue(Files.exists(secondLink));
        assertFalse(Files.exists(quarantined(id, secondLink)));
    }

    // ---- AC-12, AC-13 ----

    @Test
    @DisplayName("DFS-051.AC-12 Cancelling a queued run moves nothing")
    void cancellingAQueuedRunMovesNothing() throws Exception {
        Fx fx = basic();
        Rig rig = new Rig();

        PlanExecutionId id = rig.start(fx.scan(), fx.plan());
        assertEquals(JobStatus.QUEUED, rig.status(id).status());
        assertEquals(JobStatus.CANCELLED, ok(rig.service.cancel(id)).status());
        assertNull(rig.manual.runNext());

        assertEquals(JobStatus.CANCELLED, rig.status(id).status());
        assertTrue(Files.exists(fx.b()));
        assertEquals(List.of(), allQuarantinedFiles());
        PlanExecutionReport report = rig.report(id);
        assertTrue(report.cancelled());
        assertEquals(1, report.outcomes().size());
        assertInstanceOf(OperationOutcome.NotAttempted.class, report.outcomes().get(0));
        assertEquals(0, report.filesMoved());
    }

    /** A rig whose comparator blocks on its first compare, with two operations (photo, then doc). */
    private record Blocked(Rig rig, ScanResult scan, DeletionPlan plan, CountDownLatch blocked,
                           CountDownLatch release) {
    }

    private Blocked blockedRig(boolean twoOperations) throws IOException {
        return blockedRig(twoOperations, null);
    }

    private Blocked blockedRig(boolean twoOperations, ExecutionLock lock) throws IOException {
        Path a = file("A/photo.jpg", PHOTO);
        Path b = file("B/photo.jpg", PHOTO);
        ScanResult scan;
        DeletionPlan plan;
        if (twoOperations) {
            Path docA = file("A/doc.txt", "0123456789".getBytes(StandardCharsets.UTF_8));
            Path docC = file("C/doc.txt", "0123456789".getBytes(StandardCharsets.UTF_8));
            scan = scan(set("d-photo", a, b), set("d-doc", docA, docC));
            plan = plan(scan, rule("A", "B"), rule("A", "C"));
        } else {
            scan = scan(set("d-photo", a, b));
            plan = plan(scan, rule("A", "B"));
        }
        Rig rig = new Rig(null, lock);
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        rig.comparator.before = () -> {
            blocked.countDown();
            try {
                if (!release.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("never released");
                }
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        };
        return new Blocked(rig, scan, plan, blocked, release);
    }

    @Test
    @DisplayName("DFS-051.AC-12 Cancelling a running run finishes the operation in progress")
    void cancellingARunningRunFinishesTheOperationInProgress() throws Exception {
        Blocked bl = blockedRig(true);
        Rig rig = bl.rig();
        Path firstLink = ((Operation.Quarantine) bl.plan().operations().get(0)).links().get(0);
        Path secondLink = ((Operation.Quarantine) bl.plan().operations().get(1)).links().get(0);

        PlanExecutionId id = rig.start(bl.scan(), bl.plan());
        Thread task = rig.manual.startNext();
        assertTrue(bl.blocked().await(10, TimeUnit.SECONDS), "the comparator never started");
        assertEquals(JobStatus.RUNNING, rig.status(id).status());

        assertEquals(JobStatus.CANCELLING, ok(rig.service.cancel(id)).status());
        assertEquals(JobStatus.CANCELLING, rig.status(id).status());
        bl.release().countDown();
        task.join(30_000);
        assertFalse(task.isAlive());

        assertEquals(JobStatus.CANCELLED, rig.status(id).status());
        assertTrue(Files.exists(quarantined(id, firstLink)));
        assertTrue(Files.exists(secondLink));
        PlanExecutionReport report = rig.report(id);
        assertTrue(report.cancelled());
        assertInstanceOf(OperationOutcome.Moved.class, report.outcomes().get(0));
        assertInstanceOf(OperationOutcome.NotAttempted.class, report.outcomes().get(1));
        List<String> lines = lines(manifestOf(id));
        String last = lines.get(lines.size() - 1);
        assertEquals("end", type(last));
        assertEquals("CANCELLED", str(last, "status"));
    }

    @Test
    @DisplayName("DFS-051.AC-13 A cancel recorded before completion wins")
    void cancelRecordedBeforeCompletionWins() throws Exception {
        Blocked bl = blockedRig(false);
        Rig rig = bl.rig();

        PlanExecutionId id = rig.start(bl.scan(), bl.plan());
        Thread task = rig.manual.startNext();
        assertTrue(bl.blocked().await(10, TimeUnit.SECONDS), "the comparator never started");
        ok(rig.service.cancel(id));
        bl.release().countDown();
        task.join(30_000);
        assertFalse(task.isAlive());

        assertEquals(JobStatus.CANCELLED, rig.status(id).status());
        assertTrue(rig.report(id).cancelled());
        List<String> lines = lines(manifestOf(id));
        assertEquals("CANCELLED", str(lines.get(lines.size() - 1), "status"));
    }

    @Test
    @DisplayName("DFS-051.AC-13 A cancel after completion changes nothing")
    void cancelAfterCompletionChangesNothing() throws Exception {
        Fx fx = basic();
        Rig rig = new Rig();
        PlanExecutionId id = rig.run(fx.scan(), fx.plan());
        PlanExecutionReport before = rig.report(id);

        PlanExecutionSnapshot afterCancel = ok(rig.service.cancel(id));

        assertEquals(JobStatus.COMPLETED, afterCancel.status());
        assertEquals(JobStatus.COMPLETED, rig.status(id).status());
        assertEquals(before, rig.report(id));
        assertFalse(rig.report(id).cancelled());
        List<String> lines = lines(manifestOf(id));
        assertEquals("COMPLETED", str(lines.get(lines.size() - 1), "status"));
    }

    // ---- AC-14 ----

    @Test
    @DisplayName("DFS-051.AC-14 An ID never issued is unknown to every operation")
    void unknownIdIsUnknownToEveryOperation() {
        Rig rig = new Rig();
        PlanExecutionId unknown = new PlanExecutionId(java.util.UUID.randomUUID());

        Result.Failure<?> cancel = failed(rig.service.cancel(unknown));
        Result.Failure<?> status = failed(rig.service.status(unknown));
        Result.Failure<?> result = failed(rig.service.result(unknown));

        for (Result.Failure<?> failure : List.of(cancel, status, result)) {
            assertEquals(ErrorCode.UNKNOWN_PLAN_EXECUTION, failure.code());
            assertTrue(failure.message().contains(unknown.value().toString()), failure.message());
        }
    }

    @Test
    @DisplayName("DFS-051.AC-14 The result is not available before the run ends")
    void resultIsNotAvailableBeforeTheRunEnds() throws Exception {
        Fx fx = basic();
        Rig rig = new Rig();
        PlanExecutionId id = rig.start(fx.scan(), fx.plan());

        Result.Failure<?> failure = failed(rig.service.result(id));

        assertEquals(ErrorCode.PLAN_EXECUTION_NOT_FINISHED, failure.code());
    }

    // ---- AC-15, AC-16 ----

    @Test
    @DisplayName("DFS-051.AC-15 A rejected task fails the run with a generic message")
    void rejectedTaskFailsTheRunWithAGenericMessage() throws Exception {
        Fx fx = basic();
        Rig rig = new Rig(task -> {
            throw new RejectedExecutionException("pool closed");
        });

        Result.Failure<?> failure = failed(rig.service.start(fx.scan(), fx.plan()));

        assertEquals(ErrorCode.EXECUTOR_REJECTED, failure.code());
        assertEquals(1, rig.store.created.size());
        PlanExecutionSnapshot snapshot = rig.status(rig.store.created.get(0));
        assertEquals(JobStatus.FAILED, snapshot.status());
        assertTrue(snapshot.failure().contains("pool closed"), snapshot.failure());
        for (String message : List.of(failure.message().toLowerCase(), snapshot.failure().toLowerCase())) {
            assertFalse(message.contains("plan verify"), message);
            assertFalse(message.contains("plan execution"), message);
        }
        assertTrue(Files.exists(fx.b()));
    }

    @Test
    @DisplayName("DFS-051.AC-16 An Error in the work fails the run and is rethrown")
    void errorInTheWorkFailsTheRunAndIsRethrown() throws Exception {
        Fx fx = basic();
        Rig rig = new Rig();
        rig.comparator.before = () -> {
            throw new OutOfMemoryError("boom");
        };

        PlanExecutionId id = rig.start(fx.scan(), fx.plan());
        Throwable thrown = rig.manual.runNext();

        assertInstanceOf(OutOfMemoryError.class, thrown);
        PlanExecutionSnapshot snapshot = rig.status(id);
        assertEquals(JobStatus.FAILED, snapshot.status());
        assertTrue(snapshot.failure().contains("OutOfMemoryError"), snapshot.failure());
        assertTrue(Files.exists(fx.b()));
    }

    // ---- AC-17 ----

    @Test
    @DisplayName("DFS-051.AC-17 Progress is pushed and pulled from the same events")
    void progressIsPushedAndPulledFromTheSameEvents() throws Exception {
        Fx fx = basic();
        Rig rig = new Rig();
        List<ExecutionEvent> events = new CopyOnWriteArrayList<>();
        List<Thread> threads = new CopyOnWriteArrayList<>();

        PlanExecutionId id = ok(rig.service.start(fx.scan(), fx.plan(), event -> {
            events.add(event);
            threads.add(Thread.currentThread());
        }));
        assertNull(rig.manual.runNext());

        List<ExecutionEvent.BytesCompared> compared = events.stream()
                .filter(e -> e instanceof ExecutionEvent.BytesCompared)
                .map(e -> (ExecutionEvent.BytesCompared) e).toList();
        assertFalse(compared.isEmpty());
        assertEquals(new ExecutionEvent.BytesCompared(27, 27), compared.get(compared.size() - 1));
        List<ExecutionEvent.OperationDone> done = events.stream()
                .filter(e -> e instanceof ExecutionEvent.OperationDone)
                .map(e -> (ExecutionEvent.OperationDone) e).toList();
        assertEquals(1, done.size());
        assertEquals(1, done.get(0).done());
        assertEquals(1, done.get(0).total());
        assertInstanceOf(OperationOutcome.Moved.class, done.get(0).outcome());
        for (Thread t : threads) {
            assertEquals(rig.manual.lastThread, t);
            assertNotEquals(Thread.currentThread(), t);
        }
        PlanExecutionSnapshot snapshot = rig.status(id);
        assertEquals(1, snapshot.operationsDone());
        assertEquals(1, snapshot.operationsTotal());
        assertEquals(27, snapshot.bytesDone());
        assertEquals(27, snapshot.bytesTotal());
        assertEquals(1, snapshot.filesMoved());
        assertEquals(27, snapshot.bytesMoved());
    }

    @Test
    @DisplayName("DFS-051.AC-17 A listener that throws is detached and the run continues")
    void listenerThatThrowsIsDetachedAndTheRunContinues() throws Exception {
        Path a = file("A/photo.jpg", PHOTO);
        Path b = file("B/photo.jpg", PHOTO);
        Path docA = file("A/doc.txt", "0123456789".getBytes(StandardCharsets.UTF_8));
        Path docC = file("C/doc.txt", "0123456789".getBytes(StandardCharsets.UTF_8));
        ScanResult scan = scan(set("d-photo", a, b), set("d-doc", docA, docC));
        DeletionPlan plan = plan(scan, rule("A", "B"), rule("A", "C"));
        Rig rig = new Rig();
        AtomicInteger received = new AtomicInteger();
        ExecutionListener listener = event -> {
            received.incrementAndGet();
            throw new IllegalStateException("listener boom");
        };

        PlanExecutionId id = ok(rig.service.start(scan, plan, listener));
        assertNull(rig.manual.runNext());

        assertEquals(1, received.get());
        assertEquals(JobStatus.COMPLETED, rig.status(id).status());
        assertFalse(Files.exists(b));
        assertFalse(Files.exists(docC));
        assertTrue(Files.exists(quarantined(id, b)));
        assertTrue(Files.exists(quarantined(id, docC)));
        assertTrue(rig.report(id).listenerFailure().contains("listener boom"), rig.report(id).listenerFailure());
        assertEquals(2, rig.status(id).operationsDone());
    }

    // ---- AC-18 ----

    @Test
    @DisplayName("DFS-051.AC-18 The report lists every outcome in plan order")
    void reportListsEveryOutcomeInPlanOrder() throws Exception {
        Path a = file("A/photo.jpg", PHOTO);
        Path b = file("B/photo.jpg", PHOTO);
        Path a1 = file("A/doc1.txt", "0123456789".getBytes(StandardCharsets.UTF_8));
        Path b1 = file("B/doc1.txt", "0123456789".getBytes(StandardCharsets.UTF_8));
        Path a2 = file("A/doc2.txt", "012345678901".getBytes(StandardCharsets.UTF_8));
        Path b2 = file("B/doc2.txt", "012345678901".getBytes(StandardCharsets.UTF_8));
        ScanResult scan = scan(set("d-photo", a, b), set("d-1", a1, b1), set("d-2", a2, b2));
        DeletionPlan plan = plan(scan, rule("A", "B"));
        assertEquals(3, plan.operations().size());
        // doc1: same size and time, different bytes; doc2: a different size
        Files.write(b1, "abcdefghij".getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(b1, FileTime.from(T0));
        Files.write(b2, "0123456789012".getBytes(StandardCharsets.UTF_8));
        Rig rig = new Rig();

        PlanExecutionId id = rig.run(scan, plan);

        PlanExecutionReport report = rig.report(id);
        assertEquals(3, report.outcomes().size());
        for (int i = 0; i < 3; i++) {
            Operation.Quarantine op = (Operation.Quarantine) plan.operations().get(i);
            OperationOutcome outcome = report.outcomes().get(i);
            assertEquals(op.keeper(), outcome.keeper());
            assertEquals(op.digest(), outcome.digest());
            switch (op.digest()) {
                case "d-photo" -> assertInstanceOf(OperationOutcome.Moved.class, outcome);
                case "d-1" -> assertEquals(SkipCause.DIFFERS,
                        assertInstanceOf(OperationOutcome.Skipped.class, outcome).skipped().get(0).cause());
                default -> assertEquals(SkipCause.SIZE_CHANGED,
                        assertInstanceOf(OperationOutcome.Skipped.class, outcome).skipped().get(0).cause());
            }
        }
        assertEquals(1, report.filesMoved());
        assertEquals(27, report.bytesMoved());
        assertEquals(2, report.filesSkipped());
        assertEquals(22, report.bytesSkipped());
    }

    // ---- AC-19 ----

    @Test
    @DisplayName("DFS-051.AC-19 An empty plan completes with an empty report")
    void emptyPlanCompletesWithAnEmptyReport() throws Exception {
        ScanResult scan = scan();
        DeletionPlan plan = new DeletionPlan(PlanIds.of(List.of(), List.of()), List.of(), List.of(),
                new PlanTotals(0, 0, 0), List.of());
        Rig rig = new Rig();

        PlanExecutionId id = rig.run(scan, plan);

        assertEquals(JobStatus.COMPLETED, rig.status(id).status());
        PlanExecutionReport report = rig.report(id);
        assertEquals(List.of(), report.outcomes());
        assertEquals(0, report.filesMoved());
        assertEquals(List.of("run", "end"), types(manifestOf(id)));
    }

    @Test
    @DisplayName("DFS-051.AC-19 A null argument is a caller bug")
    void nullArgumentIsACallerBug() throws Exception {
        Fx fx = basic();
        Rig rig = new Rig();

        try {
            rig.service.start(null, fx.plan());
            fail("expected NullPointerException for a null scan");
        } catch (NullPointerException expected) {
            // expected
        }
        try {
            rig.service.start(fx.scan(), null);
            fail("expected NullPointerException for a null plan");
        } catch (NullPointerException expected) {
            // expected
        }
        try {
            rig.service.start(fx.scan(), fx.plan(), null);
            fail("expected NullPointerException for a null listener");
        } catch (NullPointerException expected) {
            // expected
        }
    }

    // ---- AC-20 ----

    @Test
    @DisplayName("DFS-051.AC-20 The factory's use cases run a plan through the same ports")
    void factoryUseCasesRunAPlan() throws Exception {
        Fx fx = basic();
        ManualExecutor executor = new ManualExecutor();
        PlanExecutionUseCases useCases = ScannerFactories.planExecutionUseCases(executor,
                new ExecutionConfig(manifests(), List.of(tmp)));

        PlanExecutionId id = ok(useCases.start().start(fx.scan(), fx.plan()));
        assertEquals(JobStatus.QUEUED, ok(useCases.status().status(id)).status());
        assertNull(executor.runNext());

        assertEquals(JobStatus.COMPLETED, ok(useCases.status().status(id)).status());
        assertEquals(1, ok(useCases.result().result(id)).filesMoved());
        assertFalse(Files.exists(fx.b()));
        assertTrue(Files.exists(quarantined(id, fx.b())));
        assertEquals(JobStatus.COMPLETED, ok(useCases.cancel().cancel(id)).status());
        assertTrue(Files.exists(manifestOf(id)));
    }

    // ---- DFS-055: one plan execution at a time ----

    private static final byte[] TWO_MB = new byte[2 * 1024 * 1024];

    static {
        for (int i = 0; i < TWO_MB.length; i++) {
            TWO_MB[i] = (byte) (i * 31);
        }
    }

    private Path lockFile() {
        return manifests().resolve("execution.lock");
    }

    private DeletionPlan crosswise(Blocked bl) {
        return plan(bl.scan(), rule("B", "A"));
    }

    /** Starts the blocked rig's first run and waits until it is RUNNING, held in its first compare. */
    private PlanExecutionId runningFirst(Blocked bl) throws InterruptedException {
        PlanExecutionId id = bl.rig().start(bl.scan(), bl.plan());
        bl.rig().manual.startNext();
        assertTrue(bl.blocked().await(10, TimeUnit.SECONDS), "the comparator never started");
        assertEquals(JobStatus.RUNNING, bl.rig().status(id).status());
        return id;
    }

    private void finish(Blocked bl) throws InterruptedException {
        bl.release().countDown();
        Thread t = bl.rig().manual.lastThread;
        t.join(30_000);
        assertFalse(t.isAlive(), "task still running");
    }

    @Test
    @DisplayName("DFS-055.AC-1 Crosswise plans never quarantine both copies")
    void crosswisePlansNeverQuarantineBothCopies() throws Exception {
        Path a = file("A/x.jpg", TWO_MB);
        Path b = file("B/x.jpg", TWO_MB);
        ScanResult scan = scan(set("d-x", a, b));
        DeletionPlan keepAQuarantineB = plan(scan, rule("A", "B"));
        DeletionPlan keepBQuarantineA = plan(scan, rule("B", "A"));
        Rig rig = new Rig();
        List<Path> moves = new CopyOnWriteArrayList<>();
        rig.deleter.onMove = moves::add;
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        rig.comparator.before = () -> {
            blocked.countDown();
            try {
                if (!release.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("never released");
                }
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        };

        PlanExecutionId first = rig.start(scan, keepAQuarantineB);
        Thread task = rig.manual.startNext();
        assertTrue(blocked.await(10, TimeUnit.SECONDS), "the comparator never started");
        assertEquals(JobStatus.RUNNING, rig.status(first).status());

        Result.Failure<?> refused = failed(rig.service.start(scan, keepBQuarantineA));

        assertEquals(ErrorCode.EXECUTION_ALREADY_RUNNING, refused.code());
        assertEquals(0, rig.manual.pending());
        assertEquals(List.of(), moves);
        assertTrue(Files.exists(a) && Files.exists(b));
        release.countDown();
        task.join(30_000);
        assertFalse(task.isAlive());
        assertEquals(JobStatus.COMPLETED, rig.status(first).status());
        assertTrue(Files.exists(a) || Files.exists(b), "no copy left outside quarantine");
        assertEquals(List.of(b), moves);
    }

    @Test
    @DisplayName("DFS-055.AC-2 A second start is refused while the first is unfinished")
    void secondStartIsRefusedWhileTheFirstIsUnfinished() throws Exception {
        Blocked bl = blockedRig(false);
        Rig rig = bl.rig();
        PlanExecutionId e1 = runningFirst(bl);
        List<Path> moves = new CopyOnWriteArrayList<>();
        rig.deleter.onMove = moves::add;

        Result.Failure<?> refused = failed(rig.service.start(bl.scan(), crosswise(bl)));

        assertEquals(ErrorCode.EXECUTION_ALREADY_RUNNING, refused.code());
        assertTrue(refused.message().contains(e1.value().toString()), refused.message());
        assertEquals(List.of(e1), rig.store.created);
        assertEquals(0, rig.manual.pending());
        assertEquals(List.of(), moves);
        finish(bl);
    }

    @Test
    @DisplayName("DFS-055.AC-2 A second start is also refused while the first is queued or cancelling")
    void secondStartIsRefusedWhileTheFirstIsQueuedOrCancelling() throws Exception {
        Blocked bl = blockedRig(false);
        Rig rig = bl.rig();
        PlanExecutionId e1 = rig.start(bl.scan(), bl.plan());
        assertEquals(JobStatus.QUEUED, rig.status(e1).status());

        Result.Failure<?> whileQueued = failed(rig.service.start(bl.scan(), crosswise(bl)));

        assertEquals(ErrorCode.EXECUTION_ALREADY_RUNNING, whileQueued.code());
        assertTrue(whileQueued.message().contains(e1.value().toString()), whileQueued.message());
        rig.manual.startNext();
        assertTrue(bl.blocked().await(10, TimeUnit.SECONDS), "the comparator never started");
        assertEquals(JobStatus.CANCELLING, ok(rig.service.cancel(e1)).status());

        Result.Failure<?> whileCancelling = failed(rig.service.start(bl.scan(), crosswise(bl)));

        assertEquals(ErrorCode.EXECUTION_ALREADY_RUNNING, whileCancelling.code());
        assertEquals(List.of(e1), rig.store.created);
        finish(bl);
    }

    @Test
    @DisplayName("DFS-055.AC-2 A start right after the first is recorded COMPLETED succeeds")
    void startAfterCompletedSucceeds() throws Exception {
        Fx fx = basic();
        Rig rig = new Rig();
        PlanExecutionId e1 = rig.run(fx.scan(), fx.plan());
        assertEquals(JobStatus.COMPLETED, rig.status(e1).status());

        PlanExecutionId e2 = rig.start(fx.scan(), plan(fx.scan(), rule("B", "A")));

        assertNotEquals(e1, e2);
        assertEquals(List.of(e1, e2), rig.store.created);
    }

    @Test
    @DisplayName("DFS-055.AC-2 A start right after the first is recorded FAILED succeeds")
    void startAfterFailedSucceeds() throws Exception {
        Fx fx = basic();
        Rig rig = new Rig();
        rig.manifestWriter.failOn = line -> line instanceof ManifestLine.Moving;
        PlanExecutionId e1 = rig.start(fx.scan(), fx.plan());
        rig.manual.runNext();
        assertEquals(JobStatus.FAILED, rig.status(e1).status());

        PlanExecutionId e2 = rig.start(fx.scan(), plan(fx.scan(), rule("B", "A")));

        assertNotEquals(e1, e2);
    }

    @Test
    @DisplayName("DFS-055.AC-2 A start right after the first is recorded CANCELLED succeeds")
    void startAfterCancelledSucceeds() throws Exception {
        Blocked bl = blockedRig(false);
        Rig rig = bl.rig();
        PlanExecutionId e1 = runningFirst(bl);
        ok(rig.service.cancel(e1));
        finish(bl);
        assertEquals(JobStatus.CANCELLED, rig.status(e1).status());

        PlanExecutionId e2 = rig.start(bl.scan(), crosswise(bl));

        assertNotEquals(e1, e2);
    }

    @Test
    @DisplayName("DFS-055.AC-2 A run cancelled while queued frees the slot")
    void runCancelledWhileQueuedFreesTheSlot() throws Exception {
        Fx fx = basic();
        Rig rig = new Rig();
        PlanExecutionId e1 = rig.start(fx.scan(), fx.plan());
        assertEquals(JobStatus.CANCELLED, ok(rig.service.cancel(e1)).status());

        PlanExecutionId e2 = rig.start(fx.scan(), plan(fx.scan(), rule("B", "A")));

        assertNotEquals(e1, e2);
    }

    @Test
    @DisplayName("DFS-055.AC-2 A run the executor rejected frees the slot")
    void runTheExecutorRejectedFreesTheSlot() throws Exception {
        Fx fx = basic();
        ManualExecutor queue = new ManualExecutor();
        AtomicBoolean first = new AtomicBoolean(true);
        Rig rig = new Rig(task -> {
            if (first.getAndSet(false)) {
                throw new RejectedExecutionException("pool closed");
            }
            queue.execute(task);
        });

        Result.Failure<?> rejected = failed(rig.service.start(fx.scan(), fx.plan()));
        assertEquals(ErrorCode.EXECUTOR_REJECTED, rejected.code());

        PlanExecutionId e2 = rig.start(fx.scan(), plan(fx.scan(), rule("B", "A")));

        assertEquals(2, rig.store.created.size());
        assertEquals(e2, rig.store.created.get(1));
        assertEquals(1, queue.pending());
    }

    @Test
    @DisplayName("DFS-055.AC-3 Another process is executing")
    void anotherProcessIsExecuting() throws Exception {
        Fx fx = basic();
        Rig rig = new Rig();
        Files.createDirectories(manifests());

        try (ExecutionLockProbe.Holder other = ExecutionLockProbe.hold(lockFile())) {
            assertEquals("LOCKED", other.firstLine());

            Result.Failure<?> refused = failed(rig.service.start(fx.scan(), fx.plan()));

            assertEquals(ErrorCode.EXECUTION_ALREADY_RUNNING, refused.code());
            assertEquals("another process is executing a plan", refused.message());
            assertEquals(List.of(), rig.store.created);
            assertEquals(0, rig.manual.pending());
            assertTrue(Files.exists(fx.a()) && Files.exists(fx.b()));
            assertEquals(List.of(), allQuarantinedFiles());
        }

        PlanExecutionId id = rig.start(fx.scan(), fx.plan());
        assertEquals(JobStatus.QUEUED, rig.status(id).status());
    }

    @Test
    @DisplayName("DFS-055.AC-3 The lock is held exactly while the execution is unfinished")
    void lockIsHeldExactlyWhileTheExecutionIsUnfinished() throws Exception {
        Blocked bl = blockedRig(false);
        runningFirst(bl);

        assertEquals("HELD", ExecutionLockProbe.tryLock(lockFile()));

        finish(bl);
        assertEquals("FREE", ExecutionLockProbe.tryLock(lockFile()));
    }

    enum Ending {
        COMPLETED, CANCELLED, FAILED
    }

    @ParameterizedTest(name = "DFS-055.AC-3 The lock is held from the start until the run is recorded {0}")
    @DisplayName("DFS-055.AC-3 The lock is held from the start until the run is recorded terminal")
    @EnumSource(Ending.class)
    void lockIsHeldUntilTheRunIsRecordedTerminal(Ending ending) throws Exception {
        CountingLock lock = new CountingLock();
        Blocked bl = blockedRig(false, lock);
        Rig rig = bl.rig();
        PlanExecutionId id = rig.start(bl.scan(), bl.plan());
        assertEquals(1, lock.live.get(), "held from the start, before the run is picked up");
        rig.manual.startNext();
        assertTrue(bl.blocked().await(10, TimeUnit.SECONDS), "the comparator never started");
        assertEquals(1, lock.live.get());
        switch (ending) {
            case CANCELLED -> ok(rig.service.cancel(id));
            case FAILED -> rig.manifestWriter.failOn = line -> line instanceof ManifestLine.Moving;
            default -> {
            }
        }

        finish(bl);

        JobStatus expected = switch (ending) {
            case COMPLETED -> JobStatus.COMPLETED;
            case CANCELLED -> JobStatus.CANCELLED;
            case FAILED -> JobStatus.FAILED;
        };
        assertEquals(expected, rig.status(id).status());
        assertEquals(0, lock.live.get());
        assertEquals(1, lock.acquires.get());
    }

    @Test
    @DisplayName("DFS-055.AC-3 A lock held elsewhere refuses the start with the exact message")
    void lockHeldElsewhereRefusesTheStart() throws Exception {
        Fx fx = basic();
        CountingLock lock = new CountingLock();
        lock.heldElsewhere = true;
        Rig rig = new Rig(null, lock);

        Result.Failure<?> refused = failed(rig.service.start(fx.scan(), fx.plan()));

        assertEquals(ErrorCode.EXECUTION_ALREADY_RUNNING, refused.code());
        assertEquals("another process is executing a plan", refused.message());
        assertEquals(List.of(), rig.store.created);
        assertEquals(0, rig.manual.pending());
        assertEquals(0, lock.live.get());
    }

    @Test
    @DisplayName("DFS-055.AC-4 The plan's own problems are reported first")
    void thePlansOwnProblemsAreReportedFirst() throws Exception {
        CountingLock lock = new CountingLock();
        Blocked bl = blockedRig(false, lock);
        PlanExecutionId e1 = runningFirst(bl);
        DeletionPlan conflicted = plan(bl.scan(), rule("A", "B"), rule("B", "A"));
        assertFalse(conflicted.conflicts().isEmpty());

        Result.Failure<?> failure = failed(bl.rig().service.start(bl.scan(), conflicted));

        assertEquals(ErrorCode.PLAN_HAS_CONFLICTS, failure.code());
        assertEquals(List.of(e1), bl.rig().store.created);
        assertEquals(1, lock.acquires.get(), "the lock is not touched for a plan that is refused");
        finish(bl);
    }

    @Test
    @DisplayName("DFS-055.AC-4 The plan is checked before the lock, even when the lock is held elsewhere")
    void thePlanIsCheckedBeforeTheLock() throws Exception {
        Fx fx = basic();
        DeletionPlan conflicted = plan(fx.scan(), rule("A", "B"), rule("B", "A"));
        CountingLock lock = new CountingLock();
        lock.heldElsewhere = true;
        Rig rig = new Rig(null, lock);
        // a sound plan does reach the lock, which is held elsewhere
        assertEquals(ErrorCode.EXECUTION_ALREADY_RUNNING, failed(rig.service.start(fx.scan(), fx.plan())).code());
        assertEquals(1, lock.acquires.get());

        Result.Failure<?> failure = failed(rig.service.start(fx.scan(), conflicted));

        assertEquals(ErrorCode.PLAN_HAS_CONFLICTS, failure.code());
        assertEquals(1, lock.acquires.get(), "the lock is not touched for a plan that is refused");
        assertEquals(List.of(), rig.store.created);
    }

    @Test
    @DisplayName("DFS-055.AC-4 A lock that cannot be taken refuses the start and leaves no trace")
    void lockThatCannotBeTakenLeavesNoTrace() throws Exception {
        Fx fx = basic();
        CountingLock lock = new CountingLock();
        lock.failure = new IOException("read-only volume");
        Rig rig = new Rig(null, lock);

        Result.Failure<?> failure = failed(rig.service.start(fx.scan(), fx.plan()));

        assertEquals(ErrorCode.MANIFEST_UNWRITABLE, failure.code());
        assertTrue(failure.message().contains("read-only volume"), failure.message());
        assertEquals(List.of(), rig.store.created);
        assertEquals(0, rig.manual.pending());
        assertEquals(0, lock.live.get());
        assertTrue(Files.exists(fx.a()) && Files.exists(fx.b()));
    }

    @Test
    @DisplayName("DFS-055.AC-4 Two simultaneous starts, one winner")
    void twoSimultaneousStartsOneWinner() throws Exception {
        Fx fx = basic();
        DeletionPlan other = plan(fx.scan(), rule("B", "A"));
        for (int round = 0; round < 20; round++) {
            CountingLock lock = new CountingLock();
            Rig rig = new Rig(null, lock);
            CountDownLatch go = new CountDownLatch(1);
            AtomicReference<Result<PlanExecutionId>> r1 = new AtomicReference<>();
            AtomicReference<Result<PlanExecutionId>> r2 = new AtomicReference<>();
            AtomicReference<Throwable> thrown = new AtomicReference<>();
            Thread t1 = startOnLatch(rig, fx.scan(), fx.plan(), go, r1, thrown);
            Thread t2 = startOnLatch(rig, fx.scan(), other, go, r2, thrown);

            go.countDown();
            t1.join(30_000);
            t2.join(30_000);

            assertFalse(t1.isAlive() || t2.isAlive());
            if (thrown.get() instanceof RuntimeException e) {
                throw e;
            }
            if (thrown.get() != null) {
                throw new AssertionError(thrown.get());
            }
            List<Result<PlanExecutionId>> results = List.of(r1.get(), r2.get());
            long wins = results.stream().filter(r -> r instanceof Result.Success).count();
            assertEquals(1, wins, results.toString());
            for (Result<PlanExecutionId> r : results) {
                if (r instanceof Result.Failure<PlanExecutionId> f) {
                    assertEquals(ErrorCode.EXECUTION_ALREADY_RUNNING, f.code());
                }
            }
            assertEquals(1, rig.store.created.size());
            assertEquals(1, lock.live.get(), "only the winner still holds the lock");
        }
    }

    private static Thread startOnLatch(Rig rig, ScanResult scan, DeletionPlan plan, CountDownLatch go,
            AtomicReference<Result<PlanExecutionId>> result, AtomicReference<Throwable> thrown) {
        Thread t = new Thread(() -> {
            try {
                go.await();
                result.set(rig.service.start(scan, plan));
            } catch (Throwable e) {
                thrown.compareAndSet(null, e);
            }
        });
        t.start();
        return t;
    }

    @Test
    @DisplayName("DFS-055.AC-4 A refused start leaves the lock held only on behalf of the first run")
    void refusedStartLeavesTheLockWithTheFirstRun() throws Exception {
        CountingLock lock = new CountingLock();
        Blocked bl = blockedRig(false, lock);
        runningFirst(bl);
        assertEquals(1, lock.live.get());

        Result.Failure<?> refused = failed(bl.rig().service.start(bl.scan(), crosswise(bl)));

        assertEquals(ErrorCode.EXECUTION_ALREADY_RUNNING, refused.code());
        assertEquals(2, lock.acquires.get());
        assertEquals(1, lock.live.get());
        finish(bl);
        assertEquals(0, lock.live.get());
    }

    @Test
    @DisplayName("DFS-055.AC-4 A run cancelled while queued releases the lock")
    void runCancelledWhileQueuedReleasesTheLock() throws Exception {
        Fx fx = basic();
        Rig rig = new Rig();
        rig.run(fx.scan(), fx.plan());

        PlanExecutionId e2 = rig.start(fx.scan(), plan(fx.scan(), rule("B", "A")));
        assertEquals("HELD", ExecutionLockProbe.tryLock(lockFile()));
        assertEquals(JobStatus.CANCELLED, ok(rig.service.cancel(e2)).status());

        assertEquals("FREE", ExecutionLockProbe.tryLock(lockFile()));
    }

    @Test
    @DisplayName("DFS-055.AC-4 A run the executor rejected releases the lock")
    void runTheExecutorRejectedReleasesTheLock() throws Exception {
        Fx fx = basic();
        CountingLock lock = new CountingLock();
        Rig rig = new Rig(task -> {
            throw new RejectedExecutionException("pool closed");
        }, lock);

        Result.Failure<?> failure = failed(rig.service.start(fx.scan(), fx.plan()));

        assertEquals(ErrorCode.EXECUTOR_REJECTED, failure.code());
        assertEquals(1, lock.acquires.get());
        assertEquals(0, lock.live.get());
    }
}
