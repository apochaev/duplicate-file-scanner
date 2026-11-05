package us.pochaev.dfs.scanner.domain.model;

/**
 * Receives a plan execution's progress (DFS-051 decision 9). Called synchronously on the executor's thread that runs
 * the execution, never on the caller's; detached at its first exception, and the run carries on.
 */
@FunctionalInterface
public interface ExecutionListener {

    void onEvent(ExecutionEvent event);
}
