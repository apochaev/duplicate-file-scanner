package us.pochaev.dfs.scanner.domain.usecases.query;

import us.pochaev.dfs.scanner.domain.model.PlanExecutionId;
import us.pochaev.dfs.scanner.domain.model.PlanExecutionReport;
import us.pochaev.dfs.scanner.domain.model.Result;

/**
 * Reads a finished plan execution's report (DFS-051 decision 9): every operation's outcome in plan order and the
 * totals.
 */
public interface GetPlanExecutionResult {

    /**
     * @return {@code Success} holding the report of a COMPLETED or CANCELLED run (a cancelled run lists the operations
     *         it never reached as not attempted); {@code Failure(PLAN_EXECUTION_NOT_FINISHED)}, whose message names the
     *         status, for QUEUED, RUNNING, CANCELLING and FAILED; or {@code Failure(UNKNOWN_PLAN_EXECUTION)} naming the ID
     * @throws NullPointerException if {@code id} is null
     */
    Result<PlanExecutionReport> result(PlanExecutionId id);
}
