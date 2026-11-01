package us.pochaev.dfs.scanner.domain.usecases.query;

import us.pochaev.dfs.scanner.domain.model.PlanVerifyId;
import us.pochaev.dfs.scanner.domain.model.PlanVerifySnapshot;
import us.pochaev.dfs.scanner.domain.model.Result;

/**
 * Reads a plan verify job's current snapshot (DFS-042 decision 3): its status and its entry and byte counts. A job
 * that failed is a {@code Success} whose snapshot says FAILED.
 */
public interface GetPlanVerifyStatus {

    /**
     * @return {@code Success} holding the current snapshot, or {@code Failure(UNKNOWN_PLAN_VERIFY)} naming the ID
     * @throws NullPointerException if {@code id} is null
     */
    Result<PlanVerifySnapshot> status(PlanVerifyId id);
}
