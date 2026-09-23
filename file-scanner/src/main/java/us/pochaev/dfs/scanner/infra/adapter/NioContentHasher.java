package us.pochaev.dfs.scanner.infra.adapter;

import us.pochaev.dfs.scanner.infra.port.ContentHasher;

import java.io.IOException;
import java.nio.file.Path;

/**
 * SHA-256 {@link ContentHasher} on NIO file channels. Stateless: a fresh
 * {@link java.security.MessageDigest} per call, so one instance is safe to share across threads
 * and concurrent scans.
 */
public final class NioContentHasher implements ContentHasher {

    /**
     * Reads the head and tail with positional reads on a {@link java.nio.channels.FileChannel},
     * using the channel's size at read time.
     */
    @Override
    public String sampleHash(Path file, int sampleSize) throws IOException {
        throw new UnsupportedOperationException("not implemented: DFS-002");
        // implementer: requireNonNull(file) and reject sampleSize <= 0 with
        // IllegalArgumentException as the real body's first lines (L-013: not in the stub).
    }

    /**
     * Not available yet: the full-hash stage is DFS-003, which implements this method.
     */
    @Override
    public String fullHash(Path file) throws IOException {
        throw new UnsupportedOperationException("not implemented: DFS-002");
        // implementer (DFS-002.AC-8): replace this with
        //     throw new UnsupportedOperationException("full hash is implemented by DFS-003");
        // The message must name DFS-003 and must NOT start with "not implemented:", so the
        // story gate's StubsCheck does not read the finished method as a leftover stub.
    }
}
