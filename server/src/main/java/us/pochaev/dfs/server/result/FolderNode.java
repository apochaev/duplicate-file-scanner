package us.pochaev.dfs.server.result;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One folder of a COMPLETED scan's tree (DFS-047 decision 2): a root, or a folder below one that holds at least one
 * counted file in its subtree. One of the API's own records: it maps from the core's {@code ScanResult.roots} and
 * {@code FolderTotal}s, rolled up, never the core types themselves (endpoint archetype, checklist item 5).
 * <p>
 * {@code files} and {@code bytes} count the whole subtree; {@code directFiles} and {@code directBytes} only the
 * folder's own files (0 for a folder that holds only subfolders). Totals count paths as walked, so two hard links
 * count twice, as in {@code FolderTotal}.
 * <p>
 * {@code duplicateFiles} and {@code duplicateBytes} (DFS-059 decision 1) count, over the whole subtree, the counted
 * files whose content has at least two physical copies in the scan, the way {@code files} and {@code bytes} count
 * (each link path once), so {@code duplicateFiles <= files} and {@code duplicateBytes <= bytes} always hold. Hard
 * links of one physical file with no other copy are not duplicates and count 0.
 */
@Schema(name = "FolderNode")
public record FolderNode(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The folder's path as the scan holds it (Path.toString(): backslashes on Windows)")
        String path,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The last name element; for a filesystem root such as C:\\ the whole path")
        String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Counted files in the whole subtree")
        long files,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Their summed size in bytes")
        long bytes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Counted files in the whole subtree whose content has at least two physical copies in"
                        + " the scan, each link path once, as files counts; at most files")
        long duplicateFiles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Their summed size in bytes; at most bytes")
        long duplicateBytes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Counted files directly in this folder")
        long directFiles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Their summed size in bytes")
        long directBytes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether listFolders with this folder as parent answers at least one folder")
        boolean hasChildren) {
}
