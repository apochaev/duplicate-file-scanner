package us.pochaev.dfs.server.analysis;

import java.util.Objects;

/**
 * Two folders and the copies between them, both directions in one record (docs/design/folder-overlap-analysis.md,
 * decision 1). {@code a} sorts before {@code b} by path, so each pair appears once.
 *
 * @param a the side whose folder sorts first
 * @param b the side whose folder sorts second
 */
public record FolderPair(PairSide a, PairSide b) {

    /**
     * @throws IllegalArgumentException if {@code a}'s folder does not sort strictly before {@code b}'s
     */
    public FolderPair {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        if (a.folder().compareTo(b.folder()) >= 0) {
            throw new IllegalArgumentException("a must sort before b: " + a.folder() + ", " + b.folder());
        }
    }
}
