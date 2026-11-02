package us.pochaev.dfs.server.result;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * One duplicate set of a COMPLETED scan (DFS-047 decision 3). One of the API's own records: it maps from the core's
 * {@code DuplicateSet}, never the core type itself (endpoint archetype, checklist item 5).
 * <p>
 * {@code copies} lists every physical copy, also those outside the {@code under} folder the set was matched by.
 */
@Schema(name = "DuplicateSetView")
public record DuplicateSetView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Lowercase hexadecimal SHA-256 of the content; identifies the set")
        String digest,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Size of each file in bytes")
        long size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "size * (physical copies - 1): hard links are not reclaimable copies")
        long reclaimableBytes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "At least two; ordered by first link path")
        List<CopyView> copies) {
}
