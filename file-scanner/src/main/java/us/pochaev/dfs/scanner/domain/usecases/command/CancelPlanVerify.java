package us.pochaev.dfs.scanner.domain.usecases.command;

import us.pochaev.dfs.scanner.domain.model.PlanVerifyId;
import us.pochaev.dfs.scanner.domain.model.PlanVerifySnapshot;
import us.pochaev.dfs.scanner.domain.model.Result;

/**
 * Requests that a plan verify job stop (DFS-042 decision 2). A QUEUED job becomes CANCELLED at once and its task
 * exits without reading anything; a RUNNING one becomes CANCELLING, its verify stops within one chunk, and it turns
 * CANCELLED once the verify has returned. On a CANCELLING or terminal job it changes nothing.
 */
public interface CancelPlanVerify {

    /**
     * @return {@code Success} holding the snapshot after the request (CANCELLED, CANCELLING, or the state it already
     *         had), or {@code Failure(UNKNOWN_PLAN_VERIFY)} naming the ID
     * @throws NullPointerException if {@code id} is null
     */
    Result<PlanVerifySnapshot> cancel(PlanVerifyId id);
}
