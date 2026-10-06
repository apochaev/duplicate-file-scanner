package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.ErrorCode;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.scanner.domain.model.ScanEvent;
import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.domain.model.ScanListener;
import us.pochaev.dfs.scanner.domain.model.ScanPhase;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.model.ScanSnapshot;
import us.pochaev.dfs.scanner.domain.model.ScanStatus;
import us.pochaev.dfs.scanner.infra.port.LocationResolver;
import us.pochaev.dfs.scanner.infra.port.ScanRecord;
import us.pochaev.dfs.scanner.infra.port.ScanRepository;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * DFS-018 AC-1 to AC-16 against fakes: a scripted {@link ScanRunner}, a {@link ManualExecutor}, a
 * fake location resolver and a fake repository that exposes its records. No real threads except
 * ones the test creates and controls with latches (bounded waits), and no sleeping.
 */
class ScanServiceTest {

    private static final Path A = Path.of("a");
    private static final Path B = Path.of("b");
    private static final Path MISSING = Path.of("missing");
    private static final Path A_FILE = Path.of("a", "file.txt");
    private static final ScanConfig CONFIG = ScanConfig.defaults();
    private static final long WAIT_SECONDS = 10;

    // ---------------------------------------------------------------- AC-1

    @Test
    @DisplayName("DFS-018.AC-1 Starting a scan returns an ID while the scan waits in the queue")
    void startingAScanReturnsAnIdWhileTheScanWaitsInTheQueue() {
        Env env = new Env();

        Result<ScanId> started = env.service.start(List.of(A), CONFIG);

        ScanId id = success(started);
        assertEquals(new ScanSnapshot(ScanStatus.QUEUED, null, 0, 0, 0, null, null), snapshot(env, id));
        assertEquals(1, env.executor.size());
        assertEquals(0, env.runner.calls.get());
    }

    // ---------------------------------------------------------------- AC-2

    @Test
    @DisplayName("DFS-018.AC-2 A scan runs, is RUNNING while the pipeline works and completes with its result")
    void aScanRunsAndCompletesWithItsResult() throws Exception {
        Env env = new Env();
        ScanResult expected = new ScanResult(List.of(duplicateSet()), List.of());
        env.runner.events = List.of(new ScanEvent.PhaseStarted(ScanPhase.WALK), new ScanEvent.FileWalked(1));
        env.runner.blockAfter = 1;
        env.runner.result = expected;
        ScanId id = success(env.service.start(List.of(A), CONFIG));

        env.runTaskOnOwnThreadUntilBlocked();
        assertEquals(ScanStatus.RUNNING, snapshot(env, id).status());

        env.releaseAndJoin();
        assertEquals(ScanStatus.COMPLETED, snapshot(env, id).status());
        assertEquals(expected, success(env.service.result(id)));
        assertTrue(success(env.service.result(id)).skipped().isEmpty());
    }

    // ---------------------------------------------------------------- AC-3

    @Test
    @DisplayName("DFS-018.AC-3 An empty location list is rejected before anything is queued")
    void emptyLocationListIsRejected() {
        assertRejected(List.of(), ErrorCode.NO_LOCATIONS);
    }

    @Test
    @DisplayName("DFS-018.AC-3 A missing location is rejected before anything is queued")
    void missingLocationIsRejected() {
        assertRejected(List.of(MISSING), ErrorCode.LOCATION_NOT_FOUND);
    }

    @Test
    @DisplayName("DFS-018.AC-3 A location that is a file is rejected before anything is queued")
    void fileLocationIsRejected() {
        assertRejected(List.of(A_FILE), ErrorCode.NOT_A_DIRECTORY);
    }

    @Test
    @DisplayName("DFS-018.AC-3 A good location followed by a missing one is rejected before anything is queued")
    void goodThenMissingLocationIsRejected() {
        assertRejected(List.of(A, MISSING), ErrorCode.LOCATION_NOT_FOUND);
    }

    private void assertRejected(List<Path> locations, ErrorCode code) {
        Env env = new Env();

        Result<ScanId> started = env.service.start(locations, CONFIG);

        assertEquals(code, failure(started).code());
        assertTrue(env.repository.records().isEmpty());
        assertEquals(0, env.executor.size());
    }

    // ---------------------------------------------------------------- AC-4

