package us.pochaev.dfs.scanner.infra.adapter;

import us.pochaev.dfs.scanner.infra.port.FileIdentity;

import java.io.IOException;
import java.nio.file.Path;

/**
 * {@link FileIdentity} on {@link java.nio.file.Files#isSameFile}, which on Windows compares the
 * volume serial number and file index, so it recognises NTFS hard links although
 * {@code fileKey()} is null there. Stateless: safe to share across threads and concurrent scans.
 */
public final class NioFileIdentity implements FileIdentity {

    @Override
    public boolean isSameFile(Path a, Path b) throws IOException {
        throw new UnsupportedOperationException("not implemented: DFS-004");
        // implementer: return Files.isSameFile(a, b) after Objects.requireNonNull on both
        // arguments (real body only, not before this throw: L-013).
    }
}
