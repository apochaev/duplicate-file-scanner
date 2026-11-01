package us.pochaev.dfs.server.scan;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The body of {@code POST /api/scans} (DFS-040 decision 4): absolute folders, plus two optional settings that default
 * to the core's {@code ScanConfig.defaults()}.
 */
@Schema(name = "StartScanRequest")
public record StartScanRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Absolute paths of the folders to scan")
        List<String> locations,
        @Schema(description = "Files smaller than this many bytes are left out; at least 1 (default 1)")
        Long minSize,
        @Schema(description = "Leave out .DS_Store and ._* files (default true)")
        Boolean defaultExcludes) {
}