    @Test
    @DisplayName("DFS-018.AC-4 An executor that rejects the task fails the start and the scan record")
    void executorRejectionFailsTheStartAndTheRecord() {
        Env env = new Env();
        env.executor.rejectWith(new RejectedExecutionException("queue full"));

        Result<ScanId> started = env.service.start(List.of(A), CONFIG);

        assertEquals(ErrorCode.EXECUTOR_REJECTED, failure(started).code());
        assertEquals(1, env.repository.records().size());
        ScanSnapshot snapshot = env.repository.records().getFirst().snapshot();
        assertEquals(ScanStatus.FAILED, snapshot.status());
        assertTrue(snapshot.failureMessage().contains("queue full"), snapshot.failureMessage());
    }

    // ---------------------------------------------------------------- AC-5

    @Test
    @DisplayName("DFS-018.AC-5 The snapshot follows the pipeline's progress")
    void snapshotFollowsThePipelinesProgress() throws Exception {
        Env env = new Env();
        env.runner.events = List.of(new ScanEvent.FileWalked(48210),
                new ScanEvent.PhaseStarted(ScanPhase.FULL_HASH),
                new ScanEvent.GroupDone(ScanPhase.FULL_HASH, 37, 120));
        env.runner.blockAfter = 3;
        ScanId id = success(env.service.start(List.of(A), CONFIG));
        assertEquals(new ScanSnapshot(ScanStatus.QUEUED, null, 0, 0, 0, null, null), snapshot(env, id));

        env.runTaskOnOwnThreadUntilBlocked();

        ScanSnapshot running = snapshot(env, id);
        assertEquals(ScanStatus.RUNNING, running.status());
        assertEquals(ScanPhase.FULL_HASH, running.phase());
        assertEquals(48210, running.filesWalked());
        assertEquals(37, running.groupsDone());
        assertEquals(120, running.groupsTotal());
        env.releaseAndJoin();
    }

    // ---------------------------------------------------------------- AC-6

    @Test
    @DisplayName("DFS-018.AC-6 A listener receives every event in order, on the thread that runs the task")
    void listenerReceivesEveryEventOnTheExecutorsThread() throws Exception {
        Env env = new Env();
        List<ScanEvent> sent = List.of(new ScanEvent.PhaseStarted(ScanPhase.WALK), new ScanEvent.FileWalked(1),
                new ScanEvent.FileWalked(2), new ScanEvent.PhaseStarted(ScanPhase.PARTIAL_HASH));
        env.runner.events = sent;
        RecordingListener listener = new RecordingListener();
        success(env.service.start(List.of(A), CONFIG, listener));

        env.runTaskOnOwnThreadUntilDone();

        assertEquals(sent, listener.events);
        assertEquals(List.of(env.taskThread), listener.threads.stream().distinct().toList());
        assertNotEquals(Thread.currentThread(), env.taskThread);
    }

    @Test
    @DisplayName("DFS-018.AC-6 The overload without a listener runs the same scan")
    void overloadWithoutAListenerRunsTheSameScan() {
        Env env = new Env();
        env.runner.events = List.of(new ScanEvent.FileWalked(1));
        ScanId id = success(env.service.start(List.of(A), CONFIG));

        env.executor.runNext();

        assertEquals(ScanStatus.COMPLETED, snapshot(env, id).status());
    }

    // ---------------------------------------------------------------- AC-7

    @Test
    @DisplayName("DFS-018.AC-7 Cancelling a queued scan cancels it at once and it never runs the pipeline")
    void cancellingAQueuedScanCancelsItAtOnce() {
        Env env = new Env();
        ScanId id = success(env.service.start(List.of(A), CONFIG));

        ScanSnapshot afterCancel = success(env.service.cancel(id));

        assertEquals(ScanStatus.CANCELLED, afterCancel.status());
        env.executor.runNext();
        assertEquals(0, env.runner.calls.get());
        assertEquals(ScanStatus.CANCELLED, snapshot(env, id).status());
    }

    // ---------------------------------------------------------------- AC-8

