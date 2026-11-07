package us.pochaev.dfs.server.plan;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A previewed plan's summary (DFS-054 decision 3): the body of the preview's {@code 201} and of
 * {@code GET /api/scans/{id}/plans/{planId}}. One of the API's own records: it maps from the core's
 * {@code DeletionPlan} without its operations and conflicts, which are paged from their own endpoints (endpoint
 * archetype, checklist item 5).
 * <p>
 * A plan is frozen once previewed: the summary of a plan ID never changes during the server run.
 */
@Schema(name = "PlanSummary")
public record PlanSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The core's content-derived plan ID (64 lowercase hex characters): the same request on"
                        + " the same scan previews to the same ID")
        String planId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        PlanTotalsView totals,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "One entry per rule, in rule order")
        List<LeftBehindView> leftBehind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Every quarantine operation of the plan")
        int operationCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Every conflict of the plan; a plan with conflicts cannot be confirmed (U7.1)")
        int conflictCount) {
}
