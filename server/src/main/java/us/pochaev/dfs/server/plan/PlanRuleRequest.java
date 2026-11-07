package us.pochaev.dfs.server.plan;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One rule of a preview request (DFS-054): in every duplicate set with a copy under {@code keep}, quarantine each copy
 * wholly under {@code quarantineIn} that has no link under {@code keep}. One of the API's own records: it maps onto the
 * core's {@code KeepAndQuarantine} (endpoint archetype, checklist item 5).
 */
@Schema(name = "PlanRuleRequest")
public record PlanRuleRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "An absolute folder (or file) inside the scan's roots whose copies are kept")
        String keep,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "An absolute folder inside the scan's roots whose copies are quarantined; neither equal"
                        + " to keep nor under it (INVALID_RULE)")
        String quarantineIn) {
}
