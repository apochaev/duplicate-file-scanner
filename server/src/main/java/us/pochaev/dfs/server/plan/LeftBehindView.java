package us.pochaev.dfs.server.plan;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What stays in one rule's quarantine folder after the plan (DFS-054 decision 4). One of the API's own records: it
 * maps from the core's {@code LeftBehind} (endpoint archetype, checklist item 5).
 */
@Schema(name = "LeftBehindView")
public record LeftBehindView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The rule's position in the request, from 0")
        int ruleIndex,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The rule's quarantineIn, normalized (Path.toString(): backslashes on Windows)")
        String folder,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Scanned files left at or under folder after the plan")
        long files,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Their summed size in bytes")
        long bytes) {
}
