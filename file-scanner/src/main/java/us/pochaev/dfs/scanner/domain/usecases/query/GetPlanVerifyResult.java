package us.pochaev.dfs.scanner.domain.usecases.query;

import us.pochaev.dfs.scanner.domain.model.PlanVerifyId;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.VerifyReport;

/**
 * Reads a finished plan verify job's report (DFS-042 decision 3): the whole {@link VerifyReport}, in plan order.
 */
public interface GetPlanVerifyResult {

    /**
     * @return {@code Success} holding the full report for a COMPLETED job, or the partial one ({@code cancelled}
     *         true, the checks finished before the cancel) for a CANCELLED job; {@code Failure(PLAN_VERIFY_NOT_FINISHED)},
     *         whose message names the status, for QUEUED, RUNNING, CANCELLING and FAILED; or
     *         {@code Failure(UNKNOWN_PLAN_VERIFY)} naming the ID
     * @throws NullPointerException if {@code id} is null
     */
    Result<VerifyReport> result(PlanVerifyId id);
}
