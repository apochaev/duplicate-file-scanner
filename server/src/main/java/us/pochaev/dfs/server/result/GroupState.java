package us.pochaev.dfs.server.result;

import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.server.analysis.FolderGroups;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * A COMPLETED scan's folder-group state for the groups endpoint (DFS-067 decision 4): kept beside the scan's entry by
 * {@link us.pochaev.dfs.server.scan.ScanRegistry#completedState} under its own key ({@code GroupState.class}), apart
 * from {@link OverlapState}, so asking for groups never builds the pair analysis. Built at once (it only holds the
 * result); the groups themselves are built lazily, on the first {@link #index()} call.
 * <p>
 * Same contract as {@link OverlapState#index()}: the first caller runs {@code build} on its own thread; callers
 * arriving meanwhile wait for the same computation and get the same {@link GroupIndex}; later callers read it; if
 * {@code build} throws, nothing is kept, the next call retries, and every caller waiting gets the exception unwrapped.
 * Thread-safe. {@link #insideRoots} needs no grouping, so a refused path never starts the computation.
 */
final class GroupState {

    private final ScanResult result;
    private final Function<ScanResult, FolderGroups> build;
    private final AtomicReference<CompletableFuture<GroupIndex>> future = new AtomicReference<>();

    /**
     * @param result the COMPLETED scan's whole result
     * @param build  builds the groups ({@code FolderGroups::of} in the server)
     */
    GroupState(ScanResult result, Function<ScanResult, FolderGroups> build) {
        this.result = Objects.requireNonNull(result, "result");
        this.build = Objects.requireNonNull(build, "build");
    }

    /** The scan's group index, built at most once (see the class comment). */
    GroupIndex index() {
        while (true) {
            CompletableFuture<GroupIndex> mine = new CompletableFuture<>();
            if (future.compareAndSet(null, mine)) {
                try {
                    GroupIndex built = new GroupIndex(build.apply(result));
                    mine.complete(built);
                    return built;
                } catch (Throwable t) {
                    future.compareAndSet(mine, null);
                    mine.completeExceptionally(t);
                    throw t;
                }
            }
            CompletableFuture<GroupIndex> other = future.get();
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

    /** Whether {@code path} (absolute, normalized) is a root of the scan or under one, as {@code OverlapState}. */
    boolean insideRoots(Path path) {
        for (Path root : result.roots()) {
            if (path.startsWith(root)) {
                return true;
            }
        }
        return false;
    }
}
