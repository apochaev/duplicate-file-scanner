package us.pochaev.dfs.server.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A refused request (DFS-040 decision 4): a code a program can test and a message a person can read. Shared by every
 * feature package since DFS-047 (decision 7); the schema name stays {@code ApiError}.
 */
@Schema(name = "ApiError")
public record ApiError(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) {
}