    @Test
    @DisplayName("DFS-018.AC-8 Cancelling a running scan goes through CANCELLING to CANCELLED")
    void cancellingARunningScanGoesThroughCancelling() throws Exception {
        Env env = new Env();
        env.runner.events = List.of(new ScanEvent.FileWalked(1), new ScanEvent.FileWalked(2),
                new ScanEvent.FileWalked(3));
        env.runner.blockAfter = 3;
        env.runner.failure = new CancellationException("stopped");
        ScanId id = success(env.service.start(List.of(A), CONFIG));
        env.runTaskOnOwnThreadUntilBlocked();
        assertFalse(env.runner.stop.getAsBoolean());

        ScanSnapshot afterCancel = success(env.service.cancel(id));

        assertEquals(ScanStatus.CANCELLING, afterCancel.status());
        assertTrue(env.runner.stop.getAsBoolean());
        env.releaseAndJoin();
        assertEquals(ScanStatus.CANCELLED, snapshot(env, id).status());
        assertEquals(ErrorCode.SCAN_NOT_COMPLETED, failure(env.service.result(id)).code());
        assertTrue(env.runner.stop.getAsBoolean());
    }

    // ---------------------------------------------------------------- AC-9

    @ParameterizedTest(name = "DFS-018.AC-9 Cancelling a {0} scan changes nothing")
    @EnumSource(value = ScanStatus.class, names = {"CANCELLING", "COMPLETED", "CANCELLED", "FAILED"})
    @DisplayName("DFS-018.AC-9 Cancelling a scan that is already stopping or finished changes nothing")
    void cancellingAStoppingOrFinishedScanChangesNothing(ScanStatus status) throws Exception {
        Env env = new Env();
        ScanId id = env.reach(status);
        ScanSnapshot before = snapshot(env, id);
        assertEquals(status, before.status());

        Result<ScanSnapshot> cancelled = env.service.cancel(id);

        assertEquals(before, success(cancelled));
        assertEquals(before, snapshot(env, id));
        env.finishIfBlocked();
    }

    // ---------------------------------------------------------------- AC-10

    @Test
    @DisplayName("DFS-018.AC-10 Completion wins over a cancel request that arrived first")
    void completionWinsOverAnEarlierCancel() throws Exception {
        Env env = new Env();
        ScanResult expected = new ScanResult(List.of(duplicateSet()), List.of());
        env.runner.events = List.of(new ScanEvent.FileWalked(1));
        env.runner.blockAfter = 1;
        env.runner.result = expected;
        ScanId id = success(env.service.start(List.of(A), CONFIG));
        env.runTaskOnOwnThreadUntilBlocked();
        assertEquals(ScanStatus.CANCELLING, success(env.service.cancel(id)).status());

        env.releaseAndJoin();

        assertEquals(ScanStatus.COMPLETED, snapshot(env, id).status());
        assertEquals(expected, success(env.service.result(id)));
    }

    @Test
    @DisplayName("DFS-018.AC-10 A cancel request after completion is a no-op")
    void cancelAfterCompletionIsANoOp() {
        Env env = new Env();
        ScanResult expected = new ScanResult(List.of(duplicateSet()), List.of());
        env.runner.result = expected;
        ScanId id = success(env.service.start(List.of(A), CONFIG));
        env.executor.runNext();

        ScanSnapshot afterCancel = success(env.service.cancel(id));

        assertEquals(ScanStatus.COMPLETED, afterCancel.status());
        assertEquals(expected, success(env.service.result(id)));
    }

    // ---------------------------------------------------------------- AC-11

    @Test
    @DisplayName("DFS-018.AC-11 GetScanStatus fails with UNKNOWN_SCAN for an ID that was never returned")
    void statusOfAnUnknownScan() {
        Env env = new Env();
        assertEquals(ErrorCode.UNKNOWN_SCAN, failure(env.service.status(ScanId.random())).code());
    }

    @Test
    @DisplayName("DFS-018.AC-11 CancelScan fails with UNKNOWN_SCAN for an ID that was never returned")
    void cancelOfAnUnknownScan() {
        Env env = new Env();
        assertEquals(ErrorCode.UNKNOWN_SCAN, failure(env.service.cancel(ScanId.random())).code());
    }

    @Test
    @DisplayName("DFS-018.AC-11 GetScanResult fails with UNKNOWN_SCAN for an ID that was never returned")
    void resultOfAnUnknownScan() {
        Env env = new Env();
        assertEquals(ErrorCode.UNKNOWN_SCAN, failure(env.service.result(ScanId.random())).code());
    }

    // ---------------------------------------------------------------- AC-12

