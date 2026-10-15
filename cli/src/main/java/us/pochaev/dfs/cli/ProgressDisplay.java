package us.pochaev.dfs.cli;

import us.pochaev.dfs.scanner.domain.model.ScanEvent;
import us.pochaev.dfs.scanner.domain.model.ScanListener;
import us.pochaev.dfs.scanner.domain.model.ScanPhase;

import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Iterator;
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
    /**
     * The files rate, once bytes are nearly done, runs from the newest progress report at least this old
     * (DFS-025 decision 1; 5 s, since hashing speeds up through the small-file tail and a longer look-back
     * overestimates).
     */
    private static final long FILES_RATE_WINDOW = Duration.ofSeconds(5).toNanos();
    /** From this percentage of bytes on, the ETA follows the recent files rate (DFS-025 decision 1). */
    private static final int FILES_RATE_FROM_PERCENT = 99;

    private final Mode mode;
    private final PrintStream err;
    private final LongSupplier nanos;
    private final long started;

    private ScanPhase phase;
    private long phaseStarted;
    private long lastLog;
    /** The last line written in {@link Mode#LOG}, without its clock; a finished line equal to it is not repeated. */
    private String lastLogged;
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
    /** The Sampling phase's time per sampled file, 0 when it sampled none; added per file left to the ETA. */
    private double nanosPerSampledFile;
    /** {@code (time, filesDone)} of recent hashing progress, oldest first, for the files rate. */
    private final ArrayDeque<long[]> hashSamples = new ArrayDeque<>();

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
                long now = nanos.getAsLong();
                if (phase == ScanPhase.PARTIAL_HASH) {
                    nanosPerSampledFile = filesDone == 0 ? 0 : (double) (now - phaseStarted) / filesDone;
                }
                hashSamples.clear();
                if (e.phase() == ScanPhase.FULL_HASH) {
                    hashSamples.add(new long[] {now, 0});
                }
                phase = e.phase();
                lastLogged = null;
                phaseStarted = now;
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
                } else if (e.phase() == ScanPhase.FULL_HASH) {
                    addHashSample(nanos.getAsLong(), e.filesDone());
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
            log();
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
        return phaseText() + "  " + clock(nanos.getAsLong() - started);
    }

    /** Writes the current line in {@link Mode#LOG} and remembers it. */
    private void log() {
        lastLogged = phaseText();
        err.print(line() + "\n");
        err.flush();
    }

    /** The current phase's line without the elapsed clock (DFS-028 decision 4); {@code phase} is not null. */
    private String phaseText() {
        return switch (phase) {
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
        } else if (mode == Mode.LOG && !phaseText().equals(lastLogged)) {
            log();
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

    /**
     * The hashing ETA (DFS-025): before 99% of bytes, the bytes left at the average byte rate plus the
     * files left at the Sampling phase's time per file; from 99% on, the files left at the files rate
     * since the newest report at least {@link #FILES_RATE_WINDOW} old, or the first estimate when no file
     * finished since then.
     */
    private String eta() {
        long now = nanos.getAsLong();
        long elapsed = now - phaseStarted;
        if (bytesDone == 0 || elapsed < ETA_MIN_TIME || bytesDone * 100.0 < bytesTotal) {
            return "--:--";
        }
        long filesLeft = Math.max(0, filesTotal - filesDone);
        if (bytesDone * 100.0 >= (double) FILES_RATE_FROM_PERCENT * bytesTotal && !hashSamples.isEmpty()) {
            long[] base = filesRateBase(now);
            long finished = filesDone - base[1];
            if (finished > 0) {
                return clock(Math.round((double) filesLeft * (now - base[0]) / finished));
            }
        }
        double remaining = (double) (bytesTotal - bytesDone) * elapsed / bytesDone + filesLeft * nanosPerSampledFile;
        return clock(Math.round(remaining));
    }

    /** Records hashing progress, keeping the newest sample at least one window old as the rate's base. */
    private void addHashSample(long now, long done) {
        hashSamples.add(new long[] {now, done});
        while (hashSamples.size() > 1) {
            Iterator<long[]> it = hashSamples.iterator();
            it.next();
            if (it.next()[0] > now - FILES_RATE_WINDOW) {
                break;
            }
            hashSamples.removeFirst();
        }
    }

    /** The newest sample at least one window before {@code now}, or the oldest kept when none is that old. */
    private long[] filesRateBase(long now) {
        long[] base = hashSamples.getFirst();
        for (long[] sample : hashSamples) {
            if (sample[0] > now - FILES_RATE_WINDOW) {
                break;
            }
            base = sample;
        }
        return base;
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
