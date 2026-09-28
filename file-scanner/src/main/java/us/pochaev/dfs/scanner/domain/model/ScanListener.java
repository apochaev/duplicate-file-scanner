package us.pochaev.dfs.scanner.domain.model;

/**
 * Receives a scan's progress (DFS-005). Supplied by the caller; the core never prints, every
 * progress report goes through here. Called on the scanning thread, synchronously.
 */
@FunctionalInterface
public interface ScanListener {

    void onEvent(ScanEvent event);
}
