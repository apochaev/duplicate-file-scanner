package us.pochaev.dfs.scanner.domain.usecases.query;

import us.pochaev.dfs.scanner.domain.model.PlanExecutionId;
import us.pochaev.dfs.scanner.domain.model.PlanExecutionSnapshot;
import us.pochaev.dfs.scanner.domain.model.Result;

/**
 * Reads a plan execution's current snapshot (DFS-051 decision 9): its status, operation and byte counts, and files
 * and bytes moved. A run that failed is a {@code Success} whose snapshot says FAILED.
 */
public interface GetPlanExecutionStatus {

    /**
     * @return {@code Success} holding the current snapshot, or {@code Failure(UNKNOWN_PLAN_EXECUTION)} naming the ID
     * @throws NullPointerException if {@code id} is null
     */
    Result<PlanExecutionSnapshot> status(PlanExecutionId id);
}
