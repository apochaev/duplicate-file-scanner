package us.pochaev.dfs.scanner.domain.usecases.query;

import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.domain.model.ScanResult;

/**
 * Reads a completed scan's whole result, unpaged, in the pipeline's own order (DFS-018 decision 5).
 */
public interface GetScanResult {

    /**
     * @return {@code Success} holding the pipeline's {@link ScanResult} for a COMPLETED scan;
     *         {@code Failure(SCAN_NOT_COMPLETED)}, whose message names the current status, for any
     *         other status; or {@code Failure(UNKNOWN_SCAN)}
     * @throws NullPointerException if {@code scanId} is null
     */
    Result<ScanResult> result(ScanId scanId);
}
