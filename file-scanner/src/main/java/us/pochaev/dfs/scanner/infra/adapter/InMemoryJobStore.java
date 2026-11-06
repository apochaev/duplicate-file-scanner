package us.pochaev.dfs.scanner.infra.adapter;

import us.pochaev.dfs.scanner.infra.port.JobStore;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
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
    public synchronized void create(I id, R record) {
        java.util.Objects.requireNonNull(id, "id");
        java.util.Objects.requireNonNull(record, "record");
        if (records.putIfAbsent(id, record) != null) {
            throw new IllegalStateException("job already exists: " + id);
        }
    }

    @Override
    public synchronized Optional<I> createUnless(I id, R record, Predicate<? super R> blocking) {
        java.util.Objects.requireNonNull(id, "id");
        java.util.Objects.requireNonNull(record, "record");
        java.util.Objects.requireNonNull(blocking, "blocking");
        for (java.util.Map.Entry<I, R> e : records.entrySet()) {
            if (blocking.test(e.getValue())) {
                return Optional.of(e.getKey());
            }
        }
        if (records.putIfAbsent(id, record) != null) {
            throw new IllegalStateException("job already exists: " + id);
        }
        return Optional.empty();
    }

    @Override
    public Optional<R> find(I id) {
        java.util.Objects.requireNonNull(id, "id");
        return Optional.ofNullable(records.get(id));
    }

    @Override
    public Optional<R> update(I id, UnaryOperator<R> change) {
        java.util.Objects.requireNonNull(id, "id");
        java.util.Objects.requireNonNull(change, "change");
        return Optional.ofNullable(records.computeIfPresent(id, (k, current) ->
                java.util.Objects.requireNonNull(change.apply(current), "change returned null")));
    }
}
