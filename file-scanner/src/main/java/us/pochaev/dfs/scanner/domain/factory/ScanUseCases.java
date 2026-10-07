package us.pochaev.dfs.scanner.domain.factory;

import us.pochaev.dfs.scanner.domain.usecases.command.CancelScan;
import us.pochaev.dfs.scanner.domain.usecases.command.StartScan;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanResult;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanStatus;

/**
 * The scan use cases of one build of {@link ScannerFactories#scanUseCases}, all backed by the same
 * scan state: a scan ID returned by {@link #start} is known to {@link #status}, {@link #result} and
 * {@link #cancel} of the same instance, and to no other instance (DFS-019 decision 2).
 *
 * @param start  starts scans on the executor the set was built with
 * @param cancel requests that a scan of this set stop
 * @param status reads a scan's current snapshot
 * @param result reads a completed scan's result
 */
public record ScanUseCases(StartScan start, CancelScan cancel, GetScanStatus status, GetScanResult result) {

    /**
     * @throws NullPointerException if any component is null
     */
    public ScanUseCases {
        // DFS-019: the implementer adds a non-null check for each component (design.md, Public API).
    }
}
