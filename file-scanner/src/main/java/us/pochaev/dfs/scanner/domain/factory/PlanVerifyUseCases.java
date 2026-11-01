package us.pochaev.dfs.scanner.domain.factory;

import us.pochaev.dfs.scanner.domain.usecases.command.CancelPlanVerify;
import us.pochaev.dfs.scanner.domain.usecases.command.StartPlanVerify;
import us.pochaev.dfs.scanner.domain.usecases.query.GetPlanVerifyResult;
import us.pochaev.dfs.scanner.domain.usecases.query.GetPlanVerifyStatus;

/**
 * The plan verify job use cases of one build of {@link ScannerFactories#planVerifyUseCases}, all backed by the same
 * job store: a {@code PlanVerifyId} returned by {@link #start} is known to {@link #cancel}, {@link #status} and
 * {@link #result} of the same instance, and to no other instance (DFS-042 decision 6).
 *
 * @param start  starts plan verify jobs on the executor the set was built with
 * @param cancel requests that a job of this set stop
 * @param status reads a job's current snapshot
 * @param result reads a finished job's report
 */
public record PlanVerifyUseCases(StartPlanVerify start, CancelPlanVerify cancel, GetPlanVerifyStatus status,
                                 GetPlanVerifyResult result) {

    public PlanVerifyUseCases {
    }
}
