package us.pochaev.dfs.cli;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;
import us.pochaev.dfs.scanner.domain.usecases.command.StartScan;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanResult;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanStatus;

import java.time.Duration;

/**
 * Runs {@link ScanCommand} on the process's arguments and streams, and hands its exit code to
 * {@code SpringApplication.exit} (DFS-020 decision 2).
 */
@Component
class ScanRunner implements ApplicationRunner, ExitCodeGenerator {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    private final ScanCommand command;
    private int exitCode;

    ScanRunner(StartScan start, GetScanStatus status, GetScanResult result) {
        this.command = new ScanCommand(start, status, result, POLL_INTERVAL);
    }

    @Override
    public void run(ApplicationArguments args) {
        exitCode = command.run(args.getSourceArgs(), System.out, System.err);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
