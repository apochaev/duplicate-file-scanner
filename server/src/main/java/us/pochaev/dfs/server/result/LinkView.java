package us.pochaev.dfs.server.result;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * One path of a physical copy (DFS-047 decision 3). One of the API's own records: it maps from the core's
 * {@code FileEntry}, never the core type itself (endpoint archetype, checklist item 5).
 */
@Schema(name = "LinkView")
public record LinkView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The path as the scan holds it (Path.toString(): backslashes on Windows)")
        String path,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The file's modified time at scan time")
        Instant modified) {
}
