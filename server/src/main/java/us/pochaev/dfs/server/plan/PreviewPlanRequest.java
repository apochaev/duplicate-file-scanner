package us.pochaev.dfs.server.plan;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The body of {@code POST /api/scans/{id}/plans} (DFS-054 decisions 1, 5): the whole draft the browser holds, previewed
 * at once. One of the API's own records: it maps onto the core's {@code PlanRequest}, never the core type itself
 * (endpoint archetype, checklist item 5).
 * <p>
 * Every path must be absolute ({@code 400 RELATIVE_PATH}) and inside one of the scan's roots
 * ({@code 403 PATH_OUTSIDE_ROOTS}), checked in request order (rules, then exceptions, then picks) before the core is
 * asked; the error message names the field ({@code rules[1].keep}). A list the client leaves out ({@code null}) counts
 * as empty; an empty request previews an empty plan.
 */
@Schema(name = "PreviewPlanRequest")
public record PreviewPlanRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Applied in order; an operation's or conflict's ruleIndex is the position here, from 0")
        List<PlanRuleRequest> rules,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Absolute files or folders inside the scan's roots that no rule quarantines a copy at"
                        + " or under")
        List<String> exceptions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "At most one per digest (DUPLICATE_PICK); a pick overrides the rules for its set")
        List<PlanPickRequest> picks) {
}
