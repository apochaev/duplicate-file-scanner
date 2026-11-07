package us.pochaev.dfs.server.plan;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What produced an operation or took part in a conflict (DFS-054 decision 4): {@code {kind: "rule", ruleIndex}} or
 * {@code {kind: "pick", digest}}; the browser holds its own request, so the index or digest is enough. One of the API's
 * own records: it maps from the core's {@code Source}, never the core type itself (endpoint archetype, checklist
 * item 5).
 * <p>
 * Exactly one of {@code ruleIndex} and {@code digest} is set, by {@code kind}; the other is {@code null}.
 */
@Schema(name = "PlanSource")
public record PlanSource(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"rule", "pick"})
        String kind,
        @Schema(nullable = true, description = "For kind rule: the rule's position in the request, from 0; else null")
        Integer ruleIndex,
        @Schema(nullable = true, description = "For kind pick: the picked set's digest; else null")
        String digest) {
}
