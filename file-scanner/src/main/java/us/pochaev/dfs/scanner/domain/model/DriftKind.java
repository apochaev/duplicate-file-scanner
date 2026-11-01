package us.pochaev.dfs.scanner.domain.model;

/**
 * How a plan path drifted since the scan (DFS-033 decision 2). When several apply, the first one listed here is
 * reported.
 */
public enum DriftKind {
    /** The path no longer exists. */
    MISSING,
    /** The path is now a symlink, directory, junction or other special file; it was not followed. */
    NOT_A_REGULAR_FILE,
    /** The path's attributes could not be read; the {@link PathDrift#detail()} says why. */
    UNREADABLE,
    /** The file's size differs from the recorded one (whatever its last-modified time). */
    SIZE_CHANGED,
    /** The size is the recorded one but the last-modified time differs. */
    MODIFIED_CHANGED
}
