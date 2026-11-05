package us.pochaev.dfs.scanner.domain.factory;

import us.pochaev.dfs.scanner.domain.usecases.command.CancelPlanExecution;
import us.pochaev.dfs.scanner.domain.usecases.command.StartPlanExecution;
import us.pochaev.dfs.scanner.domain.usecases.query.GetPlanExecutionResult;
import us.pochaev.dfs.scanner.domain.usecases.query.GetPlanExecutionStatus;

/**
 * The plan execution use cases of one build of {@link ScannerFactories#planExecutionUseCases}, all backed by the same
 * job store: a {@code PlanExecutionId} returned by {@link #start} is known to {@link #cancel}, {@link #status} and
 * {@link #result} of the same instance, and to no other instance (DFS-051 decision 10).
 *
 * @param start  starts plan executions on the executor the set was built with
 * @param cancel requests that a run of this set stop
 * @param status reads a run's current snapshot
 * @param result reads a finished run's report
 */
public record PlanExecutionUseCases(StartPlanExecution start, CancelPlanExecution cancel,
                                    GetPlanExecutionStatus status, GetPlanExecutionResult result) {

    public PlanExecutionUseCases {
        java.util.Objects.requireNonNull(start, "start");
        java.util.Objects.requireNonNull(cancel, "cancel");
        java.util.Objects.requireNonNull(status, "status");
        java.util.Objects.requireNonNull(result, "result");
    }
}
