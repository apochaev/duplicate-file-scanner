package us.pochaev.dfs.server.result;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * One page of a COMPLETED scan's folder groups (DFS-067). One of the API's own records, built from the overlap
 * package's {@code FolderGroup}s.
 * <p>
 * {@code totalItems} counts every group that matches {@code under} and reaches {@code minBytes}, not only this page; a
 * page past the end has no items and the same total. {@code hiddenSmall} counts the groups that match {@code under} and
 * fall below {@code minBytes}.
 */
@Schema(name = "GroupPage")
public record GroupPage(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ordered by reclaimableBytes, largest"
                + " first, then the first folder's path, then the folder lists path by path")
        List<FolderGroupView> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The page asked for, from 0")
        int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The page size asked for, 1 to 500")
        int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Every matching group of at least minBytes reclaimable")
        int totalItems,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Matching groups below minBytes, left out"
                + " of items; 0 when minBytes is 0")
        int hiddenSmall) {
}
