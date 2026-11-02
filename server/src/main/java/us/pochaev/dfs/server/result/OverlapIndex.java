package us.pochaev.dfs.server.result;

import org.springframework.http.HttpStatus;
import us.pochaev.dfs.server.analysis.FolderPair;
import us.pochaev.dfs.server.analysis.FolderSummary;
import us.pochaev.dfs.server.analysis.OverlapAnalysis;
import us.pochaev.dfs.server.analysis.PairSide;
import us.pochaev.dfs.server.api.ApiException;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A COMPLETED scan's overlap analysis as the overlaps API serves it (DFS-048 decisions 2, 3, 4 and 6): filters and
 * pages DFS-046's ordered lists and maps its records to the API's own ({@link FolderSummaryView}, {@link PairSideView},
 * {@link FolderPairView}). Immutable, so any request thread may read it; {@link OverlapState} builds it once per scan.
 * <p>
 * Paths given to it are absolute, normalized and inside a root (the controller checks them first). Subtree tests use
 * {@link Path#startsWith}, so a folder is inside its own subtree.
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
        List<FolderSummary> all = analysis.summaries();
        if (under == null) {
            return new SummaryPage(null, slice(all, page, size, OverlapIndex::view), page, size, all.size());
        }
        List<FolderSummary> matching = all.stream()
                .filter(s -> !s.folder().equals(under) && s.folder().startsWith(under)).toList();
        FolderSummary own = analysis.summary(under)
                .orElseGet(() -> new FolderSummary(under, 0, 0, 0, 0, 0, 0));
        return new SummaryPage(view(own), slice(matching, page, size, OverlapIndex::view), page, size,
                matching.size());
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
        List<FolderPair> matching = under == null ? analysis.pairs() : analysis.pairs().stream()
                .filter(p -> p.a().folder().startsWith(under) || p.b().folder().startsWith(under)).toList();
        return new PairPage(slice(matching, page, size, OverlapIndex::view), page, size, matching.size());
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
        if (x.equals(y)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NESTED_FOLDERS", "both folders are " + x);
        }
        if (x.startsWith(y)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NESTED_FOLDERS", y + " contains " + x);
        }
        if (y.startsWith(x)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NESTED_FOLDERS", x + " contains " + y);
        }
        return view(analysis.pair(x, y));
    }

    private static <T, V> List<V> slice(List<T> list, int page, int size, Function<T, V> map) {
        long from = (long) page * size;
        if (from >= list.size()) {
            return List.of();
        }
        int to = (int) Math.min(list.size(), from + size);
        return list.subList((int) from, to).stream().map(map).toList();
    }

    private static FolderSummaryView view(FolderSummary s) {
        return new FolderSummaryView(s.folder().toString(), s.totalFiles(), s.totalBytes(),
                s.filesWithCopyElsewhere(), s.bytesWithCopyElsewhere(), s.internalDuplicateFiles(),
                s.internalBytes());
    }

    private static PairSideView view(PairSide s) {
        return new PairSideView(s.folder().toString(), s.filesWithCopy(), s.totalFiles(), s.bytesWithCopy(),
                s.totalBytes(), s.full());
    }

    private static FolderPairView view(FolderPair p) {
        return new FolderPairView(view(p.a()), view(p.b()));
    }
}
