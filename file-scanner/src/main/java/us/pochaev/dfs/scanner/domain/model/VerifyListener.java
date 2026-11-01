package us.pochaev.dfs.scanner.domain.model;

/**
 * Receives a plan content verify's progress (DFS-034 decision 5). Supplied by the caller and called on the caller's
 * thread, synchronously; detached at its first exception.
 */
@FunctionalInterface
public interface VerifyListener {

    void onEvent(VerifyEvent event);
}
