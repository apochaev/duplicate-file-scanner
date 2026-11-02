package us.pochaev.dfs.server.result;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * One page of a COMPLETED scan's listed folder summaries (DFS-048 decisions 2 and 6): every folder with a file with a
 * copy elsewhere or an internal duplicate, ranked; with {@code under}, only those strictly inside its subtree. One of
 * the API's own records, built from the overlap analysis' {@code FolderSummary}s.
 * <p>
 * {@code folder} is the summary of {@code under} itself, also when that folder is not listed (zeros for a folder the
 * scan holds nothing for); it is null without {@code under}. {@code totalItems} counts every matching summary, not only
 * this page; a page past the end has no items and the same total.
 */
@Schema(name = "SummaryPage")
public record SummaryPage(
        @Schema(nullable = true, description = "The summary of the folder given as under; null without under")
        FolderSummaryView folder,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ordered by bytesWithCopyElsewhere, then"
                + " internalBytes, largest first, then folder path")
        List<FolderSummaryView> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The page asked for, from 0")
        int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The page size asked for, 1 to 500")
        int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Every matching summary")
        int totalItems) {
}
