package us.pochaev.dfs.server.result;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One folder of a {@link FolderGroupView}: a folder that directly holds link paths of the group's sets, never one of
 * its parents (DFS-067). One of the API's own records: it maps from the overlap package's {@code GroupFolder}, never a
 * core type (endpoint archetype, checklist item 5).
 */
@Schema(name = "GroupFolderView")
public record GroupFolderView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The folder's path as the scan holds it (Path.toString(): backslashes on Windows)")
        String folder,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Physical copies of each set of the group"
                + " with a path directly in this folder; a copy with two links here counts once")
        int copiesEach,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The folder's link paths in the group's sets; two links of one copy here count twice")
        long sharedFiles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Their summed size in bytes")
        long sharedBytes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Counted files directly in the folder (from the scan's folder totals)")
        long folderFiles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Their summed size in bytes")
        long folderBytes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "How many other groups of the scan list"
                + " this folder, whatever under and minBytes asked for")
        int otherGroups) {
}
