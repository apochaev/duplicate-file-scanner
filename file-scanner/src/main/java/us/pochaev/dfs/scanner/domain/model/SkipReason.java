package us.pochaev.dfs.scanner.domain.model;

/**
 * Why a path was left out of a scan.
 */
public enum SkipReason {
    /** A symbolic link; links are never followed. */
    SYMLINK,
    /** Not a regular file or plain directory: junctions and other reparse points, devices, FIFOs, sockets. */
    SPECIAL_FILE,
    /** The file or directory could not be read (permissions, I/O error, removed mid-scan). */
    UNREADABLE,
    /**
     * A plain directory named exactly {@link QuarantineFolder#NAME}, the app's quarantine folder: never entered, so
     * quarantined files are never reported as copies of their keepers (DFS-052 decision 1). Always on.
     */
    QUARANTINE
}
