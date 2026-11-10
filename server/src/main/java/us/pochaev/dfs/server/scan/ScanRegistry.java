package us.pochaev.dfs.server.scan;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.scanner.domain.model.ScanEvent;
import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.domain.model.ScanListener;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.model.ScanSnapshot;
import us.pochaev.dfs.scanner.domain.model.ScanStatus;
import us.pochaev.dfs.scanner.domain.usecases.command.CancelScan;
import us.pochaev.dfs.scanner.domain.usecases.command.StartScan;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanResult;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanStatus;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * The scans this server run started (DFS-040 decision 2), each with the counts its listener folds from the core's
 * events (decision 3). The listener runs on the scanning thread and only writes memory, so a slow client can never
 * hold up a scan; it never throws, since the core detaches a listener that does.
 * <p>
 * Public since DFS-047 for {@link #completedState}: other feature packages keep per-scan state beside a scan's entry
 * here (decision 6). Everything else stays package-private.
 */
@Component
public class ScanRegistry {

    private final StartScan startScan;
    private final CancelScan cancelScan;
    private final GetScanStatus getScanStatus;
    private final GetScanResult getScanResult;
    private final Clock clock;
    private final Map<UUID, Tracked> scans = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final ScheduledExecutorService sweeper;

    /**
     * The Spring constructor (DFS-064 decision 3): the {@link Clock} bean when the application context has one (a
     * test supplies a fake), else the system UTC clock.
     */
    @Autowired
    ScanRegistry(StartScan startScan, CancelScan cancelScan, GetScanStatus getScanStatus, GetScanResult getScanResult,
                 ObjectProvider<Clock> clock) {
        this(startScan, cancelScan, getScanStatus, getScanResult, clock.getIfAvailable(Clock::systemUTC));
    }

    /** Built with an explicit clock, for a test that drives the clock itself. */
    ScanRegistry(StartScan startScan, CancelScan cancelScan, GetScanStatus getScanStatus, GetScanResult getScanResult,
                 Clock clock) {
        this.startScan = startScan;
        this.cancelScan = cancelScan;
        this.getScanStatus = getScanStatus;
        this.getScanResult = getScanResult;
        this.clock = clock;
        this.sweeper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread th = new Thread(r, "scan-registry-sweep");
            th.setDaemon(true);
            return th;
        });
        sweeper.scheduleWithFixedDelay(this::sweep, 50, 50, TimeUnit.MILLISECONDS);
    }

    /**
     * Stamps {@code finishedAt} on every scan that has ended and has none yet, from the registry's clock (DFS-064
     * decision 3). The core sends no event when a scan ends, so the registry looks: a scheduled call every 50 ms
     * (the implementation starts a daemon scheduler, shut down in a {@code @PreDestroy}) and, as a safety net, every
     * read of an entry. The first caller to see a terminal status wins, so the instant is fixed. Never throws.
     */
    void sweep() {
        try {
            for (Tracked t : scans.values()) {
                if (t.finished().get() == null) {
                    if (getScanStatus.status(t.id()) instanceof Result.Success<ScanSnapshot> ok) {
                        finishedAt(t, ok.value().status().name());
                    }
                }
            }
        } catch (RuntimeException e) {
            // never throws: a failed sweep must not stop the schedule
        }
    }

    /** Stops the sweep scheduler when the application context closes. */
    @PreDestroy
    void shutdown() {
        sweeper.shutdownNow();
    }

    /** Starts a scan through the core; the folders are recorded as the caller gave them. */
    Result<ScanEntry> start(List<String> locations, List<Path> paths, ScanConfig config) {
        Counters counters = new Counters();
        Instant startedAt = clock.instant();
        long seq = sequence.incrementAndGet();
        return switch (startScan.start(paths, config, counters)) {
            case Result.Failure<ScanId> f -> new Result.Failure<>(f.code(), f.message());
            case Result.Success<ScanId> s -> {
                Tracked t = new Tracked(s.value(), List.copyOf(locations), startedAt, seq, counters);
                scans.put(s.value().value(), t);
                yield new Result.Success<>(entry(t));
            }
        };
    }

    /** Every scan of this run, newest first. */
    List<ScanEntry> list() {
        return scans.values().stream()
                .sorted(Comparator.comparingLong(Tracked::sequence).reversed())
                .map(this::entry)
                .toList();
    }

    Optional<ScanEntry> find(String id) {
        return tracked(id).map(this::entry);
    }

    /** Requests a cancel; empty when the ID is unknown. */
    Optional<ScanEntry> cancel(String id) {
        return tracked(id).map(t -> {
            cancelScan.cancel(t.id());
            return entry(t);
        });
    }

    /** The current progress of a known scan, for the event stream. */
    Optional<ScanProgress> progress(String id) {
        return tracked(id).map(this::progress);
    }

    /**
     * The per-scan state of one {@code kind}, built by {@code build} from the scan's {@link ScanResult} the first time
     * it is asked for and kept beside the scan's entry for this server run (DFS-047 decision 6). DFS-048 adds the
     * overlap analysis as a second kind.
     * <p>
     * Thread-safe, without holding any map lock during a build: each scan keeps one memoized
     * {@code CompletableFuture} per kind, created once with {@code putIfAbsent}. The caller whose future went in runs
     * {@code build} on its own thread, outside the map; concurrent callers for the same scan and kind wait on that
     * future and get the same instance, so a successful build runs once. A build may itself call
     * {@code completedState} for <em>another</em> kind of the same scan; asking for its own kind would wait on itself.
     * When {@code build} throws, the future completes exceptionally and is removed (only if it is still the mapped
     * one), so a failed build is never cached and the next request retries it; the builder and every caller waiting
     * on that build get the build's exception, unwrapped.
     *
     * @param id    the scan ID as the client sent it, possibly malformed
     * @param kind  the state's type, the key it is kept under
     * @param build makes the state from a COMPLETED scan's whole result
     * @return {@code Success} with the state; {@code Failure(UNKNOWN_SCAN)} for an unknown or malformed ID;
     *         {@code Failure(SCAN_NOT_COMPLETED)} with the core's message (naming the status) for a scan that is not
     *         COMPLETED, in which case nothing is built or kept
     * @throws RuntimeException the exception {@code build} threw (to the builder and to every caller waiting on it)
     */
    public <T> Result<T> completedState(String id, Class<T> kind, Function<ScanResult, ? extends T> build) {
        Optional<Tracked> found = tracked(id);
        if (found.isEmpty()) {
            return new Result.Failure<>(us.pochaev.dfs.scanner.domain.model.ErrorCode.UNKNOWN_SCAN,
                    "No scan with ID " + id + " in this server run");
        }
        Tracked t = found.get();
        CompletableFuture<?> existing = t.state().get(kind);
        if (existing != null) {
            return new Result.Success<>(kind.cast(await(existing)));
        }
        ScanResult scanResult;
        switch (getScanResult.result(t.id())) {
            case Result.Failure<ScanResult> f -> {
                return new Result.Failure<>(f.code(), f.message());
            }
            case Result.Success<ScanResult> ok -> scanResult = ok.value();
        }
        CompletableFuture<Object> mine = new CompletableFuture<>();
        CompletableFuture<?> other = t.state().putIfAbsent(kind, mine);
        if (other != null) {
            return new Result.Success<>(kind.cast(await(other)));
        }
        try {
            mine.complete(build.apply(scanResult));
        } catch (RuntimeException | Error e) {
            mine.completeExceptionally(e);
            t.state().remove(kind, mine);
            throw e;
        }
        return new Result.Success<>(kind.cast(mine.join()));
    }

    private static Object await(CompletableFuture<?> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw e;
        }
    }

    static boolean terminal(ScanProgress p) {
        return p.status().equals("COMPLETED") || p.status().equals("CANCELLED") || p.status().equals("FAILED");
    }

    private Optional<Tracked> tracked(String id) {
        try {
            return Optional.ofNullable(scans.get(UUID.fromString(id)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private ScanEntry entry(Tracked t) {
        ScanProgress p = progress(t);
        return new ScanEntry(t.id().value().toString(), t.locations(), t.startedAt(), finishedAt(t, p.status()), p);
    }

    /**
     * The instant the scan ended: null while {@code status} is not terminal; otherwise the value kept in
     * {@code t.finished()}, set once (compare-and-set from null) to the clock's instant, never earlier than
     * {@code startedAt}, so every later read returns the same one.
     */
    private Instant finishedAt(Tracked t, String status) {
        boolean ended = status.equals("COMPLETED") || status.equals("CANCELLED") || status.equals("FAILED");
        if (!ended) {
            return null;
        }
        Instant now = clock.instant();
        Instant stamp = now.isBefore(t.startedAt()) ? t.startedAt() : now;
        t.finished().compareAndSet(null, stamp);
        return t.finished().get();
    }

    private ScanProgress progress(Tracked t) {
        ScanSnapshot s = switch (getScanStatus.status(t.id())) {
            case Result.Success<ScanSnapshot> ok -> ok.value();
            case Result.Failure<ScanSnapshot> f -> throw new IllegalStateException("tracked scan unknown to the core: " + f);
        };
        Counters c = t.counters();
        int groupsDone = s.groupsDone();
        int groupsTotal = s.groupsTotal();
        if (s.status() == ScanStatus.COMPLETED) {
            groupsDone = groupsTotal = t.setCount(getScanResult);
        }
        return new ScanProgress(s.status().name(), s.phase() == null ? null : s.phase().name(),
                c.directories, c.files, c.bytes, c.hashedFiles, c.hashFilesTotal, c.hashedBytes, c.hashBytesTotal,
                groupsDone, groupsTotal, s.failureMessage(), c.phases(s.status()));
    }

    private record Tracked(ScanId id, List<String> locations, Instant startedAt, long sequence, Counters counters,
                           AtomicReference<Instant> finished, Map<Class<?>, CompletableFuture<?>> state) {

        Tracked(ScanId id, List<String> locations, Instant startedAt, long sequence, Counters counters) {
            this(id, locations, startedAt, sequence, counters, new AtomicReference<>(), new ConcurrentHashMap<>());
        }

        int setCount(GetScanResult results) {
            if (counters.sets < 0) {
                counters.sets = switch (results.result(id)) {
                    case Result.Success<us.pochaev.dfs.scanner.domain.model.ScanResult> ok -> ok.value().sets().size();
                    case Result.Failure<us.pochaev.dfs.scanner.domain.model.ScanResult> f -> 0;
                };
            }
            return counters.sets;
        }
    }

    /** Counts folded from the events; written by the scanning thread only, read by any. */
    private static final class Counters implements ScanListener {

        volatile long directories;
        volatile long files;
        volatile long bytes;
        volatile long hashedFiles;
        volatile long hashFilesTotal;
        volatile long hashedBytes;
        volatile long hashBytesTotal;
        volatile int sets = -1;
        private volatile List<PhaseTotals> entries = List.of();

        /**
         * One {@link PhaseTotals} per phase that has started, in run order (DFS-064 decision 2). The implementation
         * keeps each phase's own totals here, written by the scanning thread in {@code onEvent}: {@code PhaseStarted}
         * opens an entry (the walk's directories, files and bytes are copied in as they arrive), {@code HashProgress}
         * sets the open hash entry's files and bytes totals, {@code GroupDone} the open hard-link entry's groups. An
         * entry is finished when a later one exists, or when {@code status} is COMPLETED. The returned list is a
         * snapshot, safe to read from any thread, and empty before the first {@code PhaseStarted}.
         */
        List<PhaseTotals> phases(ScanStatus status) {
            List<PhaseTotals> open = entries;
            boolean all = status == ScanStatus.COMPLETED;
            List<PhaseTotals> out = new java.util.ArrayList<>(open.size());
            for (int i = 0; i < open.size(); i++) {
                PhaseTotals p = open.get(i);
                boolean finished = all || i < open.size() - 1;
                out.add(new PhaseTotals(p.phase(), finished, p.directories(), p.files(), p.bytes(), p.groups()));
            }
            return List.copyOf(out);
        }

        /** Replaces the last entry; the scanning thread is the only writer. */
        private void updateLast(Function<PhaseTotals, PhaseTotals> change) {
            List<PhaseTotals> cur = entries;
            if (cur.isEmpty()) {
                return;
            }
            List<PhaseTotals> next = new java.util.ArrayList<>(cur);
            int last = next.size() - 1;
            next.set(last, change.apply(next.get(last)));
            entries = List.copyOf(next);
        }

        private boolean lastIs(String phase) {
            List<PhaseTotals> cur = entries;
            return !cur.isEmpty() && cur.get(cur.size() - 1).phase().equals(phase);
        }

        @Override
        public void onEvent(ScanEvent event) {
            switch (event) {
                case ScanEvent.DirectoryWalked e -> {
                    directories = e.directoriesSoFar();
                    if (lastIs("WALK")) {
                        updateLast(p -> new PhaseTotals(p.phase(), false, e.directoriesSoFar(), p.files(), p.bytes(), 0));
                    }
                }
                case ScanEvent.FileWalked e -> {
                    files = e.filesSoFar();
                    bytes = e.bytesSoFar();
                    if (lastIs("WALK")) {
                        updateLast(p -> new PhaseTotals(p.phase(), false, p.directories(), e.filesSoFar(),
                                e.bytesSoFar(), 0));
                    }
                }
                case ScanEvent.PhaseStarted e -> {
                    hashedFiles = 0;
                    hashFilesTotal = 0;
                    hashedBytes = 0;
                    hashBytesTotal = 0;
                    List<PhaseTotals> next = new java.util.ArrayList<>(entries);
                    PhaseTotals opened = new PhaseTotals(e.phase().name(), false, 0, 0, 0, 0);
                    if (e.phase().name().equals("WALK")) {
                        opened = new PhaseTotals("WALK", false, directories, files, bytes, 0);
                    }
                    next.add(opened);
                    entries = List.copyOf(next);
                }
                case ScanEvent.HashProgress e -> {
                    hashedFiles = e.filesDone();
                    hashFilesTotal = e.filesTotal();
                    hashedBytes = e.bytesDone();
                    hashBytesTotal = e.bytesTotal();
                    if (lastIs(e.phase().name())) {
                        updateLast(p -> new PhaseTotals(p.phase(), false, 0, e.filesTotal(), e.bytesTotal(), 0));
                    }
                }
                case ScanEvent.GroupDone e -> {
                    if (lastIs("HARD_LINKS") && e.phase().name().equals("HARD_LINKS")) {
                        updateLast(p -> new PhaseTotals(p.phase(), false, p.directories(), p.files(), p.bytes(),
                                e.total()));
                    }
                }
                default -> {
                }
            }
        }
    }
}
