package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
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
 * a {@link SizeIndex}, then feeds its groups one at a time through {@link PartialHashFilter},
 * {@link FullHashConfirmer} and {@link HardLinkDetector} (CLAUDE.md design decision 3).
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
     * Scans {@code locations} and returns the confirmed duplicate sets and every skip.
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

        PartialHashFilter partialHashFilter = new PartialHashFilter(
                hasher, config.sampleSize(), config.partialHashThreshold(), sink);
        FullHashConfirmer fullHashConfirmer = new FullHashConfirmer(hasher, sink);
        HardLinkDetector hardLinkDetector = new HardLinkDetector(identity, sink);

        walkLocations(kept, sizeIndex, sink, listener, stopSignal);

        List<CandidateGroup> initialGroups = sizeIndex.groups();
        List<CandidateGroup> partialOutput = runGroupPhase(
                ScanPhase.PARTIAL_HASH, initialGroups, partialHashFilter::filter, listener, stopSignal);
        List<DuplicateSet> fullOutput = runGroupPhase(
                ScanPhase.FULL_HASH, partialOutput, fullHashConfirmer::confirm, listener, stopSignal);
        List<DuplicateSet> hardLinkOutput = runGroupPhase(
                ScanPhase.HARD_LINKS, fullOutput, hardLinkDetector::detect, listener, stopSignal);

        return new ScanResult(hardLinkOutput, skipped);
    }

    /** {@link ScanRunner}'s method: exactly {@link #scan}, same arguments, same contract (DFS-018). */
    @Override
    public ScanResult run(List<Path> locations, ScanConfig config, ScanListener listener,
                          BooleanSupplier stopSignal) throws IOException {
        throw new UnsupportedOperationException("not implemented: DFS-018");
    }

    private void walkLocations(List<Path> kept, SizeIndex sizeIndex, Consumer<WalkEvent.Skipped> sink,
                               ScanListener listener, BooleanSupplier stopSignal) throws IOException {
        listener.onEvent(new ScanEvent.PhaseStarted(ScanPhase.WALK));
        AtomicLong count = new AtomicLong();
        AtomicBoolean stopped = new AtomicBoolean();
        for (Path root : kept) {
            try {
                walker.walk(root, event -> {
                    if (stopSignal.getAsBoolean()) {
                        stopped.set(true);
                        return false;
                    }
                    if (event instanceof WalkEvent.FileFound found) {
                        sizeIndex.add(found.entry());
                        listener.onEvent(new ScanEvent.FileWalked(count.incrementAndGet()));
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
