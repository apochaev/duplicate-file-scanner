package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import us.pochaev.dfs.scanner.domain.factory.PlanVerifyUseCases;
import us.pochaev.dfs.scanner.domain.factory.ScannerFactories;
import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.ErrorCode;
import us.pochaev.dfs.scanner.domain.model.JobStatus;
import us.pochaev.dfs.scanner.domain.model.KeepAndQuarantine;
import us.pochaev.dfs.scanner.domain.model.Operation;
import us.pochaev.dfs.scanner.domain.model.OperationCheck;
import us.pochaev.dfs.scanner.domain.model.OperationCheck.Outcome;
import us.pochaev.dfs.scanner.domain.model.PlanTotals;
import us.pochaev.dfs.scanner.domain.model.PlanVerifyId;
import us.pochaev.dfs.scanner.domain.model.PlanVerifySnapshot;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.Source;
import us.pochaev.dfs.scanner.domain.model.VerifyEvent;
import us.pochaev.dfs.scanner.domain.model.VerifyListener;
import us.pochaev.dfs.scanner.domain.model.VerifyReport;
import us.pochaev.dfs.scanner.infra.adapter.InMemoryJobStore;
import us.pochaev.dfs.scanner.infra.port.ContentComparator;
import us.pochaev.dfs.scanner.infra.port.FileAttributesReader;
import us.pochaev.dfs.scanner.infra.port.JobStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Asynchronous plan verify jobs (DFS-042): fakes, a manual executor, no sleeping. */
class PlanVerifyServiceTest {

    private static final long CHUNK = 1024 * 1024;
    private static final long Y_SIZE = 3L * 1024 * 1024;
    private static final long TOTAL = Y_SIZE + 5;
    private static final String ID_A = "a".repeat(64);
    private static final String ID_B = "b".repeat(64);
    private static final KeepAndQuarantine RULE = new KeepAndQuarantine(Path.of("A"), Path.of("B"));

    // ---- builders ----

    private static Operation.Quarantine opX() {
        return new Operation.Quarantine("d1", 5, List.of(Path.of("B/x.jpg")), Path.of("A/x.jpg"),
                new Source.FromRule(0, RULE));
    }

    private static Operation.Quarantine opY() {
        return new Operation.Quarantine("d2", Y_SIZE, List.of(Path.of("B/y.bin")), Path.of("A/y.bin"),
                new Source.FromRule(0, RULE));
    }

    private static DeletionPlan plan(String id, Operation... ops) {
        long bytes = 0;
        for (Operation o : ops) {
            bytes += ((Operation.Quarantine) o).size();
        }
        return new DeletionPlan(id, List.of(ops), List.of(), new PlanTotals(ops.length, bytes, ops.length),
                List.of());
    }

    private static DeletionPlan fullPlan() {
        return plan(ID_A, opX(), opY());
    }

    private static OperationCheck identical(Operation.Quarantine op) {
        return new OperationCheck(op.digest(), op.links().get(0), op.keeper(), Outcome.IDENTICAL, "");
    }

    @SuppressWarnings("unchecked")
    private static <T> T ok(Result<T> result) {
        return ((Result.Success<T>) assertInstanceOf(Result.Success.class, result, String.valueOf(result))).value();
    }

    @SuppressWarnings("unchecked")
    private static Result.Failure<?> failed(Result<?> result) {
        return assertInstanceOf(Result.Failure.class, result, String.valueOf(result));
    }

    // ---- fakes ----

    /** Queues tasks until the test runs them. */
    static final class ManualExecutor implements Executor {
        private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        int pending() {
            return tasks.size();
        }

        void runNext() {
            Runnable r = tasks.poll();
            if (r == null) {
                throw new IllegalStateException("no queued task");
            }
            r.run();
        }
    }

    /** Regular files with the plan's sizes; counts its calls. */
    static final class FakeReader implements FileAttributesReader {
        final AtomicInteger calls = new AtomicInteger();
        private final Map<Path, Long> sizes = Map.of(
                Path.of("B/x.jpg"), 5L, Path.of("A/x.jpg"), 5L,
                Path.of("B/y.bin"), Y_SIZE, Path.of("A/y.bin"), Y_SIZE);

        long sizeOf(Path p) {
            return sizes.get(p);
        }

