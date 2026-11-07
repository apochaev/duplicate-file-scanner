package us.pochaev.dfs.server.plan;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * One page of a previewed plan's conflicts, largest {@code size} first (DFS-054 decision 3), paged like
 * {@code SetPage}. {@code totalItems} covers every conflict of the plan; a page past the end has no items and the same
 * total.
 */
@Schema(name = "ConflictPage")
public record ConflictPage(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Ordered by size, largest first, then digest")
        List<PlanConflict> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The page asked for, from 0")
        int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The page size asked for, 1 to 500")
        int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Every conflict of the plan")
        int totalItems) {
}
