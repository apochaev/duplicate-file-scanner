package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.JobStatus;

/**
 * One long-running job's whole state, as one immutable value behind a {@code JobStore} (DFS-042 decision 4). Changed
 * only by replacing it inside {@code JobStore.update}, through {@link JobRunner}. The cancel request is the status
 * itself (CANCELLING), never a separate flag. Not exported: callers see a snapshot built from it.
 *
 * @param status   the lifecycle status, owned by {@link JobRunner}
 * @param progress the use case's own progress value (for a plan verify, {@link PlanVerifyProgress}), changed only
 *                 while RUNNING or CANCELLING
 * @param result   the work's result once COMPLETED or CANCELLED; null in every other status
 * @param failure  for a FAILED job, {@code <exception class name>: <message>}; empty otherwise
 * @param <P>      the progress type
 * @param <T>      the result type
 */
public record Job<P, T>(JobStatus status, P progress, T result, String failure) {

    public Job {
    }
}
