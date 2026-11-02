package us.pochaev.dfs.server.result;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * One page of a COMPLETED scan's listed folder pairs (DFS-048 decisions 3 and 6): every pair of immediate folders with
 * a non-zero side, ranked; with {@code under}, only the pairs with at least one side inside its subtree (the folder
 * itself included), whatever the other side. One of the API's own records, built from the overlap analysis'
 * {@code FolderPair}s.
 * <p>
 * {@code totalItems} counts every matching pair, not only this page; a page past the end has no items and the same
 * total.
 */
@Schema(name = "PairPage")
public record PairPage(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ordered by the larger bytesWithCopy of the"
                + " two sides, largest first, then a's folder, then b's folder")
        List<FolderPairView> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The page asked for, from 0")
        int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The page size asked for, 1 to 500")
        int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Every matching pair")
        int totalItems) {
}
