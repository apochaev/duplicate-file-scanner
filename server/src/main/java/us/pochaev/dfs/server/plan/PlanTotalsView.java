package us.pochaev.dfs.server.plan;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What a previewed plan would move (DFS-054 decision 3). One of the API's own records: it maps from the core's
 * {@code PlanTotals} (endpoint archetype, checklist item 5).
 */
@Schema(name = "PlanTotalsView")
public record PlanTotalsView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Links moved: every link of every quarantined copy")
        long files,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Bytes freed: each quarantined copy's size once, whatever its link count")
        long bytes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Duplicate sets with an operation")
        int sets) {
}
