package us.pochaev.dfs.scanner.infra.adapter;

import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.infra.port.ScanRecord;
import us.pochaev.dfs.scanner.infra.port.ScanRepository;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * {@link ScanRepository} on a {@link java.util.concurrent.ConcurrentHashMap} (DFS-018 decision 8):
 * {@code create} is {@code putIfAbsent}, {@code update} is {@code computeIfPresent}, so each update
 * is applied atomically per ID. Keeps every record until the process exits.
 */
public final class InMemoryScanRepository implements ScanRepository {

    private final ConcurrentHashMap<ScanId, ScanRecord> records = new ConcurrentHashMap<>();

    @Override
    public void create(ScanRecord record) {
        Objects.requireNonNull(record, "record");
        if (records.putIfAbsent(record.id(), record) != null) {
            throw new IllegalStateException("scan already exists: " + record.id());
        }
    }

    @Override
    public Optional<ScanRecord> find(ScanId id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(records.get(id));
    }

    @Override
    public Optional<ScanRecord> update(ScanId id, UnaryOperator<ScanRecord> change) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(change, "change");
        return Optional.ofNullable(records.computeIfPresent(id,
                (k, current) -> Objects.requireNonNull(change.apply(current), "change returned null")));
    }
}
