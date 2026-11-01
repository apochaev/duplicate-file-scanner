package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.PlanVerifyId;
import us.pochaev.dfs.scanner.domain.model.PlanVerifySnapshot;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.VerifyListener;
import us.pochaev.dfs.scanner.domain.model.VerifyReport;
import us.pochaev.dfs.scanner.domain.usecases.command.CancelPlanVerify;
import us.pochaev.dfs.scanner.domain.usecases.command.StartPlanVerify;
import us.pochaev.dfs.scanner.domain.usecases.query.GetPlanVerifyResult;
import us.pochaev.dfs.scanner.domain.usecases.query.GetPlanVerifyStatus;
import us.pochaev.dfs.scanner.domain.usecases.query.VerifyPlanContent;
import us.pochaev.dfs.scanner.infra.port.JobStore;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Runs DFS-034's synchronous {@link VerifyPlanContent} as background jobs on a caller-supplied {@link Executor} and
 * tracks each by {@link PlanVerifyId} (DFS-042). The lifecycle (QUEUED to a terminal status, cancel, rejection,
 * failures) is the shared {@link JobRunner}'s; this class adds the plan's totals, the snapshot kept current from the
 * verify's events, the caller's listener with its own detach, and the report. Stateless and shareable: its fields
 * are the verifier and the job runner (which holds the store and the executor), nothing else.
 */
public final class PlanVerifyService implements StartPlanVerify, CancelPlanVerify, GetPlanVerifyStatus,
        GetPlanVerifyResult {

    private final VerifyPlanContent verifier;
    private final JobRunner<PlanVerifyId, PlanVerifyProgress, VerifyReport> jobs;

    /**
     * @param verifier the synchronous verify each job runs ({@code VerifyPlanContentService} in production; tests build
     *                 it over a fake attributes reader and a fake comparator)
     * @param store    holds every job's record
     * @param executor runs every job's task
     * @throws NullPointerException if any argument is null
     */
    public PlanVerifyService(VerifyPlanContent verifier,
                             JobStore<PlanVerifyId, Job<PlanVerifyProgress, VerifyReport>> store,
                             Executor executor) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.jobs = new JobRunner<>(store, executor, new VerifyOutcomes());
    }

    @Override
    public Result<PlanVerifyId> start(DeletionPlan plan) {
        throw new UnsupportedOperationException("not implemented: DFS-042");
    }

    @Override
    public Result<PlanVerifyId> start(DeletionPlan plan, VerifyListener listener) {
        throw new UnsupportedOperationException("not implemented: DFS-042");
    }

    @Override
    public Result<PlanVerifySnapshot> cancel(PlanVerifyId id) {
        throw new UnsupportedOperationException("not implemented: DFS-042");
    }

    @Override
    public Result<PlanVerifySnapshot> status(PlanVerifyId id) {
        throw new UnsupportedOperationException("not implemented: DFS-042");
    }

    @Override
    public Result<VerifyReport> result(PlanVerifyId id) {
        throw new UnsupportedOperationException("not implemented: DFS-042");
    }

    /** A verify's two use-case-specific results (the story's {@code design.md}, "Behavior"). */
    private static final class VerifyOutcomes implements JobRunner.Outcomes<PlanVerifyProgress, VerifyReport> {

        /** {@code new VerifyReport(progress.planId(), List.of(), true, "")}. */
        @Override
        public VerifyReport cancelledBeforeStart(PlanVerifyProgress progress) {
            throw new UnsupportedOperationException("not implemented: DFS-042");
        }

        /** The same report with {@code cancelled} true; checks and listenerFailure unchanged. */
        @Override
        public VerifyReport markCancelled(VerifyReport result) {
            throw new UnsupportedOperationException("not implemented: DFS-042");
        }
    }
}
