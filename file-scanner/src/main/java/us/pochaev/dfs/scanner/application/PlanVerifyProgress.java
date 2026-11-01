package us.pochaev.dfs.scanner.application;

/**
 * A plan verify job's progress, the {@code P} of its {@link Job} (DFS-042 decision 3). Together with the job's
 * status and failure it makes the {@code PlanVerifySnapshot} the use cases return.
 *
 * @param planId       the verified plan's id
 * @param entriesDone  the last {@code EntryChecked.done}; 0 until the first one
 * @param entriesTotal the plan's operation count
 * @param bytesDone    the last {@code BytesCompared.bytesDone}; 0 until the first one
 * @param bytesTotal   the sum of the plan's operation sizes
 */
public record PlanVerifyProgress(String planId, int entriesDone, int entriesTotal, long bytesDone, long bytesTotal) {

    public PlanVerifyProgress {
    }
}
