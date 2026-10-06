package us.pochaev.dfs.scanner.domain.model;

/**
 * Where a scan is in its lifecycle (DFS-018 decision 3). COMPLETED, CANCELLED and FAILED are
 * terminal: nothing changes a scan once it reaches one of them.
 */
public enum ScanStatus {
    /** Recorded by {@code StartScan} and handed to the executor, which has not started it yet. */
    QUEUED,
    /** The executor started the task and the pipeline is working. */
    RUNNING,
    /** A cancel was requested while RUNNING; the pipeline has not stopped yet. */
    CANCELLING,
    /** The pipeline returned its result. Terminal. */
    COMPLETED,
    /** Cancelled while QUEUED, or the pipeline stopped after a cancel request. Terminal. */
    CANCELLED,
    /** The pipeline threw, or the executor rejected the task. Terminal. */
    FAILED
}
