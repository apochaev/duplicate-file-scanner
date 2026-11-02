package us.pochaev.dfs.scanner.domain.usecases.command;

import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.ExecutionListener;
import us.pochaev.dfs.scanner.domain.model.PlanExecutionId;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanResult;

/**
 * Executes a previewed deletion plan as a background job on the caller-supplied executor, moving every planned link
 * into a quarantine folder on its own volume and recording each step in an append-only manifest, and returns the run's
 * ID at once (DFS-051, CLAUDE.md decisions 2 and 4).
 * <p>
 * On the caller's thread, before anything is recorded, written or moved: a null argument throws; then the plan is
 * refused with {@code Failure(PLAN_EDITED)} when its ID does not match its content, {@code Failure(PLAN_HAS_CONFLICTS)}
 * when it has conflicts, {@code Failure(PLAN_NOT_FROM_SCAN)} when an operation's digest or link is not in
 * {@code scan}, {@code Failure(KEEPER_NOT_SEPARATE_COPY)} when an operation's links are not one physical copy or its
 * keeper is not a link of another copy, and {@code Failure(MANIFEST_UNWRITABLE)} when the run's manifest cannot be
 * created. Otherwise the manifest holds its {@code run} line, the run is recorded QUEUED and its task submitted; if the
 * executor rejects it, the run becomes FAILED and the result is {@code Failure(EXECUTOR_REJECTED)}.
 * <p>
 * On the executor's thread, each link is re-checked against {@code scan} (size and last-modified time, without
 * following links) and its operation compared byte for byte with the keeper once, just before it moves; a link that
 * fails is skipped and the run continues. Cancel takes effect between operations, never inside one.
 */
public interface StartPlanExecution {

    /**
     * Starts an execution without a listener; progress is available through {@code GetPlanExecutionStatus}.
     *
     * @param scan the scan {@code plan} was previewed from; its {@code FileEntry}s are the "unchanged" baseline
     * @return {@code Success} holding the run's ID, or one of the {@code Failure}s above
     * @throws NullPointerException if {@code scan} or {@code plan} is null
     */
    Result<PlanExecutionId> start(ScanResult scan, DeletionPlan plan);

    /**
     * Starts an execution whose {@code BytesCompared} and {@code OperationDone} events also go to {@code listener},
     * called synchronously on the executor's thread, never on the caller's. A listener that throws is detached at its
     * first exception and the run carries on; the report's {@code listenerFailure} records it.
     *
     * @return {@code Success} holding the run's ID, or one of the {@code Failure}s above
     * @throws NullPointerException if {@code scan}, {@code plan} or {@code listener} is null
     */
    Result<PlanExecutionId> start(ScanResult scan, DeletionPlan plan, ExecutionListener listener);
}
