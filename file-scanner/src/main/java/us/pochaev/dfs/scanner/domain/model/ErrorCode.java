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
    SCAN_NOT_COMPLETED,
    /** A deletion rule's {@code quarantineIn} equals its {@code keep} or lies under it; the message names the rule index. */
    INVALID_RULE,
    /** A pick's digest is not a set of the scan; the message names the digest. */
    UNKNOWN_SET,
    /** A pick's {@code keep} is not a link of the set it names; the message names the digest. */
    PATH_NOT_IN_SET,
    /** A second pick for a digest already picked; the message names the digest. */
    DUPLICATE_PICK
}
