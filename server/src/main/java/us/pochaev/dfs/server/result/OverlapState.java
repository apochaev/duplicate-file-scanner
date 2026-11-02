package us.pochaev.dfs.server.result;

import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.server.analysis.OverlapAnalysis;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * A COMPLETED scan's overlap state for the overlaps API (DFS-048 decision 5): kept beside the scan's entry by
 * {@link us.pochaev.dfs.server.scan.ScanRegistry#completedState} (one instance per scan, built at once since it only
 * holds the result), it computes the overlap analysis lazily, on the first {@link #index()} call, at most once.
 * <p>
 * Contract of {@link #index()}: the first caller runs {@code analyse} on its own thread; callers arriving meanwhile wait
 * for that same computation and get the same {@link OverlapIndex} instance; later callers read it without computing
 * again. If {@code analyse} throws, nothing is kept, the next call retries, and the caller and every waiting caller get
 * the exception unwrapped (as {@code completedState} does for its builds). Thread-safe; no lock is held while waiting
 * longer than the computation itself.
 * <p>
 * {@link #insideRoots} needs no analysis, so a refused path never starts the computation.
 */
final class OverlapState {

    private final ScanResult result;
    private final Function<ScanResult, OverlapAnalysis> analyse;
    private final AtomicReference<CompletableFuture<OverlapIndex>> future = new AtomicReference<>();

    /**
     * @param result  the COMPLETED scan's whole result
     * @param analyse computes the analysis ({@code OverlapAnalysis::of} in the server; a counting, blocking function in
     *                the AC-8 test)
     */
    OverlapState(ScanResult result, Function<ScanResult, OverlapAnalysis> analyse) {
        this.result = Objects.requireNonNull(result, "result");
        this.analyse = Objects.requireNonNull(analyse, "analyse");
    }

    /** The scan's overlap index, computed at most once (see the class comment). */
    OverlapIndex index() {
        while (true) {
            CompletableFuture<OverlapIndex> mine = new CompletableFuture<>();
            if (future.compareAndSet(null, mine)) {
                try {
                    OverlapIndex built = new OverlapIndex(analyse.apply(result));
                    mine.complete(built);
                    return built;
                } catch (Throwable t) {
                    future.compareAndSet(mine, null);
                    mine.completeExceptionally(t);
                    throw t;
                }
            }
            CompletableFuture<OverlapIndex> other = future.get();
            if (other == null) {
                continue;
            }
            try {
                return other.join();
            } catch (CompletionException e) {
                if (e.getCause() instanceof RuntimeException re) {
                    throw re;
                }
                if (e.getCause() instanceof Error err) {
                    throw err;
                }
                throw e;
            }
        }
    }

    /**
     * Whether {@code path} (absolute, normalized) is one of the scan's roots or lies under one, by
     * {@link Path#startsWith} (the platform's rules: case-insensitive on Windows), as {@code ResultIndex.insideRoots}.
     */
    boolean insideRoots(Path path) {
        for (Path root : result.roots()) {
            if (path.startsWith(root)) {
                return true;
            }
        }
        return false;
    }
}
