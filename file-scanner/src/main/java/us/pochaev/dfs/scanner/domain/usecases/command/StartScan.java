package us.pochaev.dfs.scanner.domain.usecases.command;

import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.domain.model.ScanListener;

import java.nio.file.Path;
import java.util.List;

/**
 * Starts a scan on the caller-supplied executor and returns its ID at once (DFS-018).
 * <p>
 * On the caller's thread, before anything is recorded or submitted: an empty {@code locations}
 * is {@code Failure(NO_LOCATIONS)}; each location is resolved in order, and the first that does
 * not exist, is not a directory or cannot be resolved is {@code Failure(LOCATION_NOT_FOUND)},
 * {@code Failure(NOT_A_DIRECTORY)} or {@code Failure(LOCATION_UNREADABLE)}. A rejected start
 * leaves no record and no ID. Otherwise the scan is recorded QUEUED and its task submitted; if
 * the executor rejects it, the record becomes FAILED and the result is
 * {@code Failure(EXECUTOR_REJECTED)}.
 */
public interface StartScan {

    /**
     * Starts a scan without a listener; progress is available through {@code GetScanStatus}.
     *
     * @throws NullPointerException if any argument is null or {@code locations} contains null
     */
    Result<ScanId> start(List<Path> locations, ScanConfig config);

    /**
     * Starts a scan whose events also go to {@code listener}, called synchronously on the
     * executor's thread that runs the scan, never on the caller's. A listener that throws is
     * detached on its first exception and the scan carries on; the snapshot's
     * {@code listenerFailure} records it.
     *
     * @throws NullPointerException if any argument is null or {@code locations} contains null
     */
    Result<ScanId> start(List<Path> locations, ScanConfig config, ScanListener listener);
}
