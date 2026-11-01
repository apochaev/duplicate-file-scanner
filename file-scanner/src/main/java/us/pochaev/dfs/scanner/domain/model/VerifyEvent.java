package us.pochaev.dfs.scanner.domain.model;

/** A progress event of a plan content verify, sent to a {@link VerifyListener} (DFS-034 decision 5). */
public sealed interface VerifyEvent {

    /**
     * Bytes compared so far across the whole plan. {@code bytesTotal} is the sum of the plan's operation sizes;
     * {@code bytesDone} counts each finished operation by its plan size plus the part of the current one compared so
     * far (never more than its plan size), so it never decreases and ends at {@code bytesTotal} once every operation
     * is finished.
     */
    record BytesCompared(long bytesDone, long bytesTotal) implements VerifyEvent {

        public BytesCompared {
            if (bytesDone < 0 || bytesDone > bytesTotal) {
                throw new IllegalArgumentException("need 0 <= bytesDone <= bytesTotal: " + bytesDone + ", " + bytesTotal);
            }
        }
    }

    /** Operation {@code done} of {@code total} was checked; {@code done} rises from 1 to {@code total}. */
    record EntryChecked(OperationCheck check, int done, int total) implements VerifyEvent {

        public EntryChecked {
            java.util.Objects.requireNonNull(check, "check");
            if (done < 1 || done > total) {
                throw new IllegalArgumentException("need 1 <= done <= total: " + done + ", " + total);
            }
        }
    }
}
