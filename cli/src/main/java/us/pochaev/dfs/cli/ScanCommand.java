package us.pochaev.dfs.cli;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.PhysicalCopy;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.scanner.domain.model.ScanEvent;
import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.domain.model.ScanListener;
import us.pochaev.dfs.scanner.domain.model.ScanPhase;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.model.ScanSnapshot;
import us.pochaev.dfs.scanner.domain.model.ScanStatus;
import us.pochaev.dfs.scanner.domain.model.WalkEvent;
import us.pochaev.dfs.scanner.domain.usecases.command.StartScan;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanResult;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanStatus;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The scan command (DFS-020): parses the arguments, runs one scan, waits for it and writes the
 * result to {@code out} as JSON Lines, or as a text report with {@code --format=text} (DFS-023).
 * Kept free of Spring so it can be tested with plain streams and fake ports (decision 8);
 * {@link ScanRunner} wires it to the process.
 *
 * <p>Stdout carries only JSONL: a {@code set} line per duplicate set, a {@code skipped} line per
 * skipped path, then one {@code summary} line (decision 3). Progress and errors go to {@code err}
 * (decision 4). Exit codes: 0 completed, 1 refused start or failed scan, 2 usage error (decision 6).
 */
public final class ScanCommand {

    public static final int OK = 0;
    public static final int ERROR = 1;
    public static final int USAGE = 2;

    static final String USAGE_LINE =
            "usage: duplicate-file-scanner <folder>... [--min-size=<bytes>] [--format=jsonl|text]";
    private static final String MIN_SIZE = "--min-size=";
    private static final String FORMAT = "--format=";
    private static final Duration PROGRESS_INTERVAL = Duration.ofSeconds(1);

    private final StartScan start;
    private final GetScanStatus status;
    private final GetScanResult result;
    private final Duration pollInterval;
    private final JsonMapper json = JsonMapper.builder().build();

    public ScanCommand(StartScan start, GetScanStatus status, GetScanResult result, Duration pollInterval) {
        this.start = Objects.requireNonNull(start, "start");
        this.status = Objects.requireNonNull(status, "status");
        this.result = Objects.requireNonNull(result, "result");
        this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval");
    }

    /** Runs the command and returns the process exit code; never calls {@code System.exit}. */
    public int run(String[] args, PrintStream out, PrintStream err) {
        List<Path> folders = new ArrayList<>();
        ScanConfig defaults = ScanConfig.defaults();
        long minSize = defaults.minSize();
        String format = null;
        for (String arg : args) {
            if (arg.startsWith(MIN_SIZE)) {
                String value = arg.substring(MIN_SIZE.length());
                try {
                    minSize = Long.parseLong(value);
                } catch (NumberFormatException e) {
                    return usage(err, "--min-size must be a whole number of bytes: " + value);
                }
                if (minSize < 1) {
                    return usage(err, "--min-size must be at least 1: " + value);
                }
            } else if (arg.startsWith(FORMAT)) {
                if (format != null) {
                    return usage(err, "--format given more than once");
                }
                format = arg.substring(FORMAT.length());
                if (!format.equals("jsonl") && !format.equals("text")) {
                    return usage(err, "--format must be jsonl or text: " + format);
                }
            } else if (arg.startsWith("--")) {
                return usage(err, "unknown option: " + arg);
            } else {
                folders.add(Path.of(arg));
            }
        }
        if (folders.isEmpty()) {
            return usage(err, "no folder given");
        }
        boolean text = "text".equals(format);
        ScanConfig config = new ScanConfig(minSize, defaults.sampleSize(), defaults.partialHashThreshold());

        ScanId id;
        switch (start.start(folders, config, new Progress(err))) {
            case Result.Success<ScanId> s -> id = s.value();
            case Result.Failure<ScanId> f -> {
                return error(err, f.code() + ": " + f.message());
            }
        }

        ScanSnapshot last;
        try {
            last = await(id);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return error(err, "interrupted while waiting for the scan");
        } catch (IllegalStateException e) {
            return error(err, e.getMessage());
        }
        if (last.status() == ScanStatus.FAILED) {
            return error(err, "scan failed: " + last.failureMessage());
        }
        if (last.status() != ScanStatus.COMPLETED) {
            return error(err, "scan " + last.status().name().toLowerCase());
        }

        return switch (result.result(id)) {
            case Result.Success<ScanResult> s -> {
                if (text) {
                    TextReport.write(s.value(), out);
                } else {
                    write(s.value(), out);
                }
                yield OK;
            }
            case Result.Failure<ScanResult> f -> error(err, f.code() + ": " + f.message());
        };
    }

