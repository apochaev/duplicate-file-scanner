package us.pochaev.dfs.scanner.domain.model;

/**
 * A plan execution's state at one moment, as {@code GetPlanExecutionStatus} and {@code CancelPlanExecution} return it
 * (DFS-051 decision 9). Kept current from the same {@link ExecutionEvent}s the caller's {@link ExecutionListener}
 * receives. Counts only: per-operation outcomes come from the listener and the report.
 *
 * @param status          the run's lifecycle status
 * @param planId          the executed plan's {@link DeletionPlan#id()}
 * @param operationsDone  the last {@link ExecutionEvent.OperationDone#done()}; 0 until the first one
 * @param operationsTotal the plan's operation count, set when the run is recorded
 * @param bytesDone       the last {@link ExecutionEvent.BytesCompared#bytesDone()}; 0 until the first one
 * @param bytesTotal      the sum of the plan's operation sizes, set when the run is recorded
 * @param filesMoved      paths moved into quarantine so far (every moved link counts)
 * @param bytesMoved      the size of each operation moved whole, once (the {@link PlanExecutionReport} rule)
 * @param failure         for a FAILED run, {@code <exception class name>: <message>}; empty otherwise
 */
public record PlanExecutionSnapshot(JobStatus status, String planId, int operationsDone, int operationsTotal,
                                    long bytesDone, long bytesTotal, int filesMoved, long bytesMoved,
                                    String failure) {

    public PlanExecutionSnapshot {
        java.util.Objects.requireNonNull(status, "status");
        java.util.Objects.requireNonNull(planId, "planId");
        java.util.Objects.requireNonNull(failure, "failure");
        if (operationsDone < 0 || operationsDone > operationsTotal) {
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
