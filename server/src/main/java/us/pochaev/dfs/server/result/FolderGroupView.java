package us.pochaev.dfs.server.result;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Duplicate sets whose copies sit directly in the same folders with the same copies per folder (DFS-067,
 * docs/design/folder-groups.md G1 and G5). One of the API's own records: it maps from the overlap package's
 * {@code FolderGroup}, never a core type (endpoint archetype, checklist item 5).
 */
@Schema(name = "FolderGroupView")
public record FolderGroupView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The number of duplicate sets in the group")
        long files,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Physical copies over all its sets")
        long copies,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "For each set, (physical copies - 1) x size,"
                + " summed: the figure /sets uses")
        long reclaimableBytes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The folders that directly hold any of its"
                + " paths, once each, never their ancestors, sorted by path")
        List<GroupFolderView> folders,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The first 20 sets, largest reclaimable first, then by digest")
        List<GroupSetView> sets,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Every set of the group, not only the"
                + " listed ones")
        int totalSets) {
}
