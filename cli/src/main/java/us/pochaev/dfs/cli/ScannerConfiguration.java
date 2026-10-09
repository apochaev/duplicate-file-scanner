package us.pochaev.dfs.cli;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import us.pochaev.dfs.scanner.domain.factory.ScanUseCases;
import us.pochaev.dfs.scanner.domain.factory.ScannerFactories;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.domain.model.ScanListener;
import us.pochaev.dfs.scanner.domain.usecases.command.CancelScan;
import us.pochaev.dfs.scanner.domain.usecases.command.StartScan;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanResult;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanStatus;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The scanner's Spring wiring (CLAUDE.md decision 6): the {@code @Bean}s call the factories, and
 * the scanner itself stays Spring-free. All four ports come from one
 * {@link ScannerFactories#scanUseCases} call, so they share one scan repository.
 *
 * <p>The factory returns one service object behind all four ports, so each bean wraps its port in
 * a delegate of that interface alone: otherwise every bean would match every port type and an
 * injection point such as {@code GetScanStatus} would find four candidates.
 */
@Configuration(proxyBeanMethods = false)
public class ScannerConfiguration {

    /**
     * Virtual threads, as CLAUDE.md prescribes for the CLI. Closed with {@code shutdownNow}, not
     * {@code close}: {@code close} waits for a running scan, which would make Ctrl+C hang until
     * the scan finished (DFS-020 decision 7 leaves Ctrl+C to the JVM).
     */
    @Bean(destroyMethod = "shutdownNow")
    ExecutorService scanExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    ScanUseCases scanUseCases(ExecutorService scanExecutor) {
        return ScannerFactories.scanUseCases(scanExecutor);
    }

    @Bean
    StartScan startScan(ScanUseCases useCases) {
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

    @Bean
    CancelScan cancelScan(ScanUseCases useCases) {
        return useCases.cancel()::cancel;
    }

    @Bean
    GetScanStatus getScanStatus(ScanUseCases useCases) {
        return useCases.status()::status;
    }

    @Bean
    GetScanResult getScanResult(ScanUseCases useCases) {
        return useCases.result()::result;
    }
}
