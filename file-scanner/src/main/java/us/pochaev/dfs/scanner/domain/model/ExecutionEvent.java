package us.pochaev.dfs.scanner.domain.model;

/** A progress event of a plan execution, sent to an {@link ExecutionListener} (DFS-051 decision 9). */
public sealed interface ExecutionEvent {

    /**
     * Bytes compared so far across the whole plan, by the {@code VerifyEvent.BytesCompared} rule: {@code bytesTotal}
     * is the sum of the plan's operation sizes; each finished operation counts its size (compared or not) plus the
     * part of the current one compared so far, so it never decreases and ends at {@code bytesTotal} when every
     * operation was attempted.
     */
    record BytesCompared(long bytesDone, long bytesTotal) implements ExecutionEvent {

        public BytesCompared {
            if (bytesDone < 0 || bytesDone > bytesTotal) {
                throw new IllegalArgumentException("bytesDone out of range: " + bytesDone);
            }
        }
    }

    /** Operation {@code done} of {@code total} ended with {@code outcome}; {@code done} rises from 1. */
    record OperationDone(OperationOutcome outcome, int done, int total) implements ExecutionEvent {

        public OperationDone {
            java.util.Objects.requireNonNull(outcome, "outcome");
            if (done < 1 || done > total) {
                throw new IllegalArgumentException("done out of range: " + done);
            }
        }
    }
}
