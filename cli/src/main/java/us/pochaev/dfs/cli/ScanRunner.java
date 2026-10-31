package us.pochaev.dfs.cli;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;
import us.pochaev.dfs.scanner.domain.usecases.command.StartScan;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanResult;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanStatus;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

/**
 * Runs {@link ScanCommand} on the process's arguments and streams, and hands its exit code to
 * {@code SpringApplication.exit} (DFS-020 decision 2). Stdout is written as UTF-8 whatever the
 * platform's console encoding, so every path survives a redirect to a file (DFS-023 decision 7).
 */
@Component
class ScanRunner implements ApplicationRunner, ExitCodeGenerator {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    private final ScanCommand command;
    private int exitCode;

    ScanRunner(StartScan start, GetScanStatus status, GetScanResult result) {
        this.command = new ScanCommand(start, status, result, POLL_INTERVAL, Clock.systemUTC());
    }

    @Override
    public void run(ApplicationArguments args) {
        PrintStream out = new PrintStream(System.out, false, StandardCharsets.UTF_8);
        exitCode = command.run(args.getSourceArgs(), out, System.err);
        out.flush();
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
