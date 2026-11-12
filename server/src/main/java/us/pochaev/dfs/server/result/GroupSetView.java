package us.pochaev.dfs.server.result;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One duplicate set listed in a {@link FolderGroupView} (DFS-067). One of the API's own records: it maps from the
 * overlap package's {@code GroupSet}, never a core type (endpoint archetype, checklist item 5).
 */
@Schema(name = "GroupSetView")
public record GroupSetView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Lowercase hexadecimal SHA-256 of the content; identifies the set, as in /sets")
        String digest,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The file name of the set's first path")
        String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Size of each file in bytes")
        long size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The set's physical copies, at least two;"
                + " hard links add none")
        int copies) {
}
