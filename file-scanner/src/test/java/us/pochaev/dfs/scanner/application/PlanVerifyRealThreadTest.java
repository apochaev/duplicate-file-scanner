package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.pochaev.dfs.scanner.domain.factory.PlanVerifyUseCases;
import us.pochaev.dfs.scanner.domain.factory.ScannerFactories;
import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
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
import us.pochaev.dfs.scanner.domain.model.VerifyReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The one real-thread test of the plan verify jobs (DFS-042): virtual threads, NIO adapters, files in a temp folder. */
class PlanVerifyRealThreadTest {

    private static final int Y_SIZE = 3 * 1024 * 1024;

    @TempDir
    Path t;

    private static byte[] bytes(int size) {
        byte[] b = new byte[size];
        for (int i = 0; i < size; i++) {
            b[i] = (byte) (i * 31 + 7);
        }
        return b;
    }

    private Operation.Quarantine op(String digest, String name, int size) throws IOException {
        Files.createDirectories(t.resolve("A"));
        Files.createDirectories(t.resolve("B"));
        Files.write(t.resolve("A").resolve(name), bytes(size));
        Files.write(t.resolve("B").resolve(name), bytes(size));
        return new Operation.Quarantine(digest, size, List.of(t.resolve("B").resolve(name)),
                t.resolve("A").resolve(name), new Source.FromRule(0,
                new KeepAndQuarantine(t.resolve("A"), t.resolve("B"))));
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

    @SuppressWarnings("unchecked")
    private static <T> T ok(Result<T> result) {
        return ((Result.Success<T>) assertInstanceOf(Result.Success.class, result, String.valueOf(result))).value();
    }

    @Test
    @DisplayName("DFS-042.AC-14 A verify on a real executor over real files completes and changes nothing")
    void endToEndOnRealFiles() throws Exception {
        Operation.Quarantine x = op("d1", "x.jpg", 5);
        Operation.Quarantine y = op("d2", "y.bin", Y_SIZE);
        DeletionPlan plan = new DeletionPlan("c".repeat(64), List.of(x, y), List.of(),
                new PlanTotals(2, Y_SIZE + 5L, 2), List.of());
        Map<String, String> before = snapshot();
        List<Future<?>> futures = new CopyOnWriteArrayList<>();
        CountDownLatch last = new CountDownLatch(1);
        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            PlanVerifyUseCases useCases = ScannerFactories.planVerifyUseCases(
                    task -> futures.add(threads.submit(task)));

            PlanVerifyId id = ok(useCases.start().start(plan, event -> {
                if (event instanceof VerifyEvent.EntryChecked e && e.done() == e.total()) {
                    last.countDown();
                }
            }));

            assertTrue(last.await(30, TimeUnit.SECONDS), "last EntryChecked never arrived");
            // the listener fires before the final status update, so wait for the task itself too
            assertEquals(1, futures.size());
            futures.get(0).get(30, TimeUnit.SECONDS);

            PlanVerifySnapshot snapshot = ok(useCases.status().status(id));
            assertEquals(JobStatus.COMPLETED, snapshot.status());
            VerifyReport report = ok(useCases.result().result(id));
            assertEquals(List.of(
                    new OperationCheck("d1", x.links().get(0), x.keeper(), Outcome.IDENTICAL, ""),
                    new OperationCheck("d2", y.links().get(0), y.keeper(), Outcome.IDENTICAL, "")), report.checks());
            assertEquals(false, report.cancelled());
        }
        assertEquals(before, snapshot());
    }
}
