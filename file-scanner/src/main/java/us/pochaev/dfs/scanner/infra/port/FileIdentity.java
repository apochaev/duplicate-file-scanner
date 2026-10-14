package us.pochaev.dfs.scanner.infra.port;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Hands out an identity key per path, so the hard-link stage of the detection pipeline can group
 * the files of one duplicate set by physical file in one lookup per file (DFS-004, DFS-026;
 * CLAUDE.md: "hard links are not duplicates").
 * <p>
 * The key is opaque, with the {@code BasicFileAttributes.fileKey()} contract: two paths reach the
 * same physical file exactly when their keys are {@link Object#equals equal} (and then have equal
 * {@link Object#hashCode hash codes}). A pairwise check is {@code key(a).equals(key(b))}. Callers
 * only compare keys within one duplicate set, never across the disk. Reads metadata, not content.
 * Implementations must be safe to call from several threads at once.
 */
public interface FileIdentity {

    /**
     * The identity key of the physical file {@code path} reaches. Reading one path twice gives
     * equal keys.
     *
     * @param path the path to identify
     * @return a non-null key with value {@code equals}/{@code hashCode}
     * @throws NullPointerException if {@code path} is null
     * @throws IOException          if the file cannot be accessed to tell
     */
    Object key(Path path) throws IOException;
}
