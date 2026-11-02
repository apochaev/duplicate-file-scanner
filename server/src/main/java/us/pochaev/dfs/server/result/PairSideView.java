package us.pochaev.dfs.server.result;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One direction of a {@link FolderPairView} (DFS-048 decision 6): how many of this folder's files have a copy in the
 * other folder. One of the API's own records: it maps one to one from
 * {@code us.pochaev.dfs.server.analysis.PairSide}, its {@code full()} included.
 */
@Schema(name = "PairSideView")
public record PairSideView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "This side's folder (Path.toString(): backslashes on Windows)")
        String folder,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Its files with a different physical copy"
                + " of their content in the other folder; hard links never count")
        long filesWithCopy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Its counted files")
        long totalFiles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The summed size of filesWithCopy")
        long bytesWithCopy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The summed size of totalFiles")
        long totalBytes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "True exactly when this side has at least"
                + " one file and every one of them has a copy in the other folder")
        boolean full) {
}
