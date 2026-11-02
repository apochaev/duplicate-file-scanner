package us.pochaev.dfs.server.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.server.analysis.OverlapAnalysis;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** The per-scan overlap state computes its analysis at most once (DFS-048 decision 5). */
class OverlapStateTest {

    private static final int WAIT_SECONDS = 10;

    /** The result of a future, rethrowing the worker's own exception rather than a wrapper. */
    private static <T> T result(Future<T> f) throws Exception {
        try {
            return f.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    private static boolean isWaiting(Thread t) {
        Thread.State s = t.getState();
        return s == Thread.State.WAITING || s == Thread.State.BLOCKED || s == Thread.State.TIMED_WAITING;
    }

    @Test
    @DisplayName("DFS-048.AC-8 The analysis is computed once for concurrent and later requests")
    void computedOnce() throws Exception {
        ScanResult scan = new ScanResult(List.of(Path.of("scan-root").toAbsolutePath()), List.of(), List.of(),
                List.of(), 0);
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        OverlapState state = new OverlapState(scan, r -> {
            calls.incrementAndGet();
            entered.countDown();
            try {
                if (!release.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("never released");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return OverlapAnalysis.of(r);
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<OverlapIndex> first = pool.submit(state::index);
            if (!entered.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                result(first); // the stub's own exception, if that is why the function never ran
                fail("the analysis function was never called");
            }

            CountDownLatch secondStarted = new CountDownLatch(1);
            AtomicReference<Thread> secondThread = new AtomicReference<>();
            Future<OverlapIndex> second = pool.submit(() -> {
                secondThread.set(Thread.currentThread());
                secondStarted.countDown();
                return state.index();
            });
            assertTrue(secondStarted.await(WAIT_SECONDS, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
            while (!isWaiting(secondThread.get())) {
                if (second.isDone()) {
                    result(second);
                    fail("the second request returned before the function was released");
                }
                if (System.nanoTime() > deadline) {
                    fail("the second request never waited");
                }
                Thread.onSpinWait();
            }

            release.countDown();
            OverlapIndex a = result(first);
            OverlapIndex b = result(second);
            assertNotNull(a);
            assertSame(a, b);
            assertEquals(1, calls.get());

            assertSame(a, state.index());
            assertEquals(1, calls.get());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
