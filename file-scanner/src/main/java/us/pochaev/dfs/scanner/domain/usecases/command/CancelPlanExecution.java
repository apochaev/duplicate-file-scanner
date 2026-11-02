package us.pochaev.dfs.scanner.domain.usecases.command;

import us.pochaev.dfs.scanner.domain.model.PlanExecutionId;
import us.pochaev.dfs.scanner.domain.model.PlanExecutionSnapshot;
import us.pochaev.dfs.scanner.domain.model.Result;

/**
 * Requests that a plan execution stop (DFS-051 decision 7a). A QUEUED run becomes CANCELLED at once and its task exits
 * without touching any file; a RUNNING one becomes CANCELLING, finishes the operation in progress (every link's check,
 * rename and manifest lines), writes the manifest's {@code end} line and turns CANCELLED. On a CANCELLING or terminal
 * run it changes nothing.
 */
public interface CancelPlanExecution {

    /**
     * @return {@code Success} holding the snapshot after the request, or {@code Failure(UNKNOWN_PLAN_EXECUTION)} naming
     *         the ID
     * @throws NullPointerException if {@code id} is null
     */
    Result<PlanExecutionSnapshot> cancel(PlanExecutionId id);
}
