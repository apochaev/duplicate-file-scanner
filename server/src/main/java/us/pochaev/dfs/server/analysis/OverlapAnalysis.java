package us.pochaev.dfs.server.analysis;

import us.pochaev.dfs.scanner.domain.model.ScanResult;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The folder overlap analysis of one completed scan (docs/design/folder-overlap-analysis.md; docs/design/ui.md U5b).
 * Computed once by {@link #of}; immutable, so any thread may read it.
 */
public final class OverlapAnalysis {

    private OverlapAnalysis() {
    }

    /** Analyses {@code result} once. */
    public static OverlapAnalysis of(ScanResult result) {
        throw new UnsupportedOperationException("not implemented: DFS-046");
    }

    /** Every folder with a file with a copy elsewhere or an internal duplicate, in the fixed order. */
    public List<FolderSummary> summaries() {
        throw new UnsupportedOperationException("not implemented: DFS-046");
    }

    /** The summary of any folder from a root down; empty for a folder outside every root. */
    public Optional<FolderSummary> summary(Path folder) {
        throw new UnsupportedOperationException("not implemented: DFS-046");
    }

    /** Every pair of immediate folders with a non-zero side, in the fixed order. */
    public List<FolderPair> pairs() {
        throw new UnsupportedOperationException("not implemented: DFS-046");
    }

    /**
     * One pair for any two folders, each counted recursively, in either argument order.
     *
     * @throws IllegalArgumentException if the folders are equal, one contains the other, or either lies outside
     *                                  every root
     */
    public FolderPair pair(Path x, Path y) {
        throw new UnsupportedOperationException("not implemented: DFS-046");
    }
}
