package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.ErrorCode;
import us.pochaev.dfs.scanner.domain.model.JobStatus;
import us.pochaev.dfs.scanner.domain.model.Operation;
import us.pochaev.dfs.scanner.domain.model.PlanVerifyId;
import us.pochaev.dfs.scanner.domain.model.VerifyEvent;
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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
        Objects.requireNonNull(plan, "plan");
        return begin(plan, null);
    }

    @Override
    public Result<PlanVerifyId> start(DeletionPlan plan, VerifyListener listener) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(listener, "listener");
        return begin(plan, listener);
    }

    @Override
    public Result<PlanVerifySnapshot> cancel(PlanVerifyId id) {
        Objects.requireNonNull(id, "id");
        return jobs.cancel(id).<Result<PlanVerifySnapshot>>map(j -> new Result.Success<>(snapshot(j)))
                .orElseGet(() -> unknown(id));
    }

    @Override
    public Result<PlanVerifySnapshot> status(PlanVerifyId id) {
        Objects.requireNonNull(id, "id");
        return jobs.find(id).<Result<PlanVerifySnapshot>>map(j -> new Result.Success<>(snapshot(j)))
                .orElseGet(() -> unknown(id));
    }

    @Override
    public Result<VerifyReport> result(PlanVerifyId id) {
        Objects.requireNonNull(id, "id");
        Optional<Job<PlanVerifyProgress, VerifyReport>> found = jobs.find(id);
        if (found.isEmpty()) {
            return unknown(id);
        }
        Job<PlanVerifyProgress, VerifyReport> job = found.get();
        if (job.status() == JobStatus.COMPLETED || job.status() == JobStatus.CANCELLED) {
            return new Result.Success<>(job.result());
        }
        return new Result.Failure<>(ErrorCode.PLAN_VERIFY_NOT_FINISHED,
                "plan verify " + id.value() + " has status " + job.status() + ", no result");
    }

    private Result<PlanVerifyId> begin(DeletionPlan plan, VerifyListener listener) {
        long bytesTotal = 0;
        for (Operation op : plan.operations()) {
            bytesTotal += ((Operation.Quarantine) op).size();
        }
        PlanVerifyProgress initial = new PlanVerifyProgress(plan.id(), 0, plan.operations().size(), 0, bytesTotal);
        return jobs.submit(PlanVerifyId.random(), initial, (progress, stop) -> {
            String[] failure = {null};
            boolean[] detached = {false};
            VerifyListener combined = event -> {
                progress.update(p -> switch (event) {
                    case VerifyEvent.BytesCompared b -> new PlanVerifyProgress(p.planId(), p.entriesDone(),
                            p.entriesTotal(), b.bytesDone(), p.bytesTotal());
                    case VerifyEvent.EntryChecked c -> new PlanVerifyProgress(p.planId(), c.done(),
                            p.entriesTotal(), p.bytesDone(), p.bytesTotal());
                });
                if (listener != null && !detached[0]) {
                    try {
                        listener.onEvent(event);
                    } catch (RuntimeException e) {
                        detached[0] = true;
                        String m = e.getMessage();
                        failure[0] = m == null || m.isBlank() ? e.toString() : m;
                    }
                }
            };
            Result<VerifyReport> out = verifier.verify(plan, combined, stop);
            VerifyReport r = ((Result.Success<VerifyReport>) out).value();
            return failure[0] == null ? r : new VerifyReport(r.planId(), r.checks(), r.cancelled(), failure[0]);
        });
    }

    private static PlanVerifySnapshot snapshot(Job<PlanVerifyProgress, VerifyReport> job) {
        PlanVerifyProgress p = job.progress();
        return new PlanVerifySnapshot(job.status(), p.planId(), p.entriesDone(), p.entriesTotal(), p.bytesDone(),
                p.bytesTotal(), job.failure());
    }

    private static <T> Result<T> unknown(PlanVerifyId id) {
        return new Result.Failure<>(ErrorCode.UNKNOWN_PLAN_VERIFY, "unknown plan verify: " + id.value());
    }

    /** A verify's two use-case-specific results (the story's {@code design.md}, "Behavior"). */
    private static final class VerifyOutcomes implements JobRunner.Outcomes<PlanVerifyProgress, VerifyReport> {

        /** {@code new VerifyReport(progress.planId(), List.of(), true, "")}. */
        @Override
        public VerifyReport cancelledBeforeStart(PlanVerifyProgress progress) {
            return new VerifyReport(progress.planId(), List.of(), true, "");
        }

        /** The same report with {@code cancelled} true; checks and listenerFailure unchanged. */
        @Override
        public VerifyReport markCancelled(VerifyReport result) {
            return new VerifyReport(result.planId(), result.checks(), true, result.listenerFailure());
        }
    }
}
