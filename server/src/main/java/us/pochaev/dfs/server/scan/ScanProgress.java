package us.pochaev.dfs.server.scan;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Where a scan is (DFS-040 decision 3): the core's snapshot plus the counts folded from its events. The hash counts
 * belong to the current hashing phase. {@code groupsDone} and {@code groupsTotal} count the current phase's groups
 * while the scan runs; once it is COMPLETED both are the number of duplicate sets found.
 */
@Schema(name = "ScanProgress")
public record ScanProgress(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"QUEUED", "RUNNING", "CANCELLING", "COMPLETED", "CANCELLED", "FAILED"})
        String status,
        @Schema(nullable = true, allowableValues = {"WALK", "PARTIAL_HASH", "FULL_HASH", "HARD_LINKS"})
        String phase,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long directories,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long files,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long bytes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long hashedFiles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long hashFilesTotal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long hashedBytes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long hashBytesTotal,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int groupsDone,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int groupsTotal,
        @Schema(nullable = true) String failureMessage) {
}
