package us.pochaev.dfs.server;

import io.swagger.v3.oas.annotations.media.Schema;

/** The answer of {@code GET /api/session}: {@code {"authenticated":true}} (DFS-038). */
record SessionStatus(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean authenticated) {
}
