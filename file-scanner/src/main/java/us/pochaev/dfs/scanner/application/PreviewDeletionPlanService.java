package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.PlanRequest;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.usecases.query.PreviewDeletionPlan;

/**
 * Validates a {@link PlanRequest} and builds the {@link DeletionPlan} it produces on a {@link ScanResult} (DFS-022).
 * Has no fields: a pure function of its arguments. The algorithm and the plan ID's canonical encoding are in the
 * story's {@code design.md}.
 */
public final class PreviewDeletionPlanService implements PreviewDeletionPlan {

    @Override
    public Result<DeletionPlan> preview(ScanResult scan, PlanRequest request) {
        throw new UnsupportedOperationException("not implemented: DFS-022");
    }
}