        @Override
        public BasicFileAttributes read(Path path) throws IOException {
            calls.incrementAndGet();
            Long size = sizes.get(path);
            if (size == null) {
                throw new NoSuchFileException(path.toString());
            }
            return new BasicFileAttributes() {
                public FileTime lastModifiedTime() {
                    return FileTime.fromMillis(0);
                }

                public FileTime lastAccessTime() {
                    return FileTime.fromMillis(0);
                }

                public FileTime creationTime() {
                    return FileTime.fromMillis(0);
                }

                public boolean isRegularFile() {
                    return true;
                }

                public boolean isDirectory() {
                    return false;
                }

                public boolean isSymbolicLink() {
                    return false;
                }

                public boolean isOther() {
                    return false;
                }

                public long size() {
                    return size;
                }

                public Object fileKey() {
                    return null;
                }
            };
        }
    }

    /** Compares in 1 MiB chunks, reading the cancel signal before each, and can block, throw or call back. */
    static final class FakeComparator implements ContentComparator {
        final FakeReader reader;
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger yChunks = new AtomicInteger();
        final CountDownLatch blocked = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        volatile boolean blockOnY;
        volatile boolean throwOnY;
        volatile Runnable afterLastChunkOfY;

        FakeComparator(FakeReader reader) {
            this.reader = reader;
        }

