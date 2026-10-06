package us.pochaev.dfs.scanner.domain.usecases.command;

import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.domain.model.ScanSnapshot;

/**
 * Requests that a scan stop (DFS-018 decision 3). A QUEUED scan becomes CANCELLED at once; a
 * RUNNING one becomes CANCELLING and turns CANCELLED once the pipeline has stopped. On a
 * CANCELLING or terminal scan it changes nothing.
 */
public interface CancelScan {

    /**
     * @return {@code Success} holding the snapshot after the request (CANCELLING, CANCELLED, or the
     *         state it already had), or {@code Failure(UNKNOWN_SCAN)}
     * @throws NullPointerException if {@code scanId} is null
     */
    Result<ScanSnapshot> cancel(ScanId scanId);
}
