package us.pochaev.dfs.scannerspring;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import us.pochaev.dfs.scanner.domain.factory.PlanVerifyUseCases;
import us.pochaev.dfs.scanner.domain.factory.ScanUseCases;
import us.pochaev.dfs.scanner.domain.factory.ScannerFactories;
import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.PlanVerifyId;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.domain.model.ScanListener;
import us.pochaev.dfs.scanner.domain.model.VerifyListener;
import us.pochaev.dfs.scanner.domain.usecases.command.CancelPlanVerify;
import us.pochaev.dfs.scanner.domain.usecases.command.CancelScan;
import us.pochaev.dfs.scanner.domain.usecases.command.StartPlanVerify;
import us.pochaev.dfs.scanner.domain.usecases.command.StartScan;
import us.pochaev.dfs.scanner.domain.usecases.query.CheckPlanDrift;
import us.pochaev.dfs.scanner.domain.usecases.query.GetPlanVerifyResult;
import us.pochaev.dfs.scanner.domain.usecases.query.GetPlanVerifyStatus;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanResult;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanStatus;
import us.pochaev.dfs.scanner.domain.usecases.query.ListDirectory;
import us.pochaev.dfs.scanner.domain.usecases.query.PreviewDeletionPlan;
import us.pochaev.dfs.scanner.domain.usecases.query.VerifyPlanContent;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The scanner's shared Spring wiring (CLAUDE.md decision 6, DFS-031), registered through
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}: the {@code @Bean}s call
 * the factories, and the scanner itself stays Spring-free. All four ports come from one
 * {@link ScannerFactories#scanUseCases} call, so they share one scan repository.
 *
 * <p>The factory returns one service object behind all four ports, so each bean wraps its port in a delegate of that
 * interface alone: otherwise every bean would match every port type, an injection point such as
 * {@code GetScanStatus} would find four candidates, and {@code @ConditionalOnMissingBean(StartScan.class)} could be
 * satisfied by a bean of another port (wiring checklist item 2).
 *
 * <p>Every bean backs off when the app declares its own: {@link ScanUseCases} and each port by type, the executor by
 * the name {@code scanExecutor}. A port the app replaces no longer shares the scan repository with the others: a scan
 * started through an app's own {@code StartScan} is unknown to the autoconfigured {@code GetScanStatus}.
 */
@AutoConfiguration
public class ScannerAutoConfiguration {

    /**
     * Virtual threads, one per task. Closed with {@code shutdownNow}, not {@code close}: {@code close} waits for a
     * running scan, which would make Ctrl+C hang until the scan finished (DFS-020 decision 7; wiring checklist
     * item 3). An app supplies its own pool by declaring a bean named {@code scanExecutor}.
     */
    @Bean(destroyMethod = "shutdownNow")
    @ConditionalOnMissingBean(name = "scanExecutor")
    public ExecutorService scanExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Takes the executor by the name {@code scanExecutor}, never by type, so it cannot clash with Spring Boot's
     * {@code applicationTaskExecutor} or any other {@link Executor} in the context.
     */
    @Bean
    @ConditionalOnMissingBean
    public ScanUseCases scanUseCases(@Qualifier("scanExecutor") Executor scanExecutor) {
        return ScannerFactories.scanUseCases(scanExecutor);
    }

    /** {@code StartScan} has two methods, so an anonymous class delegating both. */
    @Bean
    @ConditionalOnMissingBean
    public StartScan startScan(ScanUseCases useCases) {
        StartScan start = useCases.start();
        return new StartScan() {
            @Override
            public Result<ScanId> start(List<Path> locations, ScanConfig config) {
                return start.start(locations, config);
            }

            @Override
            public Result<ScanId> start(List<Path> locations, ScanConfig config, ScanListener listener) {
                return start.start(locations, config, listener);
            }
        };
    }

    /** A method reference, an instance of {@code CancelScan} alone. */
    @Bean
    @ConditionalOnMissingBean
    public CancelScan cancelScan(ScanUseCases useCases) {
        return useCases.cancel()::cancel;
    }

    /** A method reference, an instance of {@code GetScanStatus} alone. */
    @Bean
    @ConditionalOnMissingBean
    public GetScanStatus getScanStatus(ScanUseCases useCases) {
        return useCases.status()::status;
    }

    /** A method reference, an instance of {@code GetScanResult} alone. */
    @Bean
    @ConditionalOnMissingBean
    public GetScanResult getScanResult(ScanUseCases useCases) {
        return useCases.result()::result;
    }

    /**
     * The deletion-plan preview (DFS-022 decision 8), straight from its own factory: it holds no scan state, so it is
     * independent of {@link ScanUseCases}, and its implementation serves this one port only, so it needs no delegate.
     */
    @Bean
    @ConditionalOnMissingBean
    public PreviewDeletionPlan previewDeletionPlan() {
        return ScannerFactories.previewDeletionPlan();
    }

    /**
     * The one-level folder listing (DFS-032 decision 6), straight from its own factory like the preview: stateless,
     * independent of {@link ScanUseCases}, and its implementation serves this one port only.
     */
    @Bean
    @ConditionalOnMissingBean
    public ListDirectory listDirectory() {
        return ScannerFactories.listDirectory();
    }

    /**
     * The plan drift check (DFS-033 decision 7), straight from its own factory like the preview: stateless,
     * independent of {@link ScanUseCases}, and its implementation serves this one port only.
     */
    @Bean
    @ConditionalOnMissingBean
    public CheckPlanDrift checkPlanDrift() {
        return ScannerFactories.checkPlanDrift();
    }

    /**
     * The plan content verify (DFS-034 decision 7), straight from its own factory like the preview: stateless,
     * synchronous, independent of {@link ScanUseCases}, and its implementation serves this one port only.
     */
    @Bean
    @ConditionalOnMissingBean
    public VerifyPlanContent verifyPlanContent() {
        return ScannerFactories.verifyPlanContent();
    }

    /**
     * Virtual threads, one per plan verify job (DFS-042 decision 6), separate from {@code scanExecutor} so an app sizes
     * or replaces verify work without touching scans. Closed with {@code shutdownNow} for the same reason as the scan
     * executor. An app supplies its own by declaring a bean named {@code planVerifyExecutor}.
     */
    @Bean(destroyMethod = "shutdownNow")
    @ConditionalOnMissingBean(name = "planVerifyExecutor")
    public ExecutorService planVerifyExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Takes the executor by the name {@code planVerifyExecutor}, never by type, like {@link #scanUseCases}. All four
     * plan verify ports come from this one set, so they share one job store.
     */
    @Bean
    @ConditionalOnMissingBean
    public PlanVerifyUseCases planVerifyUseCases(@Qualifier("planVerifyExecutor") Executor planVerifyExecutor) {
        return ScannerFactories.planVerifyUseCases(planVerifyExecutor);
    }

    /** {@code StartPlanVerify} has two methods, so an anonymous class delegating both, as for {@link StartScan}. */
    @Bean
    @ConditionalOnMissingBean
    public StartPlanVerify startPlanVerify(PlanVerifyUseCases useCases) {
        StartPlanVerify start = useCases.start();
        return new StartPlanVerify() {
            @Override
            public Result<PlanVerifyId> start(DeletionPlan plan) {
                return start.start(plan);
            }

            @Override
            public Result<PlanVerifyId> start(DeletionPlan plan, VerifyListener listener) {
                return start.start(plan, listener);
            }
        };
    }

    /** A method reference, an instance of {@code CancelPlanVerify} alone. */
    @Bean
    @ConditionalOnMissingBean
    public CancelPlanVerify cancelPlanVerify(PlanVerifyUseCases useCases) {
        return useCases.cancel()::cancel;
    }

    /** A method reference, an instance of {@code GetPlanVerifyStatus} alone. */
    @Bean
    @ConditionalOnMissingBean
    public GetPlanVerifyStatus getPlanVerifyStatus(PlanVerifyUseCases useCases) {
        return useCases.status()::status;
    }

    /** A method reference, an instance of {@code GetPlanVerifyResult} alone. */
    @Bean
    @ConditionalOnMissingBean
    public GetPlanVerifyResult getPlanVerifyResult(PlanVerifyUseCases useCases) {
        return useCases.result()::result;
    }
}
