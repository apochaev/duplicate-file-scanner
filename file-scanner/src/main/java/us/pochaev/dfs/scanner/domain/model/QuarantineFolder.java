package us.pochaev.dfs.scanner.domain.model;

/**
 * The name of the app's quarantine folder, shared by the deleter that creates it and the scan that leaves it out
 * (DFS-052 decision 4). A plan execution moves files into {@code <volume root or anchor>/.dfs-quarantine/<run ID>/...}
 * (DFS-051 decision 5); a scan never enters a directory with this exact, case-sensitive name and refuses a location
 * with it among its path's names (DFS-052 decisions 1 to 3).
 */
public final class QuarantineFolder {

    /** The quarantine folder's name, matched exactly and case-sensitively. */
    public static final String NAME = ".dfs-quarantine";

    private QuarantineFolder() {
    }
}
