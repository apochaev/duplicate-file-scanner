package us.pochaev.dfs.scanner.infra.port;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Holds every long-running job's one immutable record (DFS-042 decision 4), generic so each kind of job (a plan
 * verify now, a plan execution later) stores its own record type through it. Thread-safe: called from callers'
 * threads and from executor threads at once. Records are kept until the process exits; nothing removes one.
 *
 * @param <I> the job ID type; must have value equality
 * @param <R> the record type; the store never looks inside it
 */
public interface JobStore<I, R> {

    /**
     * Stores the first record of a new job.
     *
     * @throws NullPointerException  if an argument is null
     * @throws IllegalStateException if a record of {@code id} already exists
     */
    void create(I id, R record);

    /**
     * @return the current record of {@code id}, or empty if none was created
     * @throws NullPointerException if {@code id} is null
     */
    Optional<R> find(I id);

    /**
     * Replaces the record of {@code id} with {@code change} applied to it, atomically with respect to every other
     * {@code update} of the same ID: no update is lost and {@code change} sees the latest record. {@code change} is
     * applied exactly once per call on an existing record, must be free of side effects on this store, and must return
     * a non-null record.
     *
     * @return the record after the change, or empty (and {@code change} not applied) if no record of {@code id} exists
     * @throws NullPointerException if an argument is null, or {@code change} returns null
     */
    Optional<R> update(I id, UnaryOperator<R> change);
}
