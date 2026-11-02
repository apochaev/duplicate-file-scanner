package us.pochaev.dfs.scanner.domain.model;

/**
 * Where a long-running job (a plan verify now, a plan execution later) is in its lifecycle (DFS-042 decision 2).
 * COMPLETED, CANCELLED and FAILED are terminal: nothing changes a job once it reaches one of them. The scan keeps its
 * own {@link ScanStatus} until a later refactor merges the two.
 */
public enum JobStatus {
    /** Recorded by the start command and handed to the executor, which has not started it yet. */
    QUEUED,
    /** The executor started the task and the work is running. */
    RUNNING,
    /** A cancel was requested while RUNNING; the work has not returned yet. */
    CANCELLING,
    /** The work returned and no cancel had been recorded before. Terminal. */
    COMPLETED,
    /** Cancelled while QUEUED, or the work returned after a cancel was recorded. Terminal. */
    CANCELLED,
    /** The work threw, or the executor rejected the task. Terminal. */
    FAILED
}