    @ParameterizedTest(name = "DFS-018.AC-12 The result is not available while the scan is {0}")
    @EnumSource(value = ScanStatus.class, names = {"QUEUED", "RUNNING", "CANCELLING", "CANCELLED", "FAILED"})
    @DisplayName("DFS-018.AC-12 The result is only available once the scan completed")
    void resultIsOnlyAvailableOnceTheScanCompleted(ScanStatus status) throws Exception {
        Env env = new Env();
        ScanId id = env.reach(status);

        Result.Failure<ScanResult> failed = failure(env.service.result(id));

        assertEquals(ErrorCode.SCAN_NOT_COMPLETED, failed.code());
        assertTrue(failed.message().contains(status.name()), failed.message());
        env.finishIfBlocked();
    }

    // ---------------------------------------------------------------- AC-13

    @Test
    @DisplayName("DFS-018.AC-13 A pipeline IOException makes the scan FAILED with the reason")
    void pipelineIoExceptionFailsTheScan() {
        assertFailsWith(new NoSuchFileException("C:/a"), "java.nio.file.NoSuchFileException: C:/a");
    }

    @Test
    @DisplayName("DFS-018.AC-13 A pipeline RuntimeException makes the scan FAILED with the reason")
    void pipelineRuntimeExceptionFailsTheScan() {
        assertFailsWith(new IllegalStateException("bug in stage"),
                "java.lang.IllegalStateException: bug in stage");
    }

    private void assertFailsWith(Throwable thrown, String message) {
        Env env = new Env();
        env.runner.failure = thrown;
        ScanId id = success(env.service.start(List.of(A), CONFIG));

        env.executor.runNext();

        ScanSnapshot snapshot = snapshot(env, id);
        assertEquals(ScanStatus.FAILED, snapshot.status());
        assertEquals(message, snapshot.failureMessage());
    }

    // ---------------------------------------------------------------- AC-14

    @Test
    @DisplayName("DFS-018.AC-14 An Error leaves the scan FAILED and still propagates out of the task")
    void anErrorLeavesTheScanFailedAndPropagates() {
        Env env = new Env();
        env.runner.failure = new OutOfMemoryError("test");
        ScanId id = success(env.service.start(List.of(A), CONFIG));

        try {
            env.executor.runNext();
            fail("expected the OutOfMemoryError to propagate out of the task");
        } catch (OutOfMemoryError expected) {
            assertEquals("test", expected.getMessage());
        }

        assertEquals(ScanStatus.FAILED, snapshot(env, id).status());
    }

    // ---------------------------------------------------------------- AC-15

    @Test
    @DisplayName("DFS-018.AC-15 A throwing listener is detached and the scan carries on")
    void aThrowingListenerIsDetachedAndTheScanCarriesOn() {
        Env env = new Env();
        env.runner.events = List.of(new ScanEvent.PhaseStarted(ScanPhase.WALK), new ScanEvent.FileWalked(1),
                new ScanEvent.FileWalked(2), new ScanEvent.FileWalked(3));
        RecordingListener listener = new RecordingListener();
        listener.throwOnEvent = 2;
        ScanId id = success(env.service.start(List.of(A), CONFIG, listener));

        env.executor.runNext();

        assertEquals(2, listener.events.size());
        ScanSnapshot snapshot = snapshot(env, id);
        assertEquals(ScanStatus.COMPLETED, snapshot.status());
        assertEquals("java.lang.IllegalStateException: console closed", snapshot.listenerFailure());
        assertEquals(3, snapshot.filesWalked());
    }

    // ---------------------------------------------------------------- AC-16