    /** Polls until the scan is COMPLETED, FAILED or CANCELLED (decision 7: no completion event). */
    private ScanSnapshot await(ScanId id) throws InterruptedException {
        while (true) {
            ScanSnapshot snapshot = switch (status.status(id)) {
                case Result.Success<ScanSnapshot> s -> s.value();
                case Result.Failure<ScanSnapshot> f -> throw new IllegalStateException(f.code() + ": " + f.message());
            };
            switch (snapshot.status()) {
                case COMPLETED, FAILED, CANCELLED -> {
                    return snapshot;
                }
                default -> Thread.sleep(pollInterval);
            }
        }
    }

    private void write(ScanResult scan, PrintStream out) {
        long files = 0;
        long reclaimable = 0;
        for (DuplicateSet set : scan.sets()) {
            ObjectNode line = json.createObjectNode();
            line.put("type", "set");
            line.put("size", set.size());
            line.put("digest", set.digest());
            ArrayNode copies = line.putArray("copies");
            for (PhysicalCopy copy : set.copies()) {
                ArrayNode links = copies.addArray();
                for (FileEntry link : copy.links()) {
                    links.add(link.path().toString());
                }
            }
            line(out, line);
            files += set.files().size();
            reclaimable += set.size() * (set.copies().size() - 1);
        }
        for (WalkEvent.Skipped skipped : scan.skipped()) {
            ObjectNode line = json.createObjectNode();
            line.put("type", "skipped");
            line.put("path", skipped.path().toString());
            line.put("reason", skipped.reason().name());
            line.put("detail", skipped.detail());
            line(out, line);
        }
        ObjectNode summary = json.createObjectNode();
        summary.put("type", "summary");
        summary.put("sets", scan.sets().size());
        summary.put("files", files);
        summary.put("skipped", scan.skipped().size());
        summary.put("reclaimableBytes", reclaimable);
        line(out, summary);
        out.flush();
    }

    private void line(PrintStream out, ObjectNode node) {
        out.print(json.writeValueAsString(node));
        out.print('\n');
    }

    private static int usage(PrintStream err, String problem) {
        err.println("error: " + problem);
        err.println(USAGE_LINE);
        return USAGE;
    }

    private static int error(PrintStream err, String message) {
        err.println("error: " + message);
        return ERROR;
    }

    /**
     * Writes progress to stderr: a line per phase start, and walk and group counts at most once
     * per {@link #PROGRESS_INTERVAL}. Called on the scanning thread.
     */
    private static final class Progress implements ScanListener {

        private final PrintStream err;
        private long lastCount = System.nanoTime();

        Progress(PrintStream err) {
            this.err = err;
        }

        @Override
        public void onEvent(ScanEvent event) {
            switch (event) {
                case ScanEvent.PhaseStarted e -> {
                    err.println("scan: " + phaseName(e.phase()) + " started");
                    lastCount = System.nanoTime();
                }
                case ScanEvent.FileWalked e -> {
                    if (due()) {
                        err.println("scan: walk " + e.filesSoFar() + " files");
                    }
                }
                case ScanEvent.GroupDone e -> {
                    if (due()) {
                        err.println("scan: " + phaseName(e.phase()) + " " + e.done() + "/" + e.total() + " groups");
                    }
                }
                case ScanEvent.Skipped e -> {
                    // reported as a "skipped" JSONL line once the scan completes
                }
            }
        }

        private boolean due() {
            long now = System.nanoTime();
            if (now - lastCount < PROGRESS_INTERVAL.toNanos()) {
                return false;
            }
            lastCount = now;
            return true;
        }

        private static String phaseName(ScanPhase phase) {
            return phase.name().toLowerCase().replace('_', ' ');
        }
    }
}
