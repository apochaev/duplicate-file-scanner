package us.pochaev.dfs.server.result;

import us.pochaev.dfs.server.analysis.OverlapAnalysis;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A COMPLETED scan's overlap analysis as the overlaps API serves it (DFS-048 decisions 2, 3, 4 and 6): filters and
 * pages DFS-046's ordered lists and maps its records to the API's own ({@link FolderSummaryView}, {@link PairSideView},
 * {@link FolderPairView}). Immutable, so any request thread may read it; {@link OverlapState} builds it once per scan.
 * <p>
 * Paths given to it are absolute, normalized and inside a root (the controller checks them first). Subtree tests use
 * {@link Path#startsWith}, so a folder is inside its own subtree.
 * <p>
 * Stubs (design phase): every method body except the constructor throws.
 */
final class OverlapIndex {

    private final OverlapAnalysis analysis;

    OverlapIndex(OverlapAnalysis analysis) {
        this.analysis = Objects.requireNonNull(analysis, "analysis");
    }

    /**
     * One page of the listed summaries in DFS-046's order: every one when {@code under} is null (then
     * {@code folder} is null), else those strictly inside {@code under}'s subtree, with {@code under}'s own
     * {@code summary(under)} as {@code folder}, also when it is not listed (zeros when the scan holds nothing there).
     *
     * @param under absolute, normalized and inside a root, or null
     * @param page  0 or more
     * @param size  1 to 500
     */
    SummaryPage summaries(Path under, int page, int size) {
        throw new UnsupportedOperationException("not implemented: DFS-048");
    }

    /**
     * One page of the listed pairs in DFS-046's order: every one when {@code under} is null, else those with at least
     * one side inside {@code under}'s subtree, {@code under} itself included, whatever the other side.
     *
     * @param under absolute, normalized and inside a root, or null
     * @param page  0 or more
     * @param size  1 to 500
     */
    PairPage pairs(Path under, int page, int size) {
        throw new UnsupportedOperationException("not implemented: DFS-048");
    }

    /**
     * The pair of two folders, each counted recursively, {@code a} before {@code b} by path whatever the argument
     * order; a folder the scan holds nothing for is a side of zeros with {@code full} false.
     *
     * @param x absolute, normalized and inside a root
     * @param y absolute, normalized and inside a root
     * @throws us.pochaev.dfs.server.api.ApiException {@code 400 NESTED_FOLDERS} when the folders are equal ("both
     *                                                folders are &lt;x&gt;") or one contains the other ("&lt;outer&gt;
     *                                                contains &lt;inner&gt;"), checked before DFS-046's {@code pair}
     *                                                is called
     */
    FolderPairView pair(Path x, Path y) {
        throw new UnsupportedOperationException("not implemented: DFS-048");
    }
}
