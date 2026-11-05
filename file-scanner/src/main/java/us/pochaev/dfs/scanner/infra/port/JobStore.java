package us.pochaev.dfs.scanner.infra.port;

import java.util.Optional;
import java.util.function.Predicate;
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
     * Stores the first record of a new job unless a stored record matches {@code blocking} (DFS-055 decision 5): the
     * test of every stored record and the store are one step, atomic with respect to every other {@code create} and
     * {@code createUnless}, so of two calls that would each block the other, exactly one stores its record. A record
     * changed concurrently by {@link #update} is tested in its state either before or after that update.
     * {@code blocking} must be free of side effects on this store.
     *
     * @return empty when {@code record} was stored; otherwise the ID of a stored record that matches {@code blocking}
     *         (any one, if several do), and nothing stored
     * @throws NullPointerException  if an argument is null
     * @throws IllegalStateException if no record matches and a record of {@code id} already exists
     */
    Optional<I> createUnless(I id, R record, Predicate<? super R> blocking);

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
