package us.pochaev.dfs.server.scan;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** One scan of this server run (DFS-040 decision 2): its ID, the folders as given, when it started, its progress. */
@Schema(name = "ScanEntry")
public record ScanEntry(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> locations,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant startedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ScanProgress progress) {
}
