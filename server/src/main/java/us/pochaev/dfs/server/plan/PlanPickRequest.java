package us.pochaev.dfs.server.plan;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One single-set pick of a preview request (DFS-054): in the set with {@code digest}, keep the copy that has the link
 * {@code keep} and quarantine every other copy, whatever the rules say. One of the API's own records: it maps onto the
 * core's {@code KeepCopy} (endpoint archetype, checklist item 5).
 */
@Schema(name = "PlanPickRequest")
public record PlanPickRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "The digest of one of the scan's duplicate sets (UNKNOWN_SET otherwise)")
        String digest,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "An absolute path inside the scan's roots that is a link of that set (PATH_NOT_IN_SET"
                        + " otherwise)")
        String keep) {
}
