package us.pochaev.dfs.server.result;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * One physical copy of a duplicate set and every path that reaches it (DFS-047 decision 3): hard links of one file are
 * one copy, never duplicates of each other. One of the API's own records: it maps from the core's
 * {@code PhysicalCopy}, never the core type itself (endpoint archetype, checklist item 5).
 */
@Schema(name = "CopyView")
public record CopyView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "At least one; ordered by path")
        List<LinkView> links) {
}
