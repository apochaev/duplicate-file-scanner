package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.FolderTotal;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.scanner.domain.model.ScanEvent;
import us.pochaev.dfs.scanner.domain.model.ScanListener;
import us.pochaev.dfs.scanner.domain.model.ScanPhase;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.model.SkipReason;
import us.pochaev.dfs.scanner.domain.model.WalkEvent;
import us.pochaev.dfs.scanner.infra.port.ContentHasher;
import us.pochaev.dfs.scanner.infra.port.FileIdentity;
import us.pochaev.dfs.scanner.infra.port.FileTreeWalker;
import us.pochaev.dfs.scanner.infra.port.LocationResolver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Runs one scan synchronously, end to end (DFS-005): resolves the start locations, walks them into
 * a {@link SizeIndex} (leaving out what {@link DefaultExcludeFilter} excludes, DFS-027), then feeds its groups one at a time through {@link PartialHashFilter},
 * {@link FullHashConfirmer} and {@link HardLinkDetector} (CLAUDE.md design decision 3).
 * While walking it also totals each folder's counted files in a {@link FolderCounter}, and the result
 * records the walked roots and those totals (DFS-030).
 * <p>
 * Stateless and reusable: its fields are its ports only. Every piece of per-scan state (the
 * {@code SizeIndex}, the stage instances built from the {@link ScanConfig}, the skip collector, the
 * counters) is created inside {@link #scan} and never escapes it, so one instance may run several
 * scans at once as long as the ports are thread-safe.
 * <p>
 * Implements {@link ScanRunner}, the seam {@link ScanService} runs it through (DFS-018 decision 12).
 */
public final class ScanPipeline implements ScanRunner {

    private final LocationResolver resolver;
    private final FileTreeWalker walker;
    private final ContentHasher hasher;
    private final FileIdentity identity;

    /**
     * @throws NullPointerException if any port is null
     */
    public ScanPipeline(LocationResolver resolver, FileTreeWalker walker, ContentHasher hasher,
                        FileIdentity identity) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.walker = Objects.requireNonNull(walker, "walker");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.identity = Objects.requireNonNull(identity, "identity");
    }

    /**
     * Scans {@code locations} and returns the walked roots, the per-folder totals, the confirmed
     * duplicate sets and every skip.
     * <p>
     * Before any event is sent: an empty {@code locations} throws {@link IllegalArgumentException};
     * each location is resolved through {@link LocationResolver#realPath}, and the first that fails
     * makes the scan throw that {@link IOException}. The resolved locations are sorted ascending, any
     * equal to or inside ({@link Path#startsWith}) a kept one is dropped, and the rest are walked
     * one after another. A location whose walk throws is reported as skipped ({@code UNREADABLE})
     * and the scan goes on.
     * <p>
     * {@code stopSignal} is read at every walk event (the walk listener then returns {@code false})
     * and before each group of the partial-hash, full-hash and hard-link phases; once it reads
     * {@code true} the scan throws {@link CancellationException} and returns no partial result.
     *
     * @throws NullPointerException     if any argument is null or {@code locations} contains null
     * @throws IllegalArgumentException if {@code locations} is empty
     * @throws IOException              if a location cannot be resolved or is not a directory
     * @throws CancellationException    if {@code stopSignal} read {@code true}
     */
    public ScanResult scan(List<Path> locations, ScanConfig config, ScanListener listener,
                           BooleanSupplier stopSignal) throws IOException {
        Objects.requireNonNull(locations, "locations");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(stopSignal, "stopSignal");
        for (Path location : locations) {
            Objects.requireNonNull(location, "location");
        }
        if (locations.isEmpty()) {
            throw new IllegalArgumentException("locations must not be empty");
        }

        List<Path> resolved = new ArrayList<>();
        for (Path location : locations) {
            resolved.add(resolver.realPath(location));
        }
        List<Path> kept = keepTopLevel(resolved);

        SizeIndex sizeIndex = new SizeIndex(config.minSize());
        List<WalkEvent.Skipped> skipped = new ArrayList<>();
        Consumer<WalkEvent.Skipped> sink = s -> {
            skipped.add(s);
            listener.onEvent(new ScanEvent.Skipped(s));
        };

        HardLinkDetector hardLinkDetector = new HardLinkDetector(identity, sink);
        DefaultExcludeFilter excludeFilter = new DefaultExcludeFilter(config.defaultExcludes());

        FolderCounter folderCounter = new FolderCounter(config.minSize());

        walkLocations(kept, config.defaultExcludes(), excludeFilter, sizeIndex, folderCounter, sink, listener,
                stopSignal);

        List<CandidateGroup> initialGroups = sizeIndex.groups();
        PartialHashFilter partialHashFilter = new PartialHashFilter(hasher, config.sampleSize(),
                config.partialHashThreshold(), sink,
                new PhaseProgress(ScanPhase.PARTIAL_HASH, initialGroups, listener));
        List<CandidateGroup> partialOutput = runGroupPhase(
                ScanPhase.PARTIAL_HASH, initialGroups, partialHashFilter::filter, listener, stopSignal);
        FullHashConfirmer fullHashConfirmer = new FullHashConfirmer(hasher, sink,
                new PhaseProgress(ScanPhase.FULL_HASH, partialOutput, listener));
        List<DuplicateSet> fullOutput = runGroupPhase(
                ScanPhase.FULL_HASH, partialOutput, fullHashConfirmer::confirm, listener, stopSignal);
        List<DuplicateSet> hardLinkOutput = runGroupPhase(
                ScanPhase.HARD_LINKS, fullOutput, hardLinkDetector::detect, listener, stopSignal);

        return new ScanResult(kept, folderCounter.totals(), hardLinkOutput, skipped, excludeFilter.excluded());
    }

    /** {@link ScanRunner}'s method: exactly {@link #scan}, same arguments, same contract (DFS-018). */
    @Override
    public ScanResult run(List<Path> locations, ScanConfig config, ScanListener listener,
                          BooleanSupplier stopSignal) throws IOException {
        return scan(locations, config, listener, stopSignal);
    }

    /**
     * Walks {@code kept} in order. Every {@code FileFound} is reported as {@link ScanEvent.FileWalked}
     * first, so excluded files still count as found (DFS-027 decision 4), then goes through
     * {@code excludeFilter} and, if kept, into {@code sizeIndex} (name check before size check,
     * DFS-027 decision 7). A file goes into {@code folderCounter} exactly when it is kept and at
     * least {@link ScanConfig#minSize()}, the point where it enters the {@code SizeIndex} (DFS-030
     * decision 9). {@code skipSystemFolders} is {@link ScanConfig#defaultExcludes()}, handed to the
     * walker, which alone can prune a folder's subtree (DFS-061 decisions 3 and 6).
     */
    private void walkLocations(List<Path> kept, boolean skipSystemFolders, DefaultExcludeFilter excludeFilter,
                               SizeIndex sizeIndex, FolderCounter folderCounter, Consumer<WalkEvent.Skipped> sink,
                               ScanListener listener, BooleanSupplier stopSignal) throws IOException {
        listener.onEvent(new ScanEvent.PhaseStarted(ScanPhase.WALK));
        AtomicLong count = new AtomicLong();
        AtomicLong bytes = new AtomicLong();
        AtomicLong directories = new AtomicLong();
        AtomicBoolean stopped = new AtomicBoolean();
        for (Path root : kept) {
            try {
                walker.walk(root, skipSystemFolders, event -> {
                    if (stopSignal.getAsBoolean()) {
                        stopped.set(true);
                        return false;
                    }
                    if (event instanceof WalkEvent.FileFound found) {
                        listener.onEvent(new ScanEvent.FileWalked(count.incrementAndGet(),
                                bytes.addAndGet(found.entry().size())));
                        if (excludeFilter.keep(found.entry())) {
                            sizeIndex.add(found.entry());
                            folderCounter.add(found.entry());
                        }
                    } else if (event instanceof WalkEvent.DirectoryEntered) {
                        listener.onEvent(new ScanEvent.DirectoryWalked(directories.incrementAndGet()));
                    } else if (event instanceof WalkEvent.Skipped s) {
                        sink.accept(s);
                    }
                    return true;
                });
            } catch (IOException e) {
                sink.accept(new WalkEvent.Skipped(root, SkipReason.UNREADABLE,
                        "walk failed: " + PartialHashFilter.detail(e)));
            }
            if (stopped.get()) {
                throw new CancellationException("scan stopped while walking " + root);
            }
        }
    }

    private <I, O> List<O> runGroupPhase(ScanPhase phase, List<I> input, java.util.function.Function<List<I>, List<O>> stage,
                                         ScanListener listener, BooleanSupplier stopSignal) {
        listener.onEvent(new ScanEvent.PhaseStarted(phase));
        List<O> output = new ArrayList<>();
        int total = input.size();
        for (int i = 0; i < total; i++) {
            if (stopSignal.getAsBoolean()) {
                throw new CancellationException("scan stopped before a " + phase + " group");
            }
            output.addAll(stage.apply(List.of(input.get(i))));
            listener.onEvent(new ScanEvent.GroupDone(phase, i + 1, total));
        }
        return List.copyOf(output);
    }

    /**
     * Turns a hash stage's per-file calls into {@link ScanEvent.HashProgress} for one phase (DFS-024).
     * The totals are the input groups' file count and size sum, fixed when the phase begins; bytes
     * read within a file count up to that file's scan-time size at most, so a file that grew since
     * the walk cannot push {@code bytesDone} past {@code bytesTotal}. Used on the scan's thread only.
     */
    private static final class PhaseProgress implements FileProgress {

        private final ScanPhase phase;
        private final ScanListener listener;
        private final long filesTotal;
        private final long bytesTotal;
        private long filesDone;
        private long bytesFinished;

        PhaseProgress(ScanPhase phase, List<CandidateGroup> input, ScanListener listener) {
            this.phase = phase;
            this.listener = listener;
            long files = 0;
            long bytes = 0;
            for (CandidateGroup group : input) {
                files += group.files().size();
                bytes += group.size() * group.files().size();
            }
            this.filesTotal = files;
            this.bytesTotal = bytes;
        }

        @Override
        public void bytesRead(FileEntry file, long bytes) {
            send(bytesFinished + Math.min(bytes, file.size()));
        }

        @Override
        public void fileDone(FileEntry file) {
            filesDone++;
            bytesFinished += file.size();
            send(bytesFinished);
        }

        private void send(long bytesDone) {
            listener.onEvent(new ScanEvent.HashProgress(phase, filesDone, filesTotal, bytesDone, bytesTotal));
        }
    }

    /**
     * Totals the counted files of one scan per folder they directly sit in (DFS-030 decision 2).
     * Fed by the walk callback on the scan's thread; deliberately not thread-safe and never merged:
     * one instance belongs to one {@link #scan} call (checklist items 1 and 13). Package-private so
     * the heap benchmark can measure it on its own (DFS-030 decision 10).
     */
    static final class FolderCounter {

        private final long minSize;
        private final java.util.Map<Path, long[]> totals = new java.util.HashMap<>();

        /** @param minSize the scan's {@link ScanConfig#minSize()}: a smaller file is not counted */
        FolderCounter(long minSize) {
            this.minSize = minSize;
        }

        /**
         * Counts {@code file} in the total of its parent folder ({@code file.path().getParent()}), if
         * it is at least {@code minSize} bytes; a smaller file is ignored. The caller has already
         * applied the default excludes. Every call counts, so two hard-linked paths count twice.
         */
        void add(FileEntry file) {
            if (file.size() < minSize) {
                return;
            }
            long[] total = totals.computeIfAbsent(file.path().getParent(), k -> new long[2]);
            total[0]++;
            total[1] += file.size();
        }

        /**
         * One {@link FolderTotal} per folder that received at least one counted file, sorted ascending
         * by folder path ({@link Path#compareTo}), as an immutable list; empty if nothing was counted.
         */
        List<FolderTotal> totals() {
            return totals.entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .map(e -> new FolderTotal(e.getKey(), e.getValue()[0], e.getValue()[1]))
                    .toList();
        }
    }

    /**
     * Sorts {@code resolved} ascending and drops any location equal to or nested inside an
     * already kept one.
     */
    private static List<Path> keepTopLevel(List<Path> resolved) {
        List<Path> sorted = new ArrayList<>(resolved);
        sorted.sort(Path::compareTo);
        List<Path> kept = new ArrayList<>();
        for (Path candidate : sorted) {
            boolean nested = false;
            for (Path already : kept) {
                if (candidate.equals(already) || candidate.startsWith(already)) {
                    nested = true;
                    break;
                }
            }
            if (!nested) {
                kept.add(candidate);
            }
        }
        return kept;
    }
}