        @Override
        public Comparison compare(Path candidate, Path keeper, LongConsumer bytesCompared, BooleanSupplier cancelled) {
            calls.incrementAndGet();
            boolean isY = candidate.getFileName().toString().equals("y.bin");
            if (isY && throwOnY) {
                throw new IllegalStateException("boom");
            }
            long size = reader.sizeOf(candidate);
            long offset = 0;
            while (offset < size) {
                if (cancelled.getAsBoolean()) {
                    return new Comparison.Cancelled();
                }
                if (isY) {
                    yChunks.incrementAndGet();
                }
                offset += Math.min(CHUNK, size - offset);
                bytesCompared.accept(offset);
                if (isY && blockOnY && offset == CHUNK) {
                    blocked.countDown();
                    try {
                        if (!release.await(30, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("never released");
                        }
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
            if (isY && afterLastChunkOfY != null) {
                afterLastChunkOfY.run();
            }
            return new Comparison.Identical();
        }
    }

    /** Delegates to the in-memory store and remembers every ID created, so a test can see what was recorded. */
    static final class RecordingStore<I, R> implements JobStore<I, R> {
        final InMemoryJobStore<I, R> inner = new InMemoryJobStore<>();
        final List<I> created = new CopyOnWriteArrayList<>();

        @Override
        public void create(I id, R record) {
            inner.create(id, record);
            created.add(id);
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

    /** Collects every event, on whichever thread delivers it. */
    static final class RecordingListener implements VerifyListener {
        final List<VerifyEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void onEvent(VerifyEvent event) {
            events.add(event);
        }

        List<VerifyEvent.BytesCompared> bytes() {
            return events.stream().filter(VerifyEvent.BytesCompared.class::isInstance)
                    .map(VerifyEvent.BytesCompared.class::cast).toList();
        }

        List<VerifyEvent.EntryChecked> entries() {
            return events.stream().filter(VerifyEvent.EntryChecked.class::isInstance)
                    .map(VerifyEvent.EntryChecked.class::cast).toList();
        }
    }

    /** One service on fakes, with the manual executor. */
    static final class Rig {
        final ManualExecutor executor = new ManualExecutor();
        final FakeReader reader = new FakeReader();
        final FakeComparator comparator = new FakeComparator(reader);
        final RecordingStore<PlanVerifyId, Job<PlanVerifyProgress, VerifyReport>> store = new RecordingStore<>();
        final PlanVerifyService service = new PlanVerifyService(
                new VerifyPlanContentService(reader, comparator), store, executor);
        Thread running;

        PlanVerifyId start(DeletionPlan plan) {
            return ok(service.start(plan));
        }

        /** Runs the next task on a virtual thread and waits until the comparator is blocked in B/y.bin. */
        void runBlocked() throws InterruptedException {
            running = Thread.ofVirtual().start(executor::runNext);
            assertTrue(comparator.blocked.await(10, TimeUnit.SECONDS), "comparator never blocked");
        }

        void finish() throws InterruptedException {
            comparator.release.countDown();
            if (running != null) {
                running.join(10_000);
                assertTrue(!running.isAlive(), "task thread did not end");
            }
        }

        PlanVerifySnapshot status(PlanVerifyId id) {
            return ok(service.status(id));
        }

        VerifyReport report(PlanVerifyId id) {
            return ok(service.result(id));
        }

        /** Brings a new job (on the full plan) into {@code wanted}; call {@link #finish} afterwards. */
        PlanVerifyId jobIn(JobStatus wanted) throws InterruptedException {
            switch (wanted) {
                case QUEUED -> {
                    return start(fullPlan());
                }
                case RUNNING -> {
                    comparator.blockOnY = true;
                    PlanVerifyId id = start(fullPlan());
                    runBlocked();
                    return id;
                }
                case CANCELLING -> {
                    PlanVerifyId id = jobIn(JobStatus.RUNNING);
                    assertEquals(JobStatus.CANCELLING, ok(service.cancel(id)).status());
                    return id;
                }
                case COMPLETED -> {
                    PlanVerifyId id = start(fullPlan());
                    executor.runNext();
                    return id;
                }
                case CANCELLED -> {
                    PlanVerifyId id = start(fullPlan());
                    assertEquals(JobStatus.CANCELLED, ok(service.cancel(id)).status());
                    return id;
                }
                case FAILED -> {
                    comparator.throwOnY = true;
                    PlanVerifyId id = start(fullPlan());
                    executor.runNext();
                    return id;
                }
                default -> throw new IllegalArgumentException(wanted.toString());
            }
        }
    }

    // ---- AC-1, AC-2 ----

    @Test
    @DisplayName("DFS-042.AC-1, DFS-042.AC-2 A job is queued, runs and completes with the verify's report")
    void jobIsQueuedRunsAndCompletes() {
        Rig rig = new Rig();
        DeletionPlan plan = fullPlan();

        PlanVerifyId id = rig.start(plan);

        assertEquals(1, rig.executor.pending());
        assertEquals(0, rig.comparator.calls.get());
        PlanVerifySnapshot queued = rig.status(id);
        assertEquals(new PlanVerifySnapshot(JobStatus.QUEUED, ID_A, 0, 2, 0, TOTAL, ""), queued);
        assertEquals(2, queued.entriesTotal());
        assertEquals(3145733L, queued.bytesTotal());
        assertEquals(ErrorCode.PLAN_VERIFY_NOT_FINISHED, failed(rig.service.result(id)).code());

        rig.executor.runNext();

        assertEquals(new PlanVerifySnapshot(JobStatus.COMPLETED, ID_A, 2, 2, TOTAL, TOTAL, ""), rig.status(id));
        VerifyReport report = rig.report(id);
        assertEquals(List.of(identical(opX()), identical(opY())), report.checks());
        assertEquals(false, report.cancelled());
        Rig other = new Rig();
        VerifyReport synchronous = ok(new VerifyPlanContentService(other.reader, other.comparator)
                .verify(plan, e -> { }, () -> false));
        assertEquals(synchronous, report);
    }

    // ---- AC-1, AC-3 ----

    @Test
    @DisplayName("DFS-042.AC-1, DFS-042.AC-3 Status and listener follow the verify while it runs")
    void statusAndListenerFollowTheVerify() throws InterruptedException {
        Rig rig = new Rig();
        rig.comparator.blockOnY = true;
        RecordingListener listener = new RecordingListener();
        PlanVerifyId id = ok(rig.service.start(fullPlan(), listener));
        try {
            rig.runBlocked();

            PlanVerifySnapshot running = rig.status(id);
            assertEquals(JobStatus.RUNNING, running.status());
            assertEquals(1, running.entriesDone());
            assertEquals(1048581L, running.bytesDone());
            assertEquals(1, listener.entries().size());
            assertEquals(identical(opX()), listener.entries().get(0).check());
            assertEquals(1, listener.entries().get(0).done());
            assertEquals(2, listener.entries().get(0).total());
        } finally {
            rig.finish();
        }

        List<VerifyEvent.BytesCompared> bytes = listener.bytes();
        long previous = 0;
        for (VerifyEvent.BytesCompared b : bytes) {
            assertTrue(b.bytesDone() >= previous);
            assertEquals(TOTAL, b.bytesTotal());
            previous = b.bytesDone();
        }
        assertEquals(TOTAL, bytes.get(bytes.size() - 1).bytesDone());
        assertEquals(2, listener.entries().size());
        assertEquals(2, listener.entries().get(1).done());
        assertEquals(2, listener.entries().get(1).total());
        assertEquals(identical(opY()), listener.entries().get(1).check());
        assertEquals(JobStatus.COMPLETED, rig.status(id).status());
        assertNotSame(Thread.currentThread(), rig.running);
    }

    // ---- AC-4 ----

    @Test
    @DisplayName("DFS-042.AC-4 Cancelling a queued job")
    void cancellingAQueuedJob() {
        Rig rig = new Rig();
        PlanVerifyId id = rig.start(fullPlan());

        PlanVerifySnapshot cancelled = ok(rig.service.cancel(id));

        assertEquals(JobStatus.CANCELLED, cancelled.status());
        rig.executor.runNext();
        assertEquals(0, rig.comparator.calls.get());
        assertEquals(0, rig.reader.calls.get());
        assertEquals(JobStatus.CANCELLED, rig.status(id).status());
        assertEquals(new VerifyReport(ID_A, List.of(), true, ""), rig.report(id));
    }

    // ---- AC-5 ----

    @Test
    @DisplayName("DFS-042.AC-5 Cancelling a running job stops within one chunk")
    void cancellingARunningJobStopsWithinOneChunk() throws InterruptedException {
        Rig rig = new Rig();
        try {
            PlanVerifyId id = rig.jobIn(JobStatus.RUNNING);

            PlanVerifySnapshot snapshot = ok(rig.service.cancel(id));
            assertEquals(JobStatus.CANCELLING, snapshot.status());
            assertEquals(JobStatus.CANCELLING, rig.status(id).status());
            assertEquals(ErrorCode.PLAN_VERIFY_NOT_FINISHED, failed(rig.service.result(id)).code());

            rig.finish();

            assertEquals(1, rig.comparator.yChunks.get());
            assertEquals(JobStatus.CANCELLED, rig.status(id).status());
            VerifyReport report = rig.report(id);
            assertTrue(report.cancelled());
            assertEquals(List.of(identical(opX())), report.checks());
        } finally {
            rig.finish();
        }
    }

    // ---- AC-6 ----

    @ParameterizedTest(name = "DFS-042.AC-6 Cancel on a {0} job changes nothing")
    @EnumSource(value = JobStatus.class, names = {"CANCELLING", "COMPLETED", "CANCELLED", "FAILED"})
    @DisplayName("DFS-042.AC-6 Cancel on a job that is cancelling or finished changes nothing")
    void cancelOnCancellingOrFinishedChangesNothing(JobStatus status) throws InterruptedException {
        Rig rig = new Rig();
        try {
            PlanVerifyId id = rig.jobIn(status);
            PlanVerifySnapshot before = rig.status(id);
            assertEquals(status, before.status());

            PlanVerifySnapshot returned = ok(rig.service.cancel(id));

            assertEquals(before, returned);
            assertEquals(before, rig.status(id));
        } finally {
            rig.finish();
        }
    }

    @Test
    @DisplayName("DFS-042.AC-6 A completion recorded before the cancel wins")
    void completionBeforeCancelWins() {
        Rig rig = new Rig();
        PlanVerifyId id = rig.start(fullPlan());
        rig.executor.runNext();

        PlanVerifySnapshot returned = ok(rig.service.cancel(id));

        assertEquals(JobStatus.COMPLETED, returned.status());
        assertEquals(JobStatus.COMPLETED, rig.status(id).status());
        assertEquals(false, rig.report(id).cancelled());
        assertEquals(2, rig.report(id).checks().size());
    }

    @Test
    @DisplayName("DFS-042.AC-6 A cancel recorded before the completion wins")
    void cancelBeforeCompletionWins() {
        Rig rig = new Rig();
        PlanVerifyId[] holder = new PlanVerifyId[1];
        PlanVerifySnapshot[] fromCancel = new PlanVerifySnapshot[1];
        rig.comparator.afterLastChunkOfY = () -> fromCancel[0] = ok(rig.service.cancel(holder[0]));
        PlanVerifyId id = rig.start(fullPlan());
        holder[0] = id;

        rig.executor.runNext();

        assertEquals(JobStatus.CANCELLING, fromCancel[0].status());
        assertEquals(JobStatus.CANCELLED, rig.status(id).status());
        VerifyReport report = rig.report(id);
        assertTrue(report.cancelled());
        assertEquals(List.of(identical(opX()), identical(opY())), report.checks());
    }

    // ---- AC-7 ----

    @ParameterizedTest(name = "DFS-042.AC-7 The result of a {0} job is refused")
    @EnumSource(value = JobStatus.class, names = {"QUEUED", "RUNNING", "CANCELLING", "FAILED"})
    @DisplayName("DFS-042.AC-7 The result is refused until the job has finished")
    void resultIsRefusedUntilFinished(JobStatus status) throws InterruptedException {
        Rig rig = new Rig();
        try {
            PlanVerifyId id = rig.jobIn(status);

            Result.Failure<?> failure = failed(rig.service.result(id));

            assertEquals(ErrorCode.PLAN_VERIFY_NOT_FINISHED, failure.code());
            assertTrue(failure.message().contains(status.name()), failure.message());
        } finally {
            rig.finish();
        }
    }

    // ---- AC-8 ----

    @Test
    @DisplayName("DFS-042.AC-8 An ID that was never issued")
    void unknownId() {
        Rig rig = new Rig();
        PlanVerifyId unknown = PlanVerifyId.random();
        String text = unknown.value().toString();

        Result.Failure<?> cancel = failed(rig.service.cancel(unknown));
        Result.Failure<?> status = failed(rig.service.status(unknown));
        Result.Failure<?> result = failed(rig.service.result(unknown));

        for (Result.Failure<?> f : List.of(cancel, status, result)) {
            assertEquals(ErrorCode.UNKNOWN_PLAN_VERIFY, f.code());
            assertTrue(f.message().contains(text), f.message());
        }
    }

    // ---- AC-9 ----

    @Test
    @DisplayName("DFS-042.AC-9 The executor rejects the task")
    void executorRejects() {
        RecordingStore<PlanVerifyId, Job<PlanVerifyProgress, VerifyReport>> store = new RecordingStore<>();
        FakeReader reader = new FakeReader();
        PlanVerifyService service = new PlanVerifyService(
                new VerifyPlanContentService(reader, new FakeComparator(reader)), store, task -> {
                    throw new RejectedExecutionException("pool closed");
                });

        Result<PlanVerifyId> result = service.start(fullPlan());

        assertEquals(ErrorCode.EXECUTOR_REJECTED, failed(result).code());
        assertEquals(1, store.created.size());
        PlanVerifySnapshot snapshot = ok(service.status(store.created.get(0)));
        assertEquals(JobStatus.FAILED, snapshot.status());
        assertTrue(snapshot.failure().contains("pool closed"), snapshot.failure());
    }

    @Test
    @DisplayName("DFS-051.AC-15 The shared rejection message names no particular use case")
    void rejectionMessageNamesNoUseCase() {
        RecordingStore<PlanVerifyId, Job<PlanVerifyProgress, VerifyReport>> store = new RecordingStore<>();
        FakeReader reader = new FakeReader();
        PlanVerifyService service = new PlanVerifyService(
                new VerifyPlanContentService(reader, new FakeComparator(reader)), store, task -> {
                    throw new RejectedExecutionException("pool closed");
                });

        Result.Failure<?> failure = failed(service.start(fullPlan()));

        assertEquals(ErrorCode.EXECUTOR_REJECTED, failure.code());
        String message = failure.message().toLowerCase();
        assertTrue(message.contains("pool closed"), message);
        assertTrue(!message.contains("plan verify"), message);
        assertTrue(!message.contains("plan execution"), message);
    }

    // ---- AC-10 ----

    @Test
    @DisplayName("DFS-042.AC-10 A bug in the verify fails the job")
    void bugInVerifyFailsTheJob() {
        Rig rig = new Rig();
        rig.comparator.throwOnY = true;
        PlanVerifyId id = rig.start(fullPlan());

        rig.executor.runNext();

        PlanVerifySnapshot snapshot = rig.status(id);
        assertEquals(JobStatus.FAILED, snapshot.status());
        assertEquals("java.lang.IllegalStateException: boom", snapshot.failure());
        assertEquals(ErrorCode.PLAN_VERIFY_NOT_FINISHED, failed(rig.service.result(id)).code());
    }

    @Test
    @DisplayName("DFS-042.AC-10 A throwing listener is detached and the job completes")
    void throwingListenerIsDetached() {
        Rig rig = new Rig();
        int[] calls = {0};
        PlanVerifyId id = ok(rig.service.start(fullPlan(), e -> {
            calls[0]++;
            throw new IllegalStateException("listener down");
        }));

        rig.executor.runNext();

        assertEquals(1, calls[0]);
        PlanVerifySnapshot snapshot = rig.status(id);
        assertEquals(JobStatus.COMPLETED, snapshot.status());
        assertEquals(2, snapshot.entriesDone());
        assertEquals(TOTAL, snapshot.bytesDone());
        VerifyReport report = rig.report(id);
        assertEquals(List.of(identical(opX()), identical(opY())), report.checks());
        assertEquals("listener down", report.listenerFailure());
    }

    // ---- AC-11 ----

    @Test
    @DisplayName("DFS-042.AC-11 A null plan or listener throws and records no job")
    void nullInputThrowsAndRecordsNothing() {
        Rig rig = new Rig();
        try {
            rig.service.start(null);
            fail("null plan");
        } catch (NullPointerException expected) {
            // expected
        }
        try {
            rig.service.start(null, e -> { });
            fail("null plan with listener");
        } catch (NullPointerException expected) {
            // expected
        }
        try {
            rig.service.start(fullPlan(), null);
            fail("null listener");
        } catch (NullPointerException expected) {
            // expected
        }
        assertEquals(List.of(), rig.store.created);
        assertEquals(0, rig.executor.pending());
    }

    @Test
    @DisplayName("DFS-042.AC-11 An empty plan completes with no checks")
    void emptyPlanCompletes() {
        Rig rig = new Rig();
        PlanVerifyId id = rig.start(plan(ID_B));

        assertEquals(new PlanVerifySnapshot(JobStatus.QUEUED, ID_B, 0, 0, 0, 0, ""), rig.status(id));
        rig.executor.runNext();

        assertEquals(JobStatus.COMPLETED, rig.status(id).status());
        assertEquals(new VerifyReport(ID_B, List.of(), false, ""), rig.report(id));
        assertEquals(0, rig.comparator.calls.get());
    }

    // ---- AC-12 ----

    @Test
    @DisplayName("DFS-042.AC-12 Two jobs keep separate state")
    void twoJobsKeepSeparateState() {
        Rig rig = new Rig();
        PlanVerifyId one = rig.start(fullPlan());
        PlanVerifyId two = rig.start(plan(ID_B, opX()));
        assertNotEquals(one, two);

        assertEquals(JobStatus.CANCELLED, ok(rig.service.cancel(two)).status());
        assertEquals(JobStatus.QUEUED, rig.status(one).status());
        rig.executor.runNext();
        rig.executor.runNext();

        PlanVerifySnapshot s1 = rig.status(one);
        assertEquals(new PlanVerifySnapshot(JobStatus.COMPLETED, ID_A, 2, 2, TOTAL, TOTAL, ""), s1);
        assertEquals(List.of(identical(opX()), identical(opY())), rig.report(one).checks());
        PlanVerifySnapshot s2 = rig.status(two);
        assertEquals(new PlanVerifySnapshot(JobStatus.CANCELLED, ID_B, 0, 1, 0, 5, ""), s2);
        assertEquals(new VerifyReport(ID_B, List.of(), true, ""), rig.report(two));
    }

    // ---- AC-13 ----

    @TempDir
    Path t;

    @Test
    @DisplayName("DFS-042.AC-13 The factory returns working ports")
    void factoryReturnsWorkingPorts() throws IOException {
        Files.createDirectories(t.resolve("A"));
        Files.createDirectories(t.resolve("B"));
        byte[] content = {1, 2, 3, 4, 5};
        Files.write(t.resolve("A/x.jpg"), content);
        Files.write(t.resolve("B/x.jpg"), content);
        Operation.Quarantine op = new Operation.Quarantine("d1", 5, List.of(t.resolve("B/x.jpg")),
                t.resolve("A/x.jpg"), new Source.FromRule(0, new KeepAndQuarantine(t.resolve("A"), t.resolve("B"))));
        ManualExecutor executor = new ManualExecutor();
        PlanVerifyUseCases useCases = ScannerFactories.planVerifyUseCases(executor);

        PlanVerifyId id = ok(useCases.start().start(plan(ID_A, op)));

        assertEquals(JobStatus.QUEUED, ok(useCases.status().status(id)).status());
        executor.runNext();
        assertEquals(JobStatus.COMPLETED, ok(useCases.cancel().cancel(id)).status());
        assertEquals(new VerifyReport(ID_A, List.of(identical(op)), false, ""), ok(useCases.result().result(id)));
    }
}
