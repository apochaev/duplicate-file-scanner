package us.pochaev.dfs.scanner.infra.adapter;

import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.infra.port.ScanRecord;
import us.pochaev.dfs.scanner.infra.port.ScanRepository;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * {@link ScanRepository} on a {@link java.util.concurrent.ConcurrentHashMap} (DFS-018 decision 8):
 * {@code create} is {@code putIfAbsent}, {@code update} is {@code computeIfPresent}, so each update
 * is applied atomically per ID. Keeps every record until the process exits.
 */
public final class InMemoryScanRepository implements ScanRepository {

    @Override
    public void create(ScanRecord record) {
        throw new UnsupportedOperationException("not implemented: DFS-018");
    }

    @Override
    public Optional<ScanRecord> find(ScanId id) {
        throw new UnsupportedOperationException("not implemented: DFS-018");
    }

    @Override
    public Optional<ScanRecord> update(ScanId id, UnaryOperator<ScanRecord> change) {
        throw new UnsupportedOperationException("not implemented: DFS-018");
    }
}
