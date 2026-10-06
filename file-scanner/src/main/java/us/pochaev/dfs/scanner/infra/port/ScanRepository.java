package us.pochaev.dfs.scanner.infra.port;

import us.pochaev.dfs.scanner.domain.model.ScanId;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Holds every scan's {@link ScanRecord} (DFS-018 decision 8). Thread-safe: called from callers'
 * threads and from executor threads at once. Records are kept until the process exits (decision 9);
 * nothing removes one.
 */
public interface ScanRepository {

    /**
     * Stores a new record.
     *
     * @throws NullPointerException  if {@code record} is null
     * @throws IllegalStateException if a record with the same ID already exists
     */
    void create(ScanRecord record);

    /**
     * @return the current record of {@code id}, or empty if none was created
     * @throws NullPointerException if {@code id} is null
     */
    Optional<ScanRecord> find(ScanId id);

    /**
     * Replaces the record of {@code id} with {@code change} applied to it, atomically with respect to
     * every other {@code update} of the same ID: no update is lost and {@code change} sees the
     * latest record. {@code change} is applied exactly once per call on an existing record, must be
     * free of side effects on this repository, and must return a non-null record with the same ID.
     *
     * @return the record after the change, or empty (and {@code change} not applied) if no record
     *         of {@code id} exists
     * @throws NullPointerException if an argument is null, or {@code change} returns null
     */
    Optional<ScanRecord> update(ScanId id, UnaryOperator<ScanRecord> change);
}
