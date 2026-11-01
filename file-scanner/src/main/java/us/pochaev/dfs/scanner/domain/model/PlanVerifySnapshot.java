package us.pochaev.dfs.scanner.domain.model;

/**
 * A plan verify job's state at one moment, as {@code GetPlanVerifyStatus} and {@code CancelPlanVerify} return it
 * (DFS-042 decision 3). Kept current from the same {@link VerifyEvent}s the caller's {@link VerifyListener} receives.
 * Counts only: live per-entry marks come from the listener.
 *
 * @param status       the job's lifecycle status
 * @param planId       the verified plan's {@link DeletionPlan#id()}
 * @param entriesDone  the last {@link VerifyEvent.EntryChecked#done()}; 0 until the first one
 * @param entriesTotal the plan's operation count, set when the job is recorded
 * @param bytesDone    the last {@link VerifyEvent.BytesCompared#bytesDone()}; 0 until the first one
 * @param bytesTotal   the sum of the plan's operation sizes, set when the job is recorded
 * @param failure      for a FAILED job, {@code <exception class name>: <message>} of what failed it; empty otherwise
 */
public record PlanVerifySnapshot(JobStatus status, String planId, int entriesDone, int entriesTotal, long bytesDone,
                                 long bytesTotal, String failure) {

    public PlanVerifySnapshot {
    }
}
