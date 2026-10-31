package us.pochaev.dfs.cli;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.FolderTotal;
import us.pochaev.dfs.scanner.domain.model.PhysicalCopy;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.model.ScanSnapshot;
import us.pochaev.dfs.scanner.domain.model.ScanStatus;
import us.pochaev.dfs.scanner.domain.model.WalkEvent;
import us.pochaev.dfs.scanner.domain.usecases.command.StartScan;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanResult;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanStatus;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The scan command (DFS-020): parses the arguments, runs one scan, waits for it and writes the
 * result to {@code out} as JSON Lines, or as a text report with {@code --format=text} (DFS-023).
 * Kept free of Spring so it can be tested with plain streams and fake ports (decision 8);
 * {@link ScanRunner} wires it to the process.
 *
 * <p>Stdout carries only JSONL (format version {@value #SCHEMA_VERSION}, DFS-030): one {@code scan}
 * header line (roots, settings, start and finish times), a {@code folder} line per folder total, a
 * {@code set} line per duplicate set, a {@code skipped} line per skipped path, then one
 * {@code summary} line (DFS-020 decision 3, DFS-030 decision 5). The start and finish times come
 * from the injected {@link Clock}, read once just before the scan starts and once after it completes. Progress and errors go to {@code err}
 * (decision 4), shown by {@link ProgressDisplay} (DFS-024). Exit codes: 0 completed, 1 refused start or failed scan, 2 usage error (decision 6).
 * With {@code --output=<file>} the report goes to that file instead of {@code out}, through a {@link ReportFile} (DFS-029).
 */
public final class ScanCommand {

    public static final int OK = 0;
    public static final int ERROR = 1;
    public static final int USAGE = 2;

    static final String USAGE_LINE =
            "usage: duplicate-file-scanner <folder>... [--min-size=<bytes>] [--format=jsonl|text]"
                    + " [--progress=live|log|off] [--no-default-excludes] [--output=<file>]";
    private static final String NO_DEFAULT_EXCLUDES = "--no-default-excludes";
    private static final String MIN_SIZE = "--min-size=";
    private static final String FORMAT = "--format=";
    private static final String PROGRESS = "--progress=";
    private static final String OUTPUT = "--output=";

    /** The JSONL format version written in the {@code scan} line; bumped only for breaking changes (DFS-030 decision 7). */
    static final int SCHEMA_VERSION = 2;

    private final StartScan start;
    private final GetScanStatus status;
    private final GetScanResult result;
    private final Duration pollInterval;
    private final ReportFile.Opener opener;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();

    /** @param clock gives the scan's start and finish times written in the report header (DFS-030 decision 4) */
    public ScanCommand(StartScan start, GetScanStatus status, GetScanResult result, Duration pollInterval,
            Clock clock) {
        this(start, status, result, pollInterval, clock, file -> Files.newOutputStream(file));
    }

    /** @param opener opens the {@code --output} report's temporary file; replaced in tests to make the write fail */
    ScanCommand(StartScan start, GetScanStatus status, GetScanResult result, Duration pollInterval,
            Clock clock, ReportFile.Opener opener) {
        this.start = Objects.requireNonNull(start, "start");
        this.status = Objects.requireNonNull(status, "status");
        this.result = Objects.requireNonNull(result, "result");
        this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.opener = Objects.requireNonNull(opener, "opener");
    }

    /** Runs the command and returns the process exit code; never calls {@code System.exit}. */
    public int run(String[] args, PrintStream out, PrintStream err) {
        List<Path> folders = new ArrayList<>();
        ScanConfig defaults = ScanConfig.defaults();
        long minSize = defaults.minSize();
        String format = null;
        String progress = null;
        boolean noDefaultExcludes = false;
        String output = null;
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
            } else if (arg.startsWith(PROGRESS)) {
                if (progress != null) {
                    return usage(err, "--progress given more than once");
                }
                progress = arg.substring(PROGRESS.length());
                if (!progress.equals("live") && !progress.equals("log") && !progress.equals("off")) {
                    return usage(err, "--progress must be live, log or off: " + progress);
                }
            } else if (arg.equals(NO_DEFAULT_EXCLUDES)) {
                if (noDefaultExcludes) {
                    return usage(err, NO_DEFAULT_EXCLUDES + " given more than once");
                }
                noDefaultExcludes = true;
            } else if (arg.startsWith(OUTPUT)) {
                if (output != null) {
                    return usage(err, "--output given more than once");
                }
                output = arg.substring(OUTPUT.length());
                if (output.isEmpty()) {
                    return usage(err, "--output needs a file path");
                }
            } else if (arg.startsWith("--")) {
                return usage(err, "unknown option: " + arg);
            } else {
                try {
                    folders.add(Path.of(arg));
                } catch (InvalidPathException e) {
                    return usage(err, notValid(arg, e));
                }
            }
        }
        if (folders.isEmpty()) {
            return usage(err, "no folder given");
        }
        boolean text = "text".equals(format);
        ScanConfig config = new ScanConfig(minSize, defaults.sampleSize(), defaults.partialHashThreshold(),
                !noDefaultExcludes);
        ProgressDisplay.Mode mode =
                progress == null ? ProgressDisplay.Mode.LIVE : ProgressDisplay.Mode.valueOf(progress.toUpperCase());

        if (output == null) {
            return scan(folders, config, mode, text, null, out, err);
        }
        Path target;
        try {
            target = Path.of(output).toAbsolutePath();
        } catch (InvalidPathException e) {
            return usage(err, notValid(output, e));
        }
        Path parent = target.getParent();
        if (parent == null || Files.isDirectory(target)) {
            return usage(err, "--output is a folder: " + target);
        }
        if (!Files.exists(parent)) {
            return usage(err, "--output folder does not exist: " + parent);
        }
        if (!Files.isDirectory(parent)) {
            return usage(err, "--output folder is not a folder: " + parent);
        }
        ReportFile report;
        try {
            report = ReportFile.create(target, opener);
        } catch (IOException e) {
            return usage(err, "cannot create a file in " + parent + ": " + describe(e));
        }
        try {
            return scan(folders, config, mode, text, report, out, err);
        } finally {
            report.discard();
        }
    }

    /** Runs the scan and writes its report to {@code report}, or to {@code out} when it is null. */
    private int scan(List<Path> folders, ScanConfig config, ProgressDisplay.Mode mode, boolean text,
            ReportFile report, PrintStream out, PrintStream err) {
        ProgressDisplay display = new ProgressDisplay(mode, err, System::nanoTime);
        display.start();

        Instant startedAt = clock.instant();
        ScanId id;
        switch (start.start(folders, config, display)) {
            case Result.Success<ScanId> s -> id = s.value();
            case Result.Failure<ScanId> f -> {
                display.failed();
                return error(err, f.code() + ": " + f.message());
            }
        }

        ScanSnapshot last;
        try {
            last = await(id);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            display.failed();
            return error(err, "interrupted while waiting for the scan");
        } catch (IllegalStateException e) {
            display.failed();
            return error(err, e.getMessage());
        }
        if (last.status() != ScanStatus.COMPLETED) {
            display.failed();
            return last.status() == ScanStatus.FAILED
                    ? error(err, "scan failed: " + last.failureMessage())
                    : error(err, "scan " + last.status().name().toLowerCase());
        }
        Instant finishedAt = clock.instant();

        return switch (result.result(id)) {
            case Result.Success<ScanResult> s -> {
                display.completed(s.value().sets().size());
                if (report == null) {
                    write(s.value(), config, startedAt, finishedAt, text, out);
                    yield OK;
                }
                try {
                    report.commit(file -> write(s.value(), config, startedAt, finishedAt, text, file));
                } catch (IOException e) {
                    yield error(err, "could not write the report to " + report.target() + ": " + describe(e));
                }
                if (mode != ProgressDisplay.Mode.OFF) {
                    err.print("Report written to " + report.target() + "\n");
                    err.flush();
                }
                yield OK;
            }
            case Result.Failure<ScanResult> f -> {
                display.failed();
                yield error(err, f.code() + ": " + f.message());
            }
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

    private void write(ScanResult scan, ScanConfig config, Instant startedAt, Instant finishedAt, boolean text,
            PrintStream out) {
        if (text) {
            TextReport.write(scan, config, startedAt, finishedAt, out);
        } else {
            write(scan, config, startedAt, finishedAt, out);
        }
    }

    /** The JSONL report, lines in the order {@code scan}, {@code folder}, {@code set}, {@code skipped}, {@code summary}. */
    private void write(ScanResult scan, ScanConfig config, Instant startedAt, Instant finishedAt, PrintStream out) {
        line(out, scanLine(scan, config, startedAt, finishedAt));
        for (FolderTotal folder : scan.folders()) {
            line(out, folderLine(folder));
        }
        long files = 0;
        long reclaimable = 0;
        for (DuplicateSet set : scan.sets()) {
            ObjectNode line = json.createObjectNode();
            line.put("type", "set");
            line.put("size", set.size());
            line.put("digest", set.digest());
            line.set("copies", copies(set));
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
        summary.put("excluded", scan.excluded());
        line(out, summary);
        out.flush();
    }

    /**
     * The {@code scan} header line (DFS-030 decision 4): {@code type}, {@code schemaVersion}
     * ({@link #SCHEMA_VERSION}), {@code roots} (from {@link ScanResult#roots()}, as strings),
     * {@code minSize}, {@code sampleSize}, {@code partialHashThreshold}, {@code defaultExcludes}
     * (from {@code config}), {@code startedAt} and {@code finishedAt} ({@link Instant#toString()}, ISO-8601 UTC).
     */
    private ObjectNode scanLine(ScanResult scan, ScanConfig config, Instant startedAt, Instant finishedAt) {
        throw new UnsupportedOperationException("not implemented: DFS-030");
    }

    /** A {@code folder} line (DFS-030 decision 5): {@code type}, {@code path}, {@code files}, {@code bytes}. */
    private ObjectNode folderLine(FolderTotal folder) {
        throw new UnsupportedOperationException("not implemented: DFS-030");
    }

    /**
     * A set's {@code copies} array (DFS-030 decision 6): one array per {@link PhysicalCopy}, in
     * {@code set.copies()} order, holding one object per link, in {@code copy.links()} order, with
     * {@code path} (the path as a string) and {@code modified} ({@link FileEntry#lastModified()} as
     * {@link Instant#toString()}, full precision).
     */
    private ArrayNode copies(DuplicateSet set) {
        throw new UnsupportedOperationException("not implemented: DFS-030");
    }

    private void line(PrintStream out, ObjectNode node) {
        out.print(json.writeValueAsString(node));
        out.print('\n');
    }

    private static String notValid(String path, InvalidPathException e) {
        return "not a valid path: \"" + path + "\" (" + e.getReason()
                + (e.getIndex() < 0 ? "" : " at index " + e.getIndex()) + ")";
    }

    private static String describe(IOException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + e.getMessage();
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
}
