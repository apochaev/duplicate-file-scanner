package us.pochaev.dfs.server.plan;

import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.PlanRequest;
import us.pochaev.dfs.scanner.domain.model.ScanResult;

/**
 * Maps the core's {@code DeletionPlan} to the API's records (DFS-054 decisions 3, 4), so no core type reaches the spec.
 * <p>
 * Stubs (design phase): every method throws.
 */
final class PlanViews {

    private PlanViews() {
    }

    /**
     * The plan as the server keeps it: summary ({@code planId} = {@code plan.id()}, totals, {@code leftBehind} with
     * {@code folder} as {@code Path.toString()}, the two counts), operations and conflicts sorted as {@link KeptPlan}
     * says. A {@code Source.FromRule} maps to {@code PlanSource("rule", index, null)}, a {@code Source.FromPick} to
     * {@code PlanSource("pick", null, digest)}. Each conflict is joined with the scan's set of its digest: its
     * {@code size}, and one {@link ConflictCopy} per physical copy (links sorted by path, copies by first link) whose
     * {@code quarantinedBy} and {@code keptBy} are computed from {@code request}'s rules and exceptions as
     * {@link ConflictCopy} documents, among the conflict's rule sources only.
     *
     * @param plan    the core's plan for {@code request} on {@code scan}
     * @param request the core request it was previewed from
     * @param scan    the scan's whole result (for each conflict's set)
     */
    static KeptPlan keptPlan(DeletionPlan plan, PlanRequest request, ScanResult scan) {
        throw new UnsupportedOperationException("not implemented: DFS-054");
    }
}
