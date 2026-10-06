package us.pochaev.dfs.scanner.domain.model;

/**
 * Why a use case returned {@link Result.Failure} (DFS-018 decision 4). Later use cases add codes.
 */
public enum ErrorCode {
    /** The {@link ScanId} was never returned by {@code StartScan} in this process. */
    UNKNOWN_SCAN,
    /** {@code StartScan} was given an empty location list. */
    NO_LOCATIONS,
    /** A start location does not exist ({@link java.nio.file.NoSuchFileException}). */
    LOCATION_NOT_FOUND,
    /** A start location is not a directory ({@link java.nio.file.NotDirectoryException}). */
    NOT_A_DIRECTORY,
    /** A start location could not be resolved for another I/O reason (for example access denied). */
    LOCATION_UNREADABLE,
    /** The caller-supplied executor rejected the scan's task. */
    EXECUTOR_REJECTED,
    /** {@code GetScanResult} on a scan whose status is not COMPLETED; the message names the status. */
    SCAN_NOT_COMPLETED
}