    @Test
    @DisplayName("DFS-018.AC-16 StartScan with null locations throws NullPointerException and submits nothing")
    void startWithNullLocations() {
        Env env = new Env();
        try {
            env.service.start(null, CONFIG);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
        assertNothingSubmitted(env);
    }

    @Test
    @DisplayName("DFS-018.AC-16 StartScan with a null location element throws NullPointerException and submits nothing")
    void startWithNullLocationElement() {
        Env env = new Env();
        List<Path> locations = new ArrayList<>();
        locations.add(A);
        locations.add(null);
        try {
            env.service.start(locations, CONFIG);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
        assertNothingSubmitted(env);
    }

    @Test
    @DisplayName("DFS-018.AC-16 StartScan with a null config throws NullPointerException and submits nothing")
    void startWithNullConfig() {
        Env env = new Env();
        try {
            env.service.start(List.of(A), null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
        assertNothingSubmitted(env);
    }

    @Test
    @DisplayName("DFS-018.AC-16 StartScan with a null listener throws NullPointerException and submits nothing")
    void startWithNullListener() {
        Env env = new Env();
        try {
            env.service.start(List.of(A), CONFIG, null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
        assertNothingSubmitted(env);
    }

    @Test
    @DisplayName("DFS-018.AC-16 GetScanStatus with a null ScanId throws NullPointerException and submits nothing")
    void statusWithNullId() {
        Env env = new Env();
        try {
            env.service.status(null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
        assertNothingSubmitted(env);
    }

    @Test
    @DisplayName("DFS-018.AC-16 CancelScan with a null ScanId throws NullPointerException and submits nothing")
    void cancelWithNullId() {
        Env env = new Env();
        try {
            env.service.cancel(null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
        assertNothingSubmitted(env);
    }

    @Test
    @DisplayName("DFS-018.AC-16 GetScanResult with a null ScanId throws NullPointerException and submits nothing")
    void resultWithNullId() {
        Env env = new Env();
        try {
            env.service.result(null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
        assertNothingSubmitted(env);
    }

    private static void assertNothingSubmitted(Env env) {
        assertEquals(0, env.executor.size());
        assertTrue(env.repository.records().isEmpty());
    }

    // ---------------------------------------------------------------- helpers

    private static DuplicateSet duplicateSet() {
        Instant time = Instant.parse("2026-01-01T00:00:00Z");
        return new DuplicateSet(10, "digest",
                List.of(new FileEntry(Path.of("a", "x.jpg"), 10, time), new FileEntry(Path.of("a", "y.jpg"), 10, time)));
    }

    private static <T> T success(Result<T> result) {
        if (result instanceof Result.Success<T> success) {
            return success.value();
        }
        return fail("expected Success but was " + result);
    }

    private static <T> Result.Failure<T> failure(Result<T> result) {
        @SuppressWarnings("unchecked")
        Result.Failure<T> failure = (Result.Failure<T>) assertInstanceOf(Result.Failure.class, result,
                String.valueOf(result));
        return failure;
    }

    private static ScanSnapshot snapshot(Env env, ScanId id) {
        return success(env.service.status(id));
    }

    /** Keeps submitted tasks until the test runs them. */
    static final class ManualExecutor implements Executor {
        private final Deque<Runnable> tasks = new ArrayDeque<>();
        private RuntimeException rejection;

        @Override
        public synchronized void execute(Runnable task) {
            if (rejection != null) {
                throw rejection;
            }
            tasks.add(task);
        }

        synchronized void rejectWith(RuntimeException exception) {
            rejection = exception;
        }

        synchronized int size() {
            return tasks.size();
        }

        void runNext() {
            Runnable task;
            synchronized (this) {
                task = tasks.poll();
            }
            if (task == null) {
                fail("no queued task to run");
            }
            task.run();
        }
    }

    /**
     * Scripted {@link ScanRunner}: emits {@code events} to the listener, blocks on {@code release}
     * after {@code blockAfter} events (0 = before the first, -1 = never), then throws
     * {@code failure} or returns {@code result}.
     */
    static final class ScriptedRunner implements ScanRunner {
        List<ScanEvent> events = List.of();
        int blockAfter = -1;
        ScanResult result = new ScanResult(List.of(), List.of());
        Throwable failure;
        final AtomicInteger calls = new AtomicInteger();
        final CountDownLatch blocked = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        volatile BooleanSupplier stop;

        @Override
        public ScanResult run(List<Path> locations, ScanConfig config, ScanListener listener,
                              BooleanSupplier stopSignal) throws IOException {
            calls.incrementAndGet();
            stop = stopSignal;
            if (blockAfter == 0) {
                block();
            }
            for (int i = 0; i < events.size(); i++) {
                listener.onEvent(events.get(i));
                if (blockAfter == i + 1) {
                    block();
                }
            }
            if (failure != null) {
                ScriptedRunner.<RuntimeException>sneakyThrow(failure);
            }
            return result;
        }

        private void block() {
            blocked.countDown();
            try {
                if (!release.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("runner was never released");
                }
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }

        @SuppressWarnings("unchecked")
        private static <E extends Throwable> void sneakyThrow(Throwable t) throws E {
            throw (E) t;
        }
    }

    /** Hand-written repository that exposes every record it holds. */
    static final class ListingRepository implements ScanRepository {
        private final Map<ScanId, ScanRecord> records = new ConcurrentHashMap<>();

        @Override
        public void create(ScanRecord record) {
            if (records.putIfAbsent(record.id(), record) != null) {
                throw new IllegalStateException("duplicate " + record.id());
            }
        }

        @Override
        public Optional<ScanRecord> find(ScanId id) {
            return Optional.ofNullable(records.get(id));
        }

        @Override
        public Optional<ScanRecord> update(ScanId id, UnaryOperator<ScanRecord> change) {
            return Optional.ofNullable(records.computeIfPresent(id, (key, current) -> change.apply(current)));
        }

        List<ScanRecord> records() {
            return new ArrayList<>(records.values());
        }
    }

    static final class FakeResolver implements LocationResolver {
        private final Map<Path, IOException> errors = new HashMap<>();

        FakeResolver() {
            errors.put(MISSING, new NoSuchFileException(MISSING.toString()));
            errors.put(A_FILE, new NotDirectoryException(A_FILE.toString()));
        }

        @Override
        public Path realPath(Path location) throws IOException {
            IOException error = errors.get(location);
            if (error != null) {
                throw error;
            }
            return location;
        }
    }

    static final class RecordingListener implements ScanListener {
        final List<ScanEvent> events = new CopyOnWriteArrayList<>();
        final List<Thread> threads = Collections.synchronizedList(new ArrayList<>());
        int throwOnEvent = -1;

        @Override
        public void onEvent(ScanEvent event) {
            events.add(event);
            threads.add(Thread.currentThread());
            if (events.size() == throwOnEvent) {
                throw new IllegalStateException("console closed");
            }
        }
    }

    /** One service wired to the fakes, plus the means to run a task on a thread the test controls. */
    static final class Env {
        final ScriptedRunner runner = new ScriptedRunner();
        final ManualExecutor executor = new ManualExecutor();
        final ListingRepository repository = new ListingRepository();
        final ScanService service;
        volatile Thread taskThread;

        Env() {
            service = new ScanService(new FakeResolver(), (ScanRunner) runner, repository, executor);
        }

        void runTaskOnOwnThreadUntilBlocked() throws InterruptedException {
            startTaskThread();
            assertTrue(runner.blocked.await(WAIT_SECONDS, TimeUnit.SECONDS), "runner never reached its block point");
        }

        void runTaskOnOwnThreadUntilDone() throws InterruptedException {
            startTaskThread();
            taskThread.join(WAIT_SECONDS * 1000);
            assertFalse(taskThread.isAlive(), "task did not finish");
        }

        private void startTaskThread() {
            taskThread = new Thread(executor::runNext, "test-task");
            taskThread.start();
        }

        void releaseAndJoin() throws InterruptedException {
            runner.release.countDown();
            taskThread.join(WAIT_SECONDS * 1000);
            assertFalse(taskThread.isAlive(), "task did not finish after release");
        }

        void finishIfBlocked() throws InterruptedException {
            if (taskThread != null && taskThread.isAlive()) {
                releaseAndJoin();
            }
        }

        /** Drives a fresh scan over {@code A} into {@code target}, leaving it blocked if it is RUNNING or CANCELLING. */
        ScanId reach(ScanStatus target) throws InterruptedException {
            switch (target) {
                case FAILED -> runner.failure = new IllegalStateException("boom");
                case RUNNING, CANCELLING -> {
                    runner.events = List.of(new ScanEvent.FileWalked(1));
                    runner.blockAfter = 1;
                }
                default -> {
                }
            }
            ScanId id = success(service.start(List.of(A), CONFIG));
            switch (target) {
                case QUEUED -> {
                }
                case CANCELLED -> service.cancel(id);
                case COMPLETED, FAILED -> executor.runNext();
                case RUNNING -> runTaskOnOwnThreadUntilBlocked();
                case CANCELLING -> {
                    runTaskOnOwnThreadUntilBlocked();
                    service.cancel(id);
                }
            }
            return id;
        }
    }
}
