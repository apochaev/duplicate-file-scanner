package us.pochaev.dfs.scanner.domain.usecases.query;

import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.domain.model.ScanSnapshot;

/** Reads a scan's current snapshot (DFS-018 decision 2): the pull side of progress. */
public interface GetScanStatus {

    /**
     * @return {@code Success} holding the current snapshot (also for a FAILED scan), or
     *         {@code Failure(UNKNOWN_SCAN)}
     * @throws NullPointerException if {@code scanId} is null
     */
    Result<ScanSnapshot> status(ScanId scanId);
}
