package us.pochaev.dfs.server.plan;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * One quarantine operation of a previewed plan (DFS-054 decision 4): one physical copy, moved with all its links, kept
 * against {@code keeper}. Self-contained, so the plan screen needs no further call to show it. One of the API's own
 * records: it maps from the core's {@code Operation.Quarantine} (endpoint archetype, checklist item 5).
 */
@Schema(name = "PlanOperation")
public record PlanOperation(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The set's digest")
        String digest,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The set's file size in bytes")
        long size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Every link of the quarantined copy (hard links move together), at least one, ordered"
                        + " by path")
        List<String> links,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The first link (by path) of the copy this one is kept against; never one of links")
        String keeper,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        PlanSource source) {
}
