package us.pochaev.dfs.scannerspring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import us.pochaev.dfs.scanner.domain.factory.PlanExecutionUseCases;
import us.pochaev.dfs.scanner.domain.usecases.command.CancelPlanExecution;
import us.pochaev.dfs.scanner.domain.usecases.command.StartPlanExecution;
import us.pochaev.dfs.scanner.domain.usecases.query.GetPlanExecutionResult;
import us.pochaev.dfs.scanner.domain.usecases.query.GetPlanExecutionStatus;
import us.pochaev.dfs.scanner.domain.factory.PlanVerifyUseCases;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                assertEquals(java.util.Set.of("scanExecutor", "planVerifyExecutor", "planExecutionExecutor"),
                        context.getBeansOfType(ExecutorService.class).keySet());
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

    @Test
    @DisplayName("DFS-032.AC-9 The autoconfiguration provides one working ListDirectory bean")
    void providesListDirectoryBean() throws java.io.IOException {
        java.nio.file.Files.write(tmp.resolve("a.txt"), new byte[2]);
        runner().run(context -> {
            us.pochaev.dfs.scanner.domain.usecases.query.ListDirectory bean =
                    context.getBean(us.pochaev.dfs.scanner.domain.usecases.query.ListDirectory.class);
            assertEquals(1, context.getBeansOfType(
                    us.pochaev.dfs.scanner.domain.usecases.query.ListDirectory.class).size());
            us.pochaev.dfs.scanner.domain.model.DirectoryListing listing =
                    ((Result.Success<us.pochaev.dfs.scanner.domain.model.DirectoryListing>)
                            assertInstanceOf(Result.Success.class, bean.list(tmp))).value();
            assertEquals(1, listing.entries().size());
            assertEquals("a.txt", listing.entries().get(0).name());
        });
    }

    @Test
    @DisplayName("DFS-032.AC-9 An app's own ListDirectory bean replaces the default")
    void appListDirectoryReplacesDefault() throws java.io.IOException {
        java.nio.file.Files.write(tmp.resolve("a.txt"), new byte[2]);
        us.pochaev.dfs.scanner.domain.usecases.query.ListDirectory mine =
                folder -> new Result.Failure<>(ErrorCode.LOCATION_NOT_FOUND, "mine");
        // exercise the factory first: it is the default the bean backs off from
        assertInstanceOf(Result.Success.class, ScannerFactories.listDirectory().list(tmp));
        runner().withBean("myListDirectory", us.pochaev.dfs.scanner.domain.usecases.query.ListDirectory.class,
                () -> mine).run(context -> {
            assertSame(mine, context.getBean(us.pochaev.dfs.scanner.domain.usecases.query.ListDirectory.class));
            assertEquals(1, context.getBeansOfType(
                    us.pochaev.dfs.scanner.domain.usecases.query.ListDirectory.class).size());
        });
    }

    private ScanResult driftScan() throws java.io.IOException {
        List<FileEntry> entries = new java.util.ArrayList<>();
        for (String folder : List.of("A", "B")) {
            Path p = tmp.resolve(folder).resolve("x.jpg");
            java.nio.file.Files.createDirectories(p.getParent());
            java.nio.file.Files.write(p, new byte[5]);
            java.nio.file.attribute.BasicFileAttributes a = java.nio.file.Files.readAttributes(p,
                    java.nio.file.attribute.BasicFileAttributes.class, java.nio.file.LinkOption.NOFOLLOW_LINKS);
            entries.add(new FileEntry(p, a.size(), a.lastModifiedTime().toInstant()));
        }
        return new ScanResult(List.of(tmp), List.of(), List.of(new DuplicateSet(5, "d1", entries)), List.of(), 0);
    }

    private DeletionPlan driftPlan(ScanResult scan) {
        return ((Result.Success<DeletionPlan>) ScannerFactories.previewDeletionPlan().preview(scan,
                new PlanRequest(List.of(new KeepAndQuarantine(tmp.resolve("A"), tmp.resolve("B"))), List.of(),
                        List.of()))).value();
    }

    @Test
    @DisplayName("DFS-033.AC-9 The autoconfiguration provides one working CheckPlanDrift bean")
    void providesCheckPlanDriftBean() throws java.io.IOException {
        ScanResult scan = driftScan();
        DeletionPlan plan = driftPlan(scan);
        runner().run(context -> {
            us.pochaev.dfs.scanner.domain.usecases.query.CheckPlanDrift bean =
                    context.getBean(us.pochaev.dfs.scanner.domain.usecases.query.CheckPlanDrift.class);
            assertEquals(1, context.getBeansOfType(
                    us.pochaev.dfs.scanner.domain.usecases.query.CheckPlanDrift.class).size());
            us.pochaev.dfs.scanner.domain.model.DriftReport report =
                    ((Result.Success<us.pochaev.dfs.scanner.domain.model.DriftReport>)
                            assertInstanceOf(Result.Success.class, bean.check(scan, plan))).value();
            assertEquals(2, report.checked());
            assertTrue(report.drifts().isEmpty());
        });
    }

    @Test
    @DisplayName("DFS-033.AC-9 An app's own CheckPlanDrift bean replaces the default")
    void appCheckPlanDriftReplacesDefault() throws java.io.IOException {
        ScanResult scan = driftScan();
        DeletionPlan plan = driftPlan(scan);
        us.pochaev.dfs.scanner.domain.usecases.query.CheckPlanDrift mine =
                (s, p) -> new Result.Failure<>(ErrorCode.PLAN_NOT_FROM_SCAN, "mine");
        // exercise the factory first: it is the default the bean backs off from
        assertInstanceOf(Result.Success.class, ScannerFactories.checkPlanDrift().check(scan, plan));
        runner().withBean("myCheckPlanDrift", us.pochaev.dfs.scanner.domain.usecases.query.CheckPlanDrift.class,
                () -> mine).run(context -> {
            assertSame(mine, context.getBean(us.pochaev.dfs.scanner.domain.usecases.query.CheckPlanDrift.class));
            assertEquals(1, context.getBeansOfType(
                    us.pochaev.dfs.scanner.domain.usecases.query.CheckPlanDrift.class).size());
        });
    }

    @Test
    @DisplayName("DFS-034.AC-10 The autoconfiguration provides one working VerifyPlanContent bean")
    void providesVerifyPlanContentBean() throws java.io.IOException {
        DeletionPlan plan = driftPlan(driftScan());
        runner().run(context -> {
            us.pochaev.dfs.scanner.domain.usecases.query.VerifyPlanContent bean = context.getBean(us.pochaev.dfs.scanner.domain.usecases.query.VerifyPlanContent.class);
            assertEquals(1, context.getBeansOfType(us.pochaev.dfs.scanner.domain.usecases.query.VerifyPlanContent.class).size());
            us.pochaev.dfs.scanner.domain.model.VerifyReport report = ((Result.Success<us.pochaev.dfs.scanner.domain.model.VerifyReport>) assertInstanceOf(Result.Success.class, bean.verify(plan, e -> { }, () -> false))).value();
            assertEquals(1, report.checks().size());
            assertEquals(us.pochaev.dfs.scanner.domain.model.OperationCheck.Outcome.IDENTICAL, report.checks().get(0).outcome());
        });
    }

    @Test
    @DisplayName("DFS-034.AC-10 An app's own VerifyPlanContent bean replaces the default")
    void appVerifyPlanContentReplacesDefault() throws java.io.IOException {
        DeletionPlan plan = driftPlan(driftScan());
        us.pochaev.dfs.scanner.domain.usecases.query.VerifyPlanContent mine = (p, l, c) -> new Result.Success<>(new us.pochaev.dfs.scanner.domain.model.VerifyReport(p.id(), List.of(), false, ""));
        // exercise the factory first: it is the default the bean backs off from
        assertInstanceOf(Result.Success.class, ScannerFactories.verifyPlanContent().verify(plan, e -> { }, () -> false));
        runner().withBean("myVerifyPlanContent", us.pochaev.dfs.scanner.domain.usecases.query.VerifyPlanContent.class, () -> mine).run(context -> {
            assertSame(mine, context.getBean(us.pochaev.dfs.scanner.domain.usecases.query.VerifyPlanContent.class));
            assertEquals(1, context.getBeansOfType(us.pochaev.dfs.scanner.domain.usecases.query.VerifyPlanContent.class).size());
        });
    }

    // ---- DFS-042: plan verify jobs ----

    private static DeletionPlan emptyPlan() {
        return new DeletionPlan("d".repeat(64), List.of(), List.of(),
                new us.pochaev.dfs.scanner.domain.model.PlanTotals(0, 0, 0), List.of());
    }

    @SuppressWarnings("unchecked")
    private static us.pochaev.dfs.scanner.domain.model.JobStatus awaitJob(
            us.pochaev.dfs.scanner.domain.usecases.query.GetPlanVerifyStatus status,
            us.pochaev.dfs.scanner.domain.model.PlanVerifyId id) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        us.pochaev.dfs.scanner.domain.model.JobStatus current;
        do {
            Thread.onSpinWait();
            current = ((Result.Success<us.pochaev.dfs.scanner.domain.model.PlanVerifySnapshot>) assertInstanceOf(
                    Result.Success.class, status.status(id))).value().status();
        } while (current != us.pochaev.dfs.scanner.domain.model.JobStatus.COMPLETED
                && current != us.pochaev.dfs.scanner.domain.model.JobStatus.FAILED && System.nanoTime() < deadline);
        return current;
    }

    @SuppressWarnings("unchecked")
    private static us.pochaev.dfs.scanner.domain.model.PlanVerifyId startOn(
            us.pochaev.dfs.scanner.domain.usecases.command.StartPlanVerify start) {
        return ((Result.Success<us.pochaev.dfs.scanner.domain.model.PlanVerifyId>) assertInstanceOf(
                Result.Success.class, start.start(emptyPlan()))).value();
    }

    @Test
    @DisplayName("DFS-042.AC-13 The autoconfiguration provides the plan verify beans, sharing one job store")
    void providesPlanVerifyBeans() {
        runner().run(context -> {
            // start through the bean first: on a failed context getBean throws with the startup failure as its cause
            us.pochaev.dfs.scanner.domain.usecases.command.StartPlanVerify start =
                    context.getBean(us.pochaev.dfs.scanner.domain.usecases.command.StartPlanVerify.class);
            us.pochaev.dfs.scanner.domain.model.PlanVerifyId id = startOn(start);

            assertEquals(us.pochaev.dfs.scanner.domain.model.JobStatus.COMPLETED,
                    awaitJob(context.getBean(us.pochaev.dfs.scanner.domain.usecases.query.GetPlanVerifyStatus.class), id));
            assertInstanceOf(Result.Success.class,
                    context.getBean(us.pochaev.dfs.scanner.domain.usecases.query.GetPlanVerifyResult.class).result(id));
            assertInstanceOf(Result.Success.class,
                    context.getBean(us.pochaev.dfs.scanner.domain.usecases.command.CancelPlanVerify.class).cancel(id));
            assertEquals(1, context.getBeansOfType(PlanVerifyUseCases.class).size());
            assertEquals(1, context.getBeansOfType(us.pochaev.dfs.scanner.domain.usecases.command.StartPlanVerify.class).size());
            assertEquals(1, context.getBeansOfType(us.pochaev.dfs.scanner.domain.usecases.command.CancelPlanVerify.class).size());
            assertEquals(1, context.getBeansOfType(us.pochaev.dfs.scanner.domain.usecases.query.GetPlanVerifyStatus.class).size());
            assertEquals(1, context.getBeansOfType(us.pochaev.dfs.scanner.domain.usecases.query.GetPlanVerifyResult.class).size());
            assertTrue(context.containsBean("planVerifyExecutor"));
        });
    }

    @Test
    @DisplayName("DFS-042.AC-13 An app's own planVerifyExecutor replaces the default")
    void appPlanVerifyExecutorReplacesDefault() {
        CountingExecutor mine = new CountingExecutor();
        try {
            runner().withBean("planVerifyExecutor", ExecutorService.class, () -> mine).run(context -> {
                us.pochaev.dfs.scanner.domain.model.PlanVerifyId id =
                        startOn(context.getBean(us.pochaev.dfs.scanner.domain.usecases.command.StartPlanVerify.class));
                assertEquals(us.pochaev.dfs.scanner.domain.model.JobStatus.COMPLETED,
                        awaitJob(context.getBean(us.pochaev.dfs.scanner.domain.usecases.query.GetPlanVerifyStatus.class), id));
                assertSame(mine, context.getBean("planVerifyExecutor"));
                assertTrue(mine.count.get() >= 1, "tasks counted: " + mine.count.get());
            });
        } finally {
            mine.shutdownNow();
        }
    }

    @Test
    @DisplayName("DFS-042.AC-13 An app's own PlanVerifyUseCases replaces the default")
    void appPlanVerifyUseCasesReplacesDefault() {
        PlanVerifyUseCases mine = ScannerFactories.planVerifyUseCases(Runnable::run);
        // exercise the factory first: it is the default the bean backs off from
        us.pochaev.dfs.scanner.domain.model.PlanVerifyId own = startOn(mine.start());
        assertEquals(us.pochaev.dfs.scanner.domain.model.JobStatus.COMPLETED, awaitJob(mine.status(), own));
        runner().withBean("myPlanVerify", PlanVerifyUseCases.class, () -> mine).run(context -> {
            assertSame(mine, context.getBean(PlanVerifyUseCases.class));
            assertEquals(1, context.getBeansOfType(PlanVerifyUseCases.class).size());
            us.pochaev.dfs.scanner.domain.model.PlanVerifyId id =
                    startOn(context.getBean(us.pochaev.dfs.scanner.domain.usecases.command.StartPlanVerify.class));
            assertInstanceOf(Result.Success.class, mine.status().status(id));
        });
    }

    // ---- DFS-051: plan execution ----

    private static final byte[] PHOTO = "abcdefghijklmnopqrstuvwxyz!".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    private record Exec(ScanResult scan, DeletionPlan plan, Path b) {
    }

    @SuppressWarnings("unchecked")
    private Exec execFixture() throws java.io.IOException {
        List<FileEntry> entries = new java.util.ArrayList<>();
        Path b = null;
        for (String folder : List.of("A", "B")) {
            Path p = tmp.resolve("lib").resolve(folder).resolve("photo.jpg");
            java.nio.file.Files.createDirectories(p.getParent());
            java.nio.file.Files.write(p, PHOTO);
            java.nio.file.Files.setLastModifiedTime(p, java.nio.file.attribute.FileTime.from(Instant.EPOCH));
            entries.add(new FileEntry(p, PHOTO.length, Instant.EPOCH));
            b = p;
        }
        ScanResult scan = new ScanResult(List.of(tmp.resolve("lib")), List.of(),
                List.of(new DuplicateSet(PHOTO.length, "d-photo", entries)), List.of(), 0);
        DeletionPlan plan = ((Result.Success<DeletionPlan>) assertInstanceOf(Result.Success.class,
                ScannerFactories.previewDeletionPlan().preview(scan, new PlanRequest(List.of(new KeepAndQuarantine(
                        tmp.resolve("lib/A"), tmp.resolve("lib/B"))), List.of(), List.of())))).value();
        return new Exec(scan, plan, b);
    }

    @SuppressWarnings("unchecked")
    private static us.pochaev.dfs.scanner.domain.model.JobStatus awaitExecution(
            GetPlanExecutionStatus status, us.pochaev.dfs.scanner.domain.model.PlanExecutionId id) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        us.pochaev.dfs.scanner.domain.model.JobStatus current;
        do {
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            current = ((Result.Success<us.pochaev.dfs.scanner.domain.model.PlanExecutionSnapshot>)
                    assertInstanceOf(Result.Success.class, status.status(id))).value().status();
        } while (current != us.pochaev.dfs.scanner.domain.model.JobStatus.COMPLETED
                && current != us.pochaev.dfs.scanner.domain.model.JobStatus.FAILED && System.nanoTime() < deadline);
        return current;
    }

    @SuppressWarnings("unchecked")
    private static us.pochaev.dfs.scanner.domain.model.PlanExecutionId startExec(StartPlanExecution start, Exec fx) {
        return ((Result.Success<us.pochaev.dfs.scanner.domain.model.PlanExecutionId>) assertInstanceOf(
                Result.Success.class, start.start(fx.scan(), fx.plan()))).value();
    }

    private ApplicationContextRunner executionRunner() {
        return runner().withPropertyValues(
                "dfs.execution.manifest-folder=" + tmp.resolve("manifests"),
                "dfs.execution.quarantine-anchors[0]=" + tmp);
    }

    @Test
    @DisplayName("DFS-051.AC-20 The autoconfiguration provides the plan execution beans, manifest folder from the property")
    void providesPlanExecutionBeans() throws Exception {
        Exec fx = execFixture();
        executionRunner().run(context -> {
            // start through the bean first: on a failed context getBean throws with the startup failure as its cause
            StartPlanExecution start = context.getBean(StartPlanExecution.class);
            us.pochaev.dfs.scanner.domain.model.PlanExecutionId id = startExec(start, fx);

            assertEquals(us.pochaev.dfs.scanner.domain.model.JobStatus.COMPLETED,
                    awaitExecution(context.getBean(GetPlanExecutionStatus.class), id));
            assertInstanceOf(Result.Success.class, context.getBean(GetPlanExecutionResult.class).result(id));
            assertInstanceOf(Result.Success.class, context.getBean(CancelPlanExecution.class).cancel(id));
            assertEquals(1, context.getBeansOfType(PlanExecutionUseCases.class).size());
            assertEquals(1, context.getBeansOfType(StartPlanExecution.class).size());
            assertEquals(1, context.getBeansOfType(CancelPlanExecution.class).size());
            assertEquals(1, context.getBeansOfType(GetPlanExecutionStatus.class).size());
            assertEquals(1, context.getBeansOfType(GetPlanExecutionResult.class).size());
            assertTrue(context.containsBean("planExecutionExecutor"));
            assertFalse(java.nio.file.Files.exists(fx.b()));
            assertTrue(java.nio.file.Files.exists(tmp.resolve(".dfs-quarantine").resolve(id.value().toString())
                    .resolve(tmp.relativize(fx.b()))));
            assertTrue(java.nio.file.Files.exists(tmp.resolve("manifests").resolve(id.value() + ".jsonl")));
        });
    }

    @Test
    @DisplayName("DFS-051.AC-20 With no property the manifest folder is under the user home and nothing is created at startup")
    void defaultManifestFolderAndNothingCreatedAtStartup() throws Exception {
        Exec fx = execFixture();
        // exercise the use case first: a pure configuration assertion passes against the stubs
        executionRunner().run(context -> {
            us.pochaev.dfs.scanner.domain.model.PlanExecutionId id =
                    startExec(context.getBean(StartPlanExecution.class), fx);
            assertEquals(us.pochaev.dfs.scanner.domain.model.JobStatus.COMPLETED,
                    awaitExecution(context.getBean(GetPlanExecutionStatus.class), id));
        });
        runner().run(context -> assertEquals(Path.of(System.getProperty("user.home"), ".dfs", "manifests"),
                context.getBean(us.pochaev.dfs.scanner.domain.model.ExecutionConfig.class).manifestFolder()));
        Path neverCreated = tmp.resolve("fresh-" + java.util.UUID.randomUUID());
        runner().withPropertyValues("dfs.execution.manifest-folder=" + neverCreated).run(context -> {
            assertEquals(neverCreated, context.getBean(us.pochaev.dfs.scanner.domain.model.ExecutionConfig.class)
                    .manifestFolder());
            assertFalse(java.nio.file.Files.exists(neverCreated));
        });
    }

    @Test
    @DisplayName("DFS-051.AC-20 An app's own planExecutionExecutor replaces the default")
    void appPlanExecutionExecutorReplacesDefault() throws Exception {
        Exec fx = execFixture();
        CountingExecutor mine = new CountingExecutor();
        try {
            executionRunner().withBean("planExecutionExecutor", ExecutorService.class, () -> mine).run(context -> {
                us.pochaev.dfs.scanner.domain.model.PlanExecutionId id =
                        startExec(context.getBean(StartPlanExecution.class), fx);
                assertEquals(us.pochaev.dfs.scanner.domain.model.JobStatus.COMPLETED,
                        awaitExecution(context.getBean(GetPlanExecutionStatus.class), id));
                assertSame(mine, context.getBean("planExecutionExecutor"));
                assertTrue(mine.count.get() >= 1, "tasks counted: " + mine.count.get());
            });
        } finally {
            mine.shutdownNow();
        }
    }

    @Test
    @DisplayName("DFS-051.AC-20 An app's own StartPlanExecution bean replaces the default")
    void appStartPlanExecutionReplacesDefault() throws Exception {
        Exec fx = execFixture();
        StartPlanExecution mine = new StartPlanExecution() {
            @Override
            public Result<us.pochaev.dfs.scanner.domain.model.PlanExecutionId> start(ScanResult scan,
                                                                                         DeletionPlan plan) {
                return new Result.Failure<>(ErrorCode.PLAN_EDITED, "mine");
            }

            @Override
            public Result<us.pochaev.dfs.scanner.domain.model.PlanExecutionId> start(ScanResult scan,
                    DeletionPlan plan, us.pochaev.dfs.scanner.domain.model.ExecutionListener listener) {
                return new Result.Failure<>(ErrorCode.PLAN_EDITED, "mine");
            }
        };
        // exercise the factory first: it is the default the bean backs off from
        PlanExecutionUseCases own = ScannerFactories.planExecutionUseCases(Runnable::run,
                new us.pochaev.dfs.scanner.domain.model.ExecutionConfig(tmp.resolve("own"), List.of(tmp)));
        us.pochaev.dfs.scanner.domain.model.PlanExecutionId ran = startExec(own.start(), fx);
        assertEquals(us.pochaev.dfs.scanner.domain.model.JobStatus.COMPLETED, awaitExecution(own.status(), ran));
        executionRunner().withBean("myStartPlanExecution", StartPlanExecution.class, () -> mine).run(context -> {
            assertSame(mine, context.getBean(StartPlanExecution.class));
            assertEquals(1, context.getBeansOfType(StartPlanExecution.class).size());
            assertEquals(1, context.getBeansOfType(GetPlanExecutionStatus.class).size());
        });
    }

    @Test
    @DisplayName("DFS-055.AC-3 The plan execution bean holds the manifest folder's lock from start until the run is cancelled while queued")
    void planExecutionBeanHoldsTheManifestFolderLock() throws Exception {
        Exec fx = execFixture();
        java.util.List<Runnable> queued = new java.util.concurrent.CopyOnWriteArrayList<>();
        // an executor that never runs a task, so the run stays QUEUED
        executionRunner().withBean("planExecutionExecutor", java.util.concurrent.Executor.class,
                () -> queued::add).run(context -> {
            // start through the bean first: this is the use case that reaches the stub
            us.pochaev.dfs.scanner.domain.model.PlanExecutionId id =
                    startExec(context.getBean(StartPlanExecution.class), fx);
            assertEquals(1, queued.size());
            Path lockFile = tmp.resolve("manifests").resolve("execution.lock");
            assertTrue(java.nio.file.Files.exists(lockFile));
            // one channel stays open for the whole check: closing any channel of a file can release a JVM's locks
            try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(lockFile,
                    java.nio.file.StandardOpenOption.WRITE)) {
                try {
                    channel.tryLock();
                    org.junit.jupiter.api.Assertions.fail("the JVM should already hold the lock");
                } catch (java.nio.channels.OverlappingFileLockException expected) {
                    // expected
                }

                assertInstanceOf(Result.Success.class, context.getBean(CancelPlanExecution.class).cancel(id));

                java.nio.channels.FileLock lock = channel.tryLock();
                org.junit.jupiter.api.Assertions.assertNotNull(lock, "the lock should be free after the cancel");
                lock.release();
            }
        });
    }
}
