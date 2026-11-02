package us.pochaev.dfs.server.result;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One folder's whole subtree in a COMPLETED scan's overlap analysis (DFS-048 decision 6): "can this folder go?". One of
 * the API's own records: it maps one to one from {@code us.pochaev.dfs.server.analysis.FolderSummary}, never that type
 * itself (endpoint archetype, checklist item 5).
 * <p>
 * A file is a path, as the scan's folder totals count it; a copy is a <em>different</em> physical copy of its set, so
 * hard links never count.
 */
@Schema(name = "FolderSummaryView")
public record FolderSummaryView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The folder's path as the scan holds it (Path.toString(): backslashes on Windows)")
        String folder,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Counted files in the whole subtree")
        long totalFiles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Their summed size in bytes")
        long totalBytes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Files in the subtree with a different"
                + " physical copy of their content outside the subtree; each file counts once")
        long filesWithCopyElsewhere,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Their summed size in bytes")
        long bytesWithCopyElsewhere,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "For each content with k >= 2 physical"
                + " copies inside the subtree, k - 1")
        long internalDuplicateFiles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "For each such content, (k - 1) x size")
        long internalBytes) {
}
