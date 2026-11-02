package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.Conflict;
import us.pochaev.dfs.scanner.domain.model.Operation;

import java.util.List;

/**
 * A {@link us.pochaev.dfs.scanner.domain.model.DeletionPlan}'s content-derived ID (DFS-022 decision 1): lowercase hex
 * SHA-256 of its operations and conflicts in their canonical encoding. Shared by {@code PreviewDeletionPlanService},
 * which computes it, and {@code PlanExecutionService}, which recomputes it to refuse an edited plan (DFS-051
 * decision 14a). The implementer moves {@code PreviewDeletionPlanService.planId} and its encoding helpers here
 * unchanged; the preview's golden plan-ID tests prove the move.
 */
public final class PlanIds {

    private PlanIds() {
    }

    /**
     * @return the 64-character lowercase hex ID of a plan holding {@code operations} and {@code conflicts}, in the
     *         order given
     * @throws NullPointerException if an argument is or holds null
     */
    public static String of(List<? extends Operation> operations, List<Conflict> conflicts) {
        throw new UnsupportedOperationException("not implemented: DFS-051");
    }
}
