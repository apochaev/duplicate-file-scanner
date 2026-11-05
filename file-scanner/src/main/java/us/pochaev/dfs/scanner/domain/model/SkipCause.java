package us.pochaev.dfs.scanner.domain.model;

/**
 * Why an execution skipped one path (DFS-051 decision 4), in precedence order for one path: the candidate's drift,
 * then the keeper's drift (both read without following links, the rule of DFS-033), then the operation's one content
 * compare (DFS-034), then the move itself. The drift values mirror {@link DriftKind}, the content ones
 * {@link OperationCheck.Outcome}. The manifest's {@code skipped} line records the constant's name.
 */
public enum SkipCause {
    /** The subject (candidate or keeper) no longer exists. */
    MISSING,
    /** The subject is now a symlink, junction, directory or special file; it was neither read nor moved. */
    NOT_A_REGULAR_FILE,
    /** The subject's attributes, or the content compare, could not be read. */
    UNREADABLE,
    /** The subject's size differs from the scan's. */
    SIZE_CHANGED,
    /** The subject's size is the scan's but its last-modified time differs. */
    MODIFIED_CHANGED,
    /** The candidate's content differs from its keeper's. */
    DIFFERS,
    /** The quarantine folder for the candidate could not be named or created (read-only volume, access denied). */
    QUARANTINE_NOT_WRITABLE,
    /** The rename itself failed (locked, access denied, a cross-volume move, the quarantine path already taken). */
    MOVE_FAILED
}
