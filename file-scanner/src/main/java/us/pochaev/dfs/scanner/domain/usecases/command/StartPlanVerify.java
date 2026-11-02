package us.pochaev.dfs.scanner.domain.usecases.command;

import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.PlanVerifyId;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.VerifyListener;

/**
 * Starts a plan content verify ({@code VerifyPlanContent}, DFS-034) as a background job on the caller-supplied
 * executor and returns its ID at once (DFS-042, {@code ui.md} U7.3).
 * <p>
 * On the caller's thread: a null argument throws before anything is recorded. Otherwise the job is recorded QUEUED,
 * with the plan's operation count and total size, and its task submitted; if the executor rejects it, the job becomes
 * FAILED and the result is {@code Failure(EXECUTOR_REJECTED)}. An empty plan is accepted; its job completes with a
 * report with no checks. The verify is read-only: nothing is written, moved or deleted.
 */
public interface StartPlanVerify {

    /**
     * Starts a verify without a listener; progress is available through {@code GetPlanVerifyStatus}.
     *
     * @return {@code Success} holding the new job's ID, or {@code Failure(EXECUTOR_REJECTED)}
     * @throws NullPointerException if {@code plan} is null
     */
    Result<PlanVerifyId> start(DeletionPlan plan);

    /**
     * Starts a verify whose {@code BytesCompared} and {@code EntryChecked} events also go to {@code listener}, called
     * synchronously on the executor's thread that runs the job, never on the caller's. A listener that throws is
     * detached at its first exception and the verify carries on; the report's {@code listenerFailure} records it.
     *
     * @return {@code Success} holding the new job's ID, or {@code Failure(EXECUTOR_REJECTED)}
     * @throws NullPointerException if {@code plan} or {@code listener} is null
     */
    Result<PlanVerifyId> start(DeletionPlan plan, VerifyListener listener);
}
