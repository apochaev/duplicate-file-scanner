package us.pochaev.dfs.scannerspring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import us.pochaev.dfs.scanner.domain.factory.ScanUseCases;
import us.pochaev.dfs.scanner.domain.factory.ScannerFactories;
import us.pochaev.dfs.scanner.domain.model.ErrorCode;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.domain.model.ScanSnapshot;
import us.pochaev.dfs.scanner.domain.model.ScanStatus;
import us.pochaev.dfs.scanner.domain.usecases.command.CancelScan;
import us.pochaev.dfs.scanner.domain.usecases.command.StartScan;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanResult;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanStatus;
import us.pochaev.dfs.scanner.domain.usecases.query.PreviewDeletionPlan;
import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.KeepAndQuarantine;
import us.pochaev.dfs.scanner.domain.model.PlanRequest;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import java.time.Instant;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The scanner autoconfiguration in an ApplicationContextRunner (DFS-031). */
class ScannerAutoConfigurationTest {

    @TempDir
    Path tmp;

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ScannerAutoConfiguration.class));
    }

    /** A virtual-thread pool that counts the tasks handed to it. */
    static final class CountingExecutor extends ThreadPoolExecutor {
        final AtomicInteger count = new AtomicInteger();

        CountingExecutor() {
            super(0, Integer.MAX_VALUE, 1, TimeUnit.SECONDS, new SynchronousQueue<>(), Thread.ofVirtual().factory());
        }

        @Override
        public void execute(Runnable command) {
            count.incrementAndGet();
            super.execute(command);
        }
    }

    private ScanStatus emptyScan(StartScan start, GetScanStatus status) {
        ScanId id = ((Result.Success<ScanId>) start.start(List.of(tmp), ScanConfig.defaults())).value();
        return await(status, id);
    }

    private static ScanStatus await(GetScanStatus status, ScanId id) {
        ScanSnapshot snapshot;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        do {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            snapshot = ((Result.Success<ScanSnapshot>) status.status(id)).value();
        } while (snapshot.status() != ScanStatus.COMPLETED && snapshot.status() != ScanStatus.FAILED
                && System.nanoTime() < deadline);
        return snapshot.status();
    }

    @Test
    @DisplayName("DFS-031.AC-1 The autoconfiguration provides the four scan ports, sharing one scan repository")
    void providesTheFourPorts() {
        runner().run(context -> {
            // getBean first: on a failed context it throws with the startup failure as its cause
            StartScan start = context.getBean(StartScan.class);
            assertEquals(1, context.getBeansOfType(StartScan.class).size());
            assertEquals(1, context.getBeansOfType(CancelScan.class).size());
            assertEquals(1, context.getBeansOfType(GetScanStatus.class).size());
            assertEquals(1, context.getBeansOfType(GetScanResult.class).size());
            ScanId id = ((Result.Success<ScanId>) start.start(List.of(tmp), ScanConfig.defaults())).value();
            assertEquals(ScanStatus.COMPLETED, await(context.getBean(GetScanStatus.class), id));
            assertInstanceOf(Result.Success.class, context.getBean(GetScanResult.class).result(id));
            assertInstanceOf(Result.Success.class, context.getBean(CancelScan.class).cancel(id));
        });
    }

    @Test
    @DisplayName("DFS-031.AC-1 The autoconfiguration is registered in AutoConfiguration.imports")
    void registeredForSpringBoot() {
        List<String> names = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).getCandidates();
        assertTrue(names.contains("us.pochaev.dfs.scannerspring.ScannerAutoConfiguration"), names.toString());

        Class<?>[] classes = names.stream().filter(n -> n.startsWith("us.pochaev.dfs.scannerspring."))
                .map(n -> {
                    try {
                        return Class.forName(n);
                    } catch (ClassNotFoundException e) {
                        throw new IllegalStateException(e);
                    }
                }).toArray(Class<?>[]::new);
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(classes)).run(context -> {
            context.getBean(StartScan.class);
            assertEquals(1, context.getBeansOfType(StartScan.class).size());
        });
    }

    @Test
    @DisplayName("DFS-031.AC-2 The default scanExecutor runs tasks on virtual threads")
    void defaultExecutorIsVirtual() {
        runner().run(context -> {
            ExecutorService executor = context.getBean("scanExecutor", ExecutorService.class);
            assertTrue(executor.submit(() -> Thread.currentThread().isVirtual()).get(10, TimeUnit.SECONDS));
        });
    }

    @Test
    @DisplayName("DFS-031.AC-2 An app's own scanExecutor replaces the default")
    void appExecutorReplacesDefault() {
        CountingExecutor mine = new CountingExecutor();
        try {
            runner().withBean("scanExecutor", ExecutorService.class, () -> mine).run(context -> {
                StartScan start = context.getBean(StartScan.class);
                assertEquals(ScanStatus.COMPLETED, emptyScan(start, context.getBean(GetScanStatus.class)));
                assertEquals(1, context.getBeansOfType(ExecutorService.class).size());
                assertSame(mine, context.getBean("scanExecutor"));
                assertTrue(mine.count.get() >= 1, "tasks counted: " + mine.count.get());
            });
        } finally {
            mine.shutdownNow();
        }
    }

    @Test
    @DisplayName("DFS-031.AC-3 An app's own port bean replaces only that port")
    void appPortReplacesOnlyThatPort() {
        GetScanResult mine = id -> new Result.Failure<>(ErrorCode.UNKNOWN_SCAN, "mine");
        runner().withBean("myResult", GetScanResult.class, () -> mine).run(context -> {
            context.getBean(StartScan.class);
            assertEquals(1, context.getBeansOfType(GetScanResult.class).size());
            assertSame(mine, context.getBean(GetScanResult.class));
            assertEquals(1, context.getBeansOfType(StartScan.class).size());
            assertEquals(1, context.getBeansOfType(CancelScan.class).size());
            assertEquals(1, context.getBeansOfType(GetScanStatus.class).size());
        });
    }

    @Test
    @DisplayName("DFS-031.AC-3 An app's own ScanUseCases backs the four ports")
    void appScanUseCasesBacksPorts() {
        CountingExecutor counting = new CountingExecutor();
        try {
            ScanUseCases mine = ScannerFactories.scanUseCases(counting);
            runner().withBean("myUseCases", ScanUseCases.class, () -> mine).run(context -> {
                StartScan start = context.getBean(StartScan.class);
                assertEquals(ScanStatus.COMPLETED, emptyScan(start, context.getBean(GetScanStatus.class)));
                assertEquals(1, context.getBeansOfType(ScanUseCases.class).size());
                assertSame(mine, context.getBean(ScanUseCases.class));
                assertTrue(counting.count.get() >= 1, "tasks counted: " + counting.count.get());
            });
        } finally {
            counting.shutdownNow();
        }
    }

    private static Result<DeletionPlan> previewSample(PreviewDeletionPlan preview) {
        ScanResult scan = new ScanResult(List.of(), List.of(), List.of(new DuplicateSet(10, "d1", List.of(
                new FileEntry(Path.of("C:/A/x.jpg"), 10, Instant.EPOCH),
                new FileEntry(Path.of("C:/B/x.jpg"), 10, Instant.EPOCH)))), List.of(), 0);
        return preview.preview(scan, new PlanRequest(
                List.of(new KeepAndQuarantine(Path.of("C:/A"), Path.of("C:/B"))), List.of(), List.of()));
    }

    @Test
    @DisplayName("DFS-022.AC-14 The autoconfiguration provides one working PreviewDeletionPlan bean")
    void providesPreviewBean() {
        runner().run(context -> {
            PreviewDeletionPlan bean = context.getBean(PreviewDeletionPlan.class);
            assertEquals(1, context.getBeansOfType(PreviewDeletionPlan.class).size());
            assertEquals(1, ((Result.Success<DeletionPlan>) assertInstanceOf(Result.Success.class,
                    previewSample(bean))).value().operations().size());
        });
    }

    @Test
    @DisplayName("DFS-022.AC-14 An app's own PreviewDeletionPlan bean replaces the default")
    void appPreviewReplacesDefault() {
        PreviewDeletionPlan mine = (scan, request) -> new Result.Failure<>(ErrorCode.INVALID_RULE, "mine");
        // exercise the factory first: it is the default the bean backs off from
        assertInstanceOf(Result.Success.class, previewSample(ScannerFactories.previewDeletionPlan()));
        runner().withBean("myPreview", PreviewDeletionPlan.class, () -> mine).run(context -> {
            assertSame(mine, context.getBean(PreviewDeletionPlan.class));
            assertEquals(1, context.getBeansOfType(PreviewDeletionPlan.class).size());
        });
    }
}
