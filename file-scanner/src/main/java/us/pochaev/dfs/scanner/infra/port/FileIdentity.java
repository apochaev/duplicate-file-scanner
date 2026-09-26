package us.pochaev.dfs.scanner.infra.port;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Tells whether two paths reach the same physical file (hard links), for the hard-link stage of
 * the detection pipeline (DFS-004; CLAUDE.md: "hard links are not duplicates").
 * <p>
 * A pairwise check rather than a key per path: {@code BasicFileAttributes.fileKey()} is null on
 * Windows, so a plain-NIO adapter cannot produce a key. Callers only ask within one duplicate set,
 * never across the disk. Reads attributes, not content. Implementations must be safe to call from
 * several threads at once.
 */
public interface FileIdentity {

    /**
     * Whether {@code a} and {@code b} locate the same physical file. Two equal paths are the same
     * file.
     *
     * @param a one path
     * @param b the other path
     * @return true if both paths reach one physical file
     * @throws NullPointerException if {@code a} or {@code b} is null
     * @throws IOException          if either file cannot be accessed to tell
     */
    boolean isSameFile(Path a, Path b) throws IOException;
}
