package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.PlanExecutionId;

import java.nio.file.Path;

/**
 * A plan execution's progress, the {@code P} of its {@link Job} (DFS-051 decision 9). With the job's status and
 * failure it makes the {@code PlanExecutionSnapshot}; it also carries what a run cancelled while QUEUED needs to build
 * its report (every operation not attempted).
 *
 * @param runId          the run's ID
 * @param manifest       the run's manifest path
 * @param plan           the executed plan
 * @param operationsDone the last {@code OperationDone.done}; 0 until the first one
 * @param bytesDone      the last {@code BytesCompared.bytesDone}; 0 until the first one
 * @param bytesTotal     the sum of the plan's operation sizes
 * @param filesMoved     paths moved so far
 * @param bytesMoved     sizes of operations moved whole so far
 */
public record PlanExecutionProgress(PlanExecutionId runId, Path manifest, DeletionPlan plan, int operationsDone,
                                    long bytesDone, long bytesTotal, int filesMoved, long bytesMoved) {

    public PlanExecutionProgress {
        java.util.Objects.requireNonNull(runId, "runId");
        java.util.Objects.requireNonNull(manifest, "manifest");
        java.util.Objects.requireNonNull(plan, "plan");
        if (operationsDone < 0 || operationsDone > plan.operations().size()) {
            throw new IllegalArgumentException("operationsDone out of range: " + operationsDone);
        }
        if (bytesDone < 0 || bytesDone > bytesTotal) {
            throw new IllegalArgumentException("bytesDone out of range: " + bytesDone);
        }
        if (filesMoved < 0 || bytesMoved < 0) {
            throw new IllegalArgumentException("moved totals must not be negative");
        }
    }
}
