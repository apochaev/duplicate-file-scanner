package us.pochaev.dfs.scanner.infra.adapter;

import us.pochaev.dfs.scanner.infra.port.JobStore;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * {@link JobStore} on a {@link ConcurrentHashMap} (DFS-042 decision 4): {@code create} is {@code putIfAbsent},
 * {@code update} is {@code computeIfPresent}, so each update is applied atomically per ID. Keeps every record until
 * the process exits.
 *
 * @param <I> the job ID type; must have value equality
 * @param <R> the record type
 */
public final class InMemoryJobStore<I, R> implements JobStore<I, R> {

    private final ConcurrentHashMap<I, R> records = new ConcurrentHashMap<>();

    @Override
    public void create(I id, R record) {
        throw new UnsupportedOperationException("not implemented: DFS-042");
    }

    @Override
    public Optional<R> find(I id) {
        throw new UnsupportedOperationException("not implemented: DFS-042");
    }

    @Override
    public Optional<R> update(I id, UnaryOperator<R> change) {
        throw new UnsupportedOperationException("not implemented: DFS-042");
    }
}
