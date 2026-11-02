package us.pochaev.dfs.server.result;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Two folders and the copies between them, both directions (DFS-048 decision 6). One of the API's own records: it maps
 * one to one from {@code us.pochaev.dfs.server.analysis.FolderPair}. {@code a}'s folder sorts before {@code b}'s by
 * path, whatever order a client gave them in.
 */
@Schema(name = "FolderPairView")
public record FolderPairView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The side whose folder sorts first")
        PairSideView a,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The side whose folder sorts second")
        PairSideView b) {
}
