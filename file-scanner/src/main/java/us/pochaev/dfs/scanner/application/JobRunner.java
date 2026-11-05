package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.ErrorCode;
import us.pochaev.dfs.scanner.domain.model.JobStatus;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.infra.port.JobStore;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
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

    /**
     * Told the job's terminal record once its task has recorded it, so a use case can act on the status that was
     * actually recorded rather than on its work's own view (DFS-051: the manifest's {@code end} line).
     */
    @FunctionalInterface
    interface Settled<P, T> {

        /**
         * Runs on the executor's thread, after the task's last {@link JobStore#update} has recorded COMPLETED,
         * CANCELLED or FAILED, and before the task returns (or rethrows an {@code Error}). Never called for a job whose
         * work did not run: cancelled while QUEUED, rejected by the executor, or found not QUEUED at pickup.
         *
         * @param job the job's terminal record, as stored
         */
        void settled(Job<P, T> job);
    }

    /** The two results only the use case knows how to build. */
    interface Outcomes<P, T> {

        /** The result of a job cancelled while QUEUED, whose work never ran. */
        T cancelledBeforeStart(P progress);

        /** {@code result} marked as cancelled, for a work that returned after a cancel was recorded. */
        T markCancelled(T result);

        /**
         * Told a job's terminal record exactly once per created job, once the job is COMPLETED, CANCELLED or FAILED
         * and its work is not running (DFS-055: the plan execution releases its lock lease here). Called on the thread
         * that ended the job: the task's, after {@link Settled}, when the work ran (also on the {@code Error} path
         * and when {@code Settled} throws); the cancelling caller's, when {@link #cancel} turned QUEUED into
         * CANCELLED; the starting caller's, when the executor rejected the task or threw, or {@link #abandon} gave the
         * job up, each turning QUEUED into FAILED. Never for a job a pickup found no longer QUEUED (whoever ended it
         * calls it). Must not throw. The default does nothing.
         *
         * @param job the job's terminal record, as stored
         */
        default void ended(Job<P, T> job) {
        }
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
        return submit(id, initial, work, job -> {
        });
    }

    private Result<I> submitInternal(I id, P initial, Work<P, T> work, Settled<P, T> settled) {
        store.create(id, new Job<>(JobStatus.QUEUED, initial, null, ""));
        try {
            executor.execute(() -> runTask(id, work, settled));
        } catch (RejectedExecutionException e) {
            String failure = e.getClass().getName() + ": " + e.getMessage();
            markFailed(id, failure, true);
            return new Result.Failure<>(ErrorCode.EXECUTOR_REJECTED, rejectionMessage(failure));
        } catch (RuntimeException e) {
            markFailed(id, e.getClass().getName() + ": " + e.getMessage(), true);
            throw e;
        }
        return new Result.Success<>(id);
    }

    /**
     * As {@link #submit(Object, Object, Work)}, and once the task has recorded the job's terminal status, calls
     * {@code settled} with the stored record (see {@link Settled}). The three-argument form becomes this one with a
     * no-op {@code settled}. An exception from {@code settled} leaves the recorded status as it is: a
     * {@code RuntimeException} propagates out of the task, and on the {@code Error} path it is added as suppressed to
     * the {@code Error}, which is still rethrown.
     *
     * @throws NullPointerException if {@code settled} is null
     */
    Result<I> submit(I id, P initial, Work<P, T> work, Settled<P, T> settled) {
        Objects.requireNonNull(settled, "settled");
        return submitInternal(id, initial, work, settled);
    }

    /**
     * Records {@code id} QUEUED with {@code initial} progress unless a stored job matches {@code blocks}, in one
     * {@link JobStore#createUnless} (DFS-055 decision 5). Submits nothing: the caller follows with {@link #dispatch},
     * or {@link #abandon} if it gives the job up first.
     *
     * @return empty when the job was recorded; otherwise the ID of a stored job that matches {@code blocks}, with
     *         nothing recorded and no hook called
     * @throws NullPointerException if an argument is null
     */
    Optional<I> claim(I id, P initial, Predicate<? super Job<P, T>> blocks) {
        throw new UnsupportedOperationException("not implemented: DFS-055");
    }

    /**
     * Gives up a claimed job before {@link #dispatch}: QUEUED becomes FAILED with {@code failure}, and if this call
     * made that change, {@link Outcomes#ended} is called with the stored record. Any other status is left as it is.
     *
     * @throws NullPointerException if an argument is null
     */
    void abandon(I id, String failure) {
        throw new UnsupportedOperationException("not implemented: DFS-055");
    }

    /**
     * Submits the task of a job {@link #claim}ed QUEUED, exactly as {@link #submit(Object, Object, Work, Settled)}
     * does after its create: {@code Success(id)}; {@code Failure(EXECUTOR_REJECTED)} on
     * {@code RejectedExecutionException}, the job FAILED; any other {@code RuntimeException} rethrown, the job FAILED.
     * When either failure turns QUEUED into FAILED, {@link Outcomes#ended} is called before returning or rethrowing.
     *
     * @throws NullPointerException if an argument is null
     */
    Result<I> dispatch(I id, Work<P, T> work, Settled<P, T> settled) {
        throw new UnsupportedOperationException("not implemented: DFS-055");
    }

    /**
     * The {@code EXECUTOR_REJECTED} message, naming no particular use case (DFS-051 decision 7c, DFS-042.L-3):
     * {@code "executor rejected the task: " + failure}.
     *
     * @param failure {@code <exception class name>: <message>} of the rejection
     */
    static String rejectionMessage(String failure) {
        return "executor rejected the task: " + failure;
    }

    private void markFailed(I id, String failure, boolean queuedToo) {
        store.update(id, job -> {
            boolean live = job.status() == JobStatus.RUNNING || job.status() == JobStatus.CANCELLING
                    || (queuedToo && job.status() == JobStatus.QUEUED);
            return live ? new Job<>(JobStatus.FAILED, job.progress(), null, failure) : job;
        });
    }

    private void runTask(I id, Work<P, T> work, Settled<P, T> settled) {
        Optional<Job<P, T>> started = store.update(id, job -> job.status() == JobStatus.QUEUED
                ? new Job<>(JobStatus.RUNNING, job.progress(), null, "") : job);
        if (started.isEmpty() || started.get().status() != JobStatus.RUNNING) {
            return;
        }
        Progress<P> progress = change -> store.update(id, job ->
                job.status() == JobStatus.RUNNING || job.status() == JobStatus.CANCELLING
                        ? new Job<>(job.status(), change.apply(job.progress()), job.result(), job.failure())
                        : job);
        BooleanSupplier stop = () -> store.find(id).map(j -> j.status() != JobStatus.RUNNING).orElse(true);
        T t;
        try {
            t = work.run(progress, stop);
        } catch (RuntimeException e) {
            markFailed(id, e.getClass().getName() + ": " + e.getMessage(), false);
            settle(id, settled, null);
            return;
        } catch (Error e) {
            markFailed(id, e.getClass().getName() + ": " + e.getMessage(), false);
            settle(id, settled, e);
            throw e;
        }
        store.update(id, job -> switch (job.status()) {
            case RUNNING -> new Job<>(JobStatus.COMPLETED, job.progress(), t, "");
            case CANCELLING -> new Job<>(JobStatus.CANCELLED, job.progress(), outcomes.markCancelled(t), "");
            default -> job;
        });
        settle(id, settled, null);
    }

    /** Calls the hook with the stored record; a hook exception is suppressed into {@code error} when there is one. */
    private void settle(I id, Settled<P, T> settled, Error error) {
        Optional<Job<P, T>> stored = store.find(id);
        if (stored.isEmpty()) {
            return;
        }
        try {
            settled.settled(stored.get());
        } catch (RuntimeException e) {
            if (error == null) {
                throw e;
            }
            error.addSuppressed(e);
        }
    }

    /**
     * QUEUED becomes CANCELLED (result {@link Outcomes#cancelledBeforeStart}), RUNNING becomes CANCELLING; any other
     * status is left as it is. One atomic update.
     *
     * @return the job after the request, or empty for an unknown ID
     */
    Optional<Job<P, T>> cancel(I id) {
        Objects.requireNonNull(id, "id");
        return store.update(id, job -> switch (job.status()) {
            case QUEUED -> new Job<>(JobStatus.CANCELLED, job.progress(),
                    outcomes.cancelledBeforeStart(job.progress()), "");
            case RUNNING -> new Job<>(JobStatus.CANCELLING, job.progress(), null, "");
            default -> job;
        });
    }

    /**
     * @return the job's current record, or empty for an unknown ID
     */
    Optional<Job<P, T>> find(I id) {
        Objects.requireNonNull(id, "id");
        return store.find(id);
    }
}
