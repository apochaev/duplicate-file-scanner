package us.pochaev.dfs.scanner.infra.port;

import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.model.ScanSnapshot;

/**
 * All per-scan state, as one immutable value behind {@link ScanRepository} (DFS-018 decision 8).
 * Changed only by replacing it inside {@link ScanRepository#update}. Not exported: callers see the
 * snapshot and the result through the use cases.
 *
 * @param id       the scan's ID; never null, never changed by an update
 * @param snapshot the scan's current snapshot; never null
 * @param result   the pipeline's result once the status is COMPLETED; null in every other status
 */
public record ScanRecord(ScanId id, ScanSnapshot snapshot, ScanResult result) {

    public ScanRecord {
        java.util.Objects.requireNonNull(id, "id");
        java.util.Objects.requireNonNull(snapshot, "snapshot");
    }
}
