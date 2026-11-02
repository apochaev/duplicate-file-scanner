package us.pochaev.dfs.server.result;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * One page of a COMPLETED scan's duplicate sets matching {@code under} (all sets without it), largest reclaimable first
 * (DFS-047 decision 3). One of the API's own records, built from the core's {@code DuplicateSet}s.
 * <p>
 * {@code totalItems} and {@code totalReclaimableBytes} cover every matching set, not only this page; a page past the
 * end has no items and the same totals.
 */
@Schema(name = "SetPage")
public record SetPage(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Ordered by reclaimableBytes, then size, largest first, then digest")
        List<DuplicateSetView> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The page asked for, from 0")
        int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The page size asked for, 1 to 500")
        int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Every matching set")
        int totalItems,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The reclaimable bytes of every matching set")
        long totalReclaimableBytes) {
}
