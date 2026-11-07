package us.pochaev.dfs.server.plan;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A duplicate set the plan leaves untouched because a rule's keeper would itself be quarantined by another rule
 * (DFS-054 decision 4; the core's {@code Conflict}). Self-contained, so the plan screen can explain it from one page:
 * the server joins the scan's set (size, copies) onto the core's conflict and works out, per copy, which of the
 * conflict's rules keep it and which would quarantine it. One of the API's own records (endpoint archetype, checklist
 * item 5).
 */
@Schema(name = "PlanConflict")
public record PlanConflict(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The set's digest")
        String digest,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The set's file size in bytes")
        long size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Every physical copy of the set, ordered by first link")
        List<ConflictCopy> copies,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Every rule that would have produced an operation in this set, by ascending ruleIndex")
        List<PlanSource> sources) {
}
