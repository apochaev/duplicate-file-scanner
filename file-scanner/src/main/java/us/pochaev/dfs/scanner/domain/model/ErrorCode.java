package us.pochaev.dfs.scanner.domain.model;

/**
 * Why a use case returned {@link Result.Failure} (DFS-018 decision 4). Later use cases add codes.
 */
public enum ErrorCode {
    /** The {@link ScanId} was never returned by {@code StartScan} in this process. */
    UNKNOWN_SCAN,
    /** {@code StartScan} was given an empty location list. */
    NO_LOCATIONS,
    /**
     * A start location, or a folder given to {@code ListDirectory}, does not exist
     * ({@link java.nio.file.NoSuchFileException}).
     */
    LOCATION_NOT_FOUND,
    /**
     * A start location, or a folder given to {@code ListDirectory}, is not a directory
     * ({@link java.nio.file.NotDirectoryException}).
     */
    NOT_A_DIRECTORY,
    /**
     * A start location could not be resolved, or a folder given to {@code ListDirectory} could not be opened or read to
     * the end, for another I/O reason (for example access denied).
     */
    LOCATION_UNREADABLE,
    /**
     * A folder given to {@code ListDirectory} is a symlink, junction or other reparse point, which the listing never
     * follows (DFS-032 decision 4). A scan's start location is resolved instead.
     */
    LOCATION_IS_LINK,
    /** The caller-supplied executor rejected the scan's or the plan verify's task (DFS-018, DFS-042 decision 5). */
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
    DUPLICATE_PICK,
    /**
     * {@code CheckPlanDrift} was given a plan whose operation digest is not a set of the scan, or whose link or keeper
     * is not a path of that set's copies (DFS-033 decision 4); the message names the first such digest or path.
     */
    PLAN_NOT_FROM_SCAN,
    /**
     * The {@link PlanVerifyId} was never returned by {@code StartPlanVerify} of the same use-case set in this process
     * (DFS-042 decision 5); the message names the ID.
     */
    UNKNOWN_PLAN_VERIFY,
    /**
     * {@code GetPlanVerifyResult} on a job whose status is QUEUED, RUNNING, CANCELLING or FAILED (DFS-042 decision 3);
     * the message names the status.
     */
    PLAN_VERIFY_NOT_FINISHED
}
