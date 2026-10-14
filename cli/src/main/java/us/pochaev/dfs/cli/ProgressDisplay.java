package us.pochaev.dfs.cli;

import us.pochaev.dfs.scanner.domain.model.ScanEvent;
import us.pochaev.dfs.scanner.domain.model.ScanListener;
import us.pochaev.dfs.scanner.domain.model.ScanPhase;

import java.io.PrintStream;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Shows a scan's progress on stderr (DFS-024): one line per phase, with folders, files and bytes
 * found while walking, files done while sampling, files, bytes, percentage and ETA while hashing,
 * and sets done while checking hard links, each followed by the time since the scan started.
 * <p>
 * In {@link Mode#LIVE} the current line is redrawn in place with {@code \r} by {@link #redraw},
 * which {@link #start} calls from a timer about every 200 ms, so the clock moves even while one
 * file is read; a phase's line is completed with a newline when the next phase starts. In
 * {@link Mode#LOG} plain lines are written: at most one every 5 seconds, and each phase's finished
 * line. {@link Mode#OFF} writes nothing. Events arrive on the scan's thread and redraws on the
 * timer's, so every method is synchronized.
 */
final class ProgressDisplay implements ScanListener {

    enum Mode { LIVE, LOG, OFF }

    private static final long REDRAW_MILLIS = 200;
    private static final long LOG_INTERVAL = Duration.ofSeconds(5).toNanos();
    private static final long ETA_MIN_TIME = Duration.ofSeconds(3).toNanos();

    private final Mode mode;
    private final PrintStream err;
    private final LongSupplier nanos;
    private final long started;

    private ScanPhase phase;
    private long phaseStarted;
    private long lastLog;
    private int drawnLength;
    private Thread timer;

    private long directories;
    private long files;
    private long bytesFound;
    private long compared;
    private long filesDone;
    private long filesTotal;
    private long bytesDone;
    private long bytesTotal;
    private long groupsDone;
    private long groupsTotal;

    /** @param nanos the clock, {@code System::nanoTime} outside tests; the scan starts when this is built */
    ProgressDisplay(Mode mode, PrintStream err, LongSupplier nanos) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.err = Objects.requireNonNull(err, "err");
        this.nanos = Objects.requireNonNull(nanos, "nanos");
        this.started = nanos.getAsLong();
    }

    @Override
    public synchronized void onEvent(ScanEvent event) {
        switch (event) {
            case ScanEvent.PhaseStarted e -> {
                finishLine();
                phase = e.phase();
                phaseStarted = nanos.getAsLong();
                lastLog = phaseStarted;
                filesDone = 0;
                filesTotal = 0;
                bytesDone = 0;
                bytesTotal = 0;
                groupsDone = 0;
                groupsTotal = 0;
                return;
            }
            case ScanEvent.DirectoryWalked e -> directories = e.directoriesSoFar();
            case ScanEvent.FileWalked e -> {
                files = e.filesSoFar();
                bytesFound = e.bytesSoFar();
            }
            case ScanEvent.HashProgress e -> {
                filesDone = e.filesDone();
                filesTotal = e.filesTotal();
                bytesDone = e.bytesDone();
                bytesTotal = e.bytesTotal();
                if (e.phase() == ScanPhase.PARTIAL_HASH) {
                    compared = e.filesTotal();
                }
            }
            case ScanEvent.GroupDone e -> {
                groupsDone = e.done();
                groupsTotal = e.total();
            }
            case ScanEvent.Skipped e -> {
                // listed in the output once the scan completes
            }
        }
        if (mode == Mode.LOG && phase != null && nanos.getAsLong() - lastLog >= LOG_INTERVAL) {
            lastLog = nanos.getAsLong();
            err.print(line() + "\n");
            err.flush();
        }
    }

    /** Redraws the current line in place ({@link Mode#LIVE} only); also called by the timer. */
    synchronized void redraw() {
        if (mode != Mode.LIVE || phase == null) {
            return;
        }
        draw("");
    }

    /** Starts the redraw timer in {@link Mode#LIVE}; does nothing otherwise. */
    synchronized void start() {
        if (mode != Mode.LIVE || timer != null) {
            return;
        }
        timer = Thread.ofPlatform().daemon().name("progress").start(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(REDRAW_MILLIS);
                    redraw();
                }
            } catch (InterruptedException e) {
                // stopped
            }
        });
    }

    /** The scan completed: finishes the current line and writes the {@code Done} line. */
    void completed(int sets) {
        stopTimer();
        synchronized (this) {
            finishLine();
            phase = null;
            if (mode != Mode.OFF) {
                err.print("Done in " + clock(nanos.getAsLong() - started) + ": " + TextReport.count(files)
                        + " files found, " + TextReport.count(compared) + " compared, " + TextReport.count(sets)
                        + " duplicate sets\n");
                err.flush();
            }
        }
    }

    /** The scan did not complete: finishes the current line, so an error written next starts on its own line. */
    void failed() {
        stopTimer();
        synchronized (this) {
            if (mode == Mode.LIVE) {
                finishLine();
            }
            phase = null;
        }
    }

    /** The current phase's line, without {@code \r} or newline; empty before the first phase. */
    synchronized String line() {
        if (phase == null) {
            return "";
        }
        String text = switch (phase) {
            case WALK -> label("Walking:") + TextReport.count(directories) + " folders, "
                    + TextReport.count(files) + " files, " + TextReport.size(bytesFound) + " found";
            case PARTIAL_HASH -> label("Sampling:") + TextReport.count(filesDone) + " / "
                    + TextReport.count(filesTotal) + " files";
            case FULL_HASH -> label("Hashing:") + TextReport.count(filesDone) + " / " + TextReport.count(filesTotal)
                    + " files, " + TextReport.size(bytesDone) + " / " + TextReport.size(bytesTotal)
                    + " (" + percent() + "%)  ETA " + eta();
            case HARD_LINKS -> label("Hard links:") + TextReport.count(groupsDone) + " / "
                    + TextReport.count(groupsTotal) + " sets";
        };
        return text + "  " + clock(nanos.getAsLong() - started);
    }

    private void stopTimer() {
        Thread t;
        synchronized (this) {
            t = timer;
            timer = null;
        }
        if (t != null) {
            t.interrupt();
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Writes the current phase's finished line, in {@link Mode#LIVE} and {@link Mode#LOG}. */
    private void finishLine() {
        if (phase == null) {
            return;
        }
        if (mode == Mode.LIVE) {
            draw("\n");
            drawnLength = 0;
        } else if (mode == Mode.LOG) {
            err.print(line() + "\n");
            err.flush();
        }
    }

    private void draw(String end) {
        String text = line();
        String pad = " ".repeat(Math.max(0, drawnLength - text.length()));
        err.print("\r" + text + pad + end);
        err.flush();
        drawnLength = text.length();
    }

    private long percent() {
        return bytesTotal == 0 ? 0 : bytesDone * 100 / bytesTotal;
    }

    private String eta() {
        long elapsed = nanos.getAsLong() - phaseStarted;
        if (bytesDone == 0 || elapsed < ETA_MIN_TIME || bytesDone * 100.0 < bytesTotal) {
            return "--:--";
        }
        double remaining = (double) (bytesTotal - bytesDone) * elapsed / bytesDone;
        return clock(Math.round(remaining));
    }

    private static String label(String label) {
        return String.format(Locale.ROOT, "%-12s", label);
    }

    /** {@code m:ss}, or {@code h:mm:ss} from one hour on. */
    static String clock(long nanos) {
        long seconds = Math.max(0, nanos) / 1_000_000_000L;
        long h = seconds / 3600;
        long m = seconds % 3600 / 60;
        long s = seconds % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%d:%02d", m, s);
    }
}
