package us.pochaev.dfs.server.result;

import us.pochaev.dfs.scanner.domain.model.ScanResult;

import java.nio.file.Path;
import java.util.List;

/**
 * A COMPLETED scan's result, indexed once for the result API (DFS-047 decision 6): the folder tree of the scan's roots
 * with subtree totals rolled up from {@code ScanResult.folders}, and the duplicate sets sorted once by reclaimable bytes
 * (largest first), then size (largest first), then digest. Immutable once built, so any request thread may read it;
 * {@link us.pochaev.dfs.server.scan.ScanRegistry#completedState} builds it at most once per scan and keeps it for this
 * server run.
 * <p>
 * Paths given to it are absolute and normalized; {@link #insideRoots} is checked before {@link #children} and
 * {@link #sets} are asked. A path inside a root that the scan holds nothing for answers an empty list or page, never
 * an error, so the API does not reveal whether a folder exists (decision 5).
 * <p>
 * Stubs (design phase): every method throws.
 */
final class ResultIndex {

    private ResultIndex() {
    }

    /** Builds the index: the folder tree with rolled-up totals and the sets in their one order. */
    static ResultIndex of(ScanResult result) {
        throw new UnsupportedOperationException("not implemented: DFS-047");
    }

    /**
     * Whether {@code path} (absolute, normalized) is a root of the scan or lies under one, by {@link Path#startsWith}
     * (the platform's rules: case-insensitive on Windows).
     */
    boolean insideRoots(Path path) {
        throw new UnsupportedOperationException("not implemented: DFS-047");
    }

    /** The scan's roots as folders, ordered by name, case-insensitively. */
    List<FolderNode> roots() {
        throw new UnsupportedOperationException("not implemented: DFS-047");
    }

    /**
     * The immediate subfolders of {@code parent} that hold at least one counted file in their subtree, ordered by
     * name, case-insensitively; empty when the scan holds nothing for {@code parent}.
     */
    List<FolderNode> children(Path parent) {
        throw new UnsupportedOperationException("not implemented: DFS-047");
    }

    /**
     * One page of the sets with at least one link inside {@code under} (every set when {@code under} is null), each set
     * with all its copies; the totals cover every matching set.
     *
     * @param under absolute, normalized and inside a root, or null
     * @param page  0 or more
     * @param size  1 to 500
     */
    SetPage sets(Path under, int page, int size) {
        throw new UnsupportedOperationException("not implemented: DFS-047");
    }
}
