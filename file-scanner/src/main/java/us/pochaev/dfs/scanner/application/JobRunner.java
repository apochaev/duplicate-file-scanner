package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.infra.port.JobStore;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;

/**
 * The job machinery shared by every long-running use case except the scan (DFS-042 decision 1): records a job QUEUED,
 * submits it to the caller-supplied executor, runs its {@link Work} once the job is RUNNING, settles cancel requests,
 * executor rejection and failures, and records the outcome. Every transition is one {@link JobStore#update} that
 * checks the current status first, so two transitions that race are settled by whichever update lands first. A plan
 * verify uses it now, a plan execution later, each behind its own typed ports. Holds only its store, executor and
 * outcomes; no per-job state in fields.
 *
 * @param <I> the job ID type
 * @param <P> the progress type
 * @param <T> the result type
 */
final class JobRunner<I, P, T> {

    /** What a job does on the executor's thread once it is RUNNING. */
    @FunctionalInterface
    interface Work<P, T> {

        /**
         * @param progress changes the job's progress; ignored once the job is no longer RUNNING or CANCELLING
         * @param stop     true once the job is no longer RUNNING (a cancel was recorded); the work should then return
         *                 as soon as it can, with a result that says it stopped
         * @return the work's result, recorded as COMPLETED or CANCELLED by {@link JobRunner}
         */
        T run(Progress<P> progress, BooleanSupplier stop);
    }

    /** Applies a change to a running job's progress, as one atomic store update. */
    @FunctionalInterface
    interface Progress<P> {

        void update(UnaryOperator<P> change);
    }

    /** The two results only the use case knows how to build. */
    interface Outcomes<P, T> {

        /** The result of a job cancelled while QUEUED, whose work never ran. */
        T cancelledBeforeStart(P progress);

        /** {@code result} marked as cancelled, for a work that returned after a cancel was recorded. */
        T markCancelled(T result);
    }

    private final JobStore<I, Job<P, T>> store;
    private final Executor executor;
    private final Outcomes<P, T> outcomes;

    /**
     * @throws NullPointerException if any argument is null
     */
    JobRunner(JobStore<I, Job<P, T>> store, Executor executor, Outcomes<P, T> outcomes) {
        this.store = Objects.requireNonNull(store, "store");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.outcomes = Objects.requireNonNull(outcomes, "outcomes");
    }

    /**
     * Records {@code id} QUEUED with {@code initial} progress, then submits a task that runs {@code work}.
     *
     * @return {@code Success(id)}; or {@code Failure(EXECUTOR_REJECTED)} when the executor throws
     *         {@code RejectedExecutionException}, with the job FAILED and its failure set to the rejection
     * @throws RuntimeException any other exception from the executor, rethrown after marking the job FAILED
     */
    Result<I> submit(I id, P initial, Work<P, T> work) {
        throw new UnsupportedOperationException("not implemented: DFS-042");
    }

    /**
     * QUEUED becomes CANCELLED (result {@link Outcomes#cancelledBeforeStart}), RUNNING becomes CANCELLING; any other
     * status is left as it is. One atomic update.
     *
     * @return the job after the request, or empty for an unknown ID
     */
    Optional<Job<P, T>> cancel(I id) {
        throw new UnsupportedOperationException("not implemented: DFS-042");
    }

    /**
     * @return the job's current record, or empty for an unknown ID
     */
    Optional<Job<P, T>> find(I id) {
        throw new UnsupportedOperationException("not implemented: DFS-042");
    }
}
