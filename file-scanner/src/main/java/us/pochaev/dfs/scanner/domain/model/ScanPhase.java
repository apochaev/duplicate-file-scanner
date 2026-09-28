package us.pochaev.dfs.scanner.domain.model;

/** The phases of a scan, in the order they run (DFS-005, CLAUDE.md design decision 3). */
public enum ScanPhase {
    /** Walking the start locations and grouping files by size. */
    WALK,
    /** The head-and-tail sample hash. */
    PARTIAL_HASH,
    /** The full SHA-256 hash. */
    FULL_HASH,
    /** Grouping hard links to one file into one physical copy. */
    HARD_LINKS
}
