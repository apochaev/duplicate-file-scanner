package us.pochaev.dfs.server.scan;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * One scan of this server run (DFS-040 decision 2): its ID, the folders as given, when it started, when it ended, its
 * progress. {@code finishedAt} (DFS-064 decision 3) is null while the scan is QUEUED, RUNNING or CANCELLING, and once
 * it is COMPLETED, CANCELLED or FAILED the instant the server saw it end, fixed from then on.
 */
@Schema(name = "ScanEntry")
public record ScanEntry(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> locations,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant startedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant finishedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ScanProgress progress) {
}
