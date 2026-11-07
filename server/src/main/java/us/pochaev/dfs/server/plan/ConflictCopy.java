package us.pochaev.dfs.server.plan;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * One physical copy of a conflicting set and the conflict's rules that touch it (DFS-054 decision 4). Built by the
 * server from the scan's {@code PhysicalCopy} and the request's rules and exceptions, restricted to the rules among the
 * conflict's {@code sources}. One of the API's own records (endpoint archetype, checklist item 5).
 */
@Schema(name = "ConflictCopy")
public record ConflictCopy(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Every link of the copy (hard links are one copy), at least one, ordered by path")
        List<String> links,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Ascending indexes of the conflict's rules that would quarantine this copy: every link"
                        + " under the rule's quarantineIn, none under its keep, none at or under an exception")
        List<Integer> quarantinedBy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Ascending indexes of the conflict's rules that keep this copy as their keeper: of the"
                        + " copies with a link under the rule's keep, the one whose first link comes first by path")
        List<Integer> keptBy) {
}
