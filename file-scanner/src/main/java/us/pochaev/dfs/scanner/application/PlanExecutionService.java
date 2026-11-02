package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.ExecutionConfig;
import us.pochaev.dfs.scanner.domain.model.ExecutionListener;
import us.pochaev.dfs.scanner.domain.model.PlanExecutionId;
import us.pochaev.dfs.scanner.domain.model.PlanExecutionReport;
import us.pochaev.dfs.scanner.domain.model.PlanExecutionSnapshot;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.usecases.command.CancelPlanExecution;
import us.pochaev.dfs.scanner.domain.usecases.command.StartPlanExecution;
import us.pochaev.dfs.scanner.domain.usecases.query.GetPlanExecutionResult;
import us.pochaev.dfs.scanner.domain.usecases.query.GetPlanExecutionStatus;
import us.pochaev.dfs.scanner.infra.port.ContentComparator;
import us.pochaev.dfs.scanner.infra.port.FileAttributesReader;
import us.pochaev.dfs.scanner.infra.port.FileDeleter;
import us.pochaev.dfs.scanner.infra.port.JobStore;
import us.pochaev.dfs.scanner.infra.port.ManifestWriter;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Executes deletion plans into quarantine as background jobs on a caller-supplied {@link Executor} and tracks each by
 * {@link PlanExecutionId} (DFS-051). The lifecycle (QUEUED to a terminal status, cancel, rejection, failures) is the
 * shared {@link JobRunner}'s; this class adds the whole-plan validation on the caller's thread, the per-link
 * check-compare-move loop with its manifest lines, the snapshot kept current from the execution's events, the caller's
 * listener with its own detach, and the report. Stateless and shareable: its fields are its ports, the manifest
 * folder setting and the job runner (which holds the store and the executor).
 */
public final class PlanExecutionService implements StartPlanExecution, CancelPlanExecution, GetPlanExecutionStatus,
        GetPlanExecutionResult {

    private final FileAttributesReader reader;
    private final ContentComparator comparator;
    private final FileDeleter deleter;
    private final ManifestWriter manifests;
    private final ExecutionConfig config;
    private final JobRunner<PlanExecutionId, PlanExecutionProgress, PlanExecutionReport> jobs;

    /**
     * @param reader     reads each candidate's and keeper's attributes without following links (drift, decision 3)
     * @param comparator compares each operation's first checked link with its keeper (decision 4)
     * @param deleter    names quarantine paths and moves files (decision 5)
     * @param manifests  writes each run's manifest (decision 6)
     * @param store      holds every run's record
     * @param executor   runs every run's task
     * @param config     the manifest folder (its anchors are the deleter's business)
     * @throws NullPointerException if any argument is null
     */
    public PlanExecutionService(FileAttributesReader reader, ContentComparator comparator, FileDeleter deleter,
                                ManifestWriter manifests,
                                JobStore<PlanExecutionId, Job<PlanExecutionProgress, PlanExecutionReport>> store,
                                Executor executor, ExecutionConfig config) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.comparator = Objects.requireNonNull(comparator, "comparator");
        this.deleter = Objects.requireNonNull(deleter, "deleter");
        this.manifests = Objects.requireNonNull(manifests, "manifests");
        this.config = Objects.requireNonNull(config, "config");
        this.jobs = new JobRunner<>(store, executor, new ExecutionOutcomes());
    }

    @Override
    public Result<PlanExecutionId> start(ScanResult scan, DeletionPlan plan) {
        throw new UnsupportedOperationException("not implemented: DFS-051");
    }

    @Override
    public Result<PlanExecutionId> start(ScanResult scan, DeletionPlan plan, ExecutionListener listener) {
        throw new UnsupportedOperationException("not implemented: DFS-051");
    }

    @Override
    public Result<PlanExecutionSnapshot> cancel(PlanExecutionId id) {
        throw new UnsupportedOperationException("not implemented: DFS-051");
    }

    @Override
    public Result<PlanExecutionSnapshot> status(PlanExecutionId id) {
        throw new UnsupportedOperationException("not implemented: DFS-051");
    }

    @Override
    public Result<PlanExecutionReport> result(PlanExecutionId id) {
        throw new UnsupportedOperationException("not implemented: DFS-051");
    }

    /** An execution's two use-case-specific results (the story's {@code design.md}, "Behavior"). */
    private static final class ExecutionOutcomes
            implements JobRunner.Outcomes<PlanExecutionProgress, PlanExecutionReport> {

        /** Every operation {@code NotAttempted}, {@code cancelled} true, all totals 0, no listener failure. */
        @Override
        public PlanExecutionReport cancelledBeforeStart(PlanExecutionProgress progress) {
            throw new UnsupportedOperationException("not implemented: DFS-051");
        }

        /** The same report with {@code cancelled} true; outcomes, totals and listenerFailure unchanged. */
        @Override
        public PlanExecutionReport markCancelled(PlanExecutionReport result) {
            throw new UnsupportedOperationException("not implemented: DFS-051");
        }
    }
}
