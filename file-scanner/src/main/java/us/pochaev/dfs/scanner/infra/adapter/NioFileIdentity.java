package us.pochaev.dfs.scanner.infra.adapter;

import us.pochaev.dfs.scanner.infra.port.FileIdentity;

import java.io.IOException;
import java.nio.file.Path;

/**
 * {@link FileIdentity} on the platform's own file identity (DFS-026). On Windows, for a path of the
 * default file system, the key comes from {@link WindowsFileId} (volume serial number and 128-bit
 * file ID, through the FFM API), because {@code BasicFileAttributes.fileKey()} is null there.
 * Everywhere else the key is {@code fileKey()} itself (device and inode on Linux and macOS), read
 * from the attributes without opening the file. Stateless: safe to share across threads and
 * concurrent scans.
 */
public final class NioFileIdentity implements FileIdentity {

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException on Windows, if the JVM refuses the native call (native access
     *                               not enabled); the message names {@code --enable-native-access}
     *                               (DFS-026.AC-8)
     */
    @Override
    public Object key(Path path) throws IOException {
        throw new UnsupportedOperationException("not implemented: DFS-026");
        // implementer: Objects.requireNonNull(path, "path") as the real body's first line (L-013).
        // Then: if Windows and path.getFileSystem() == FileSystems.getDefault() -> WindowsFileId.key(path);
        // else Files.readAttributes(path, BasicFileAttributes.class).fileKey(), and an IOException
        // naming the path if that is null (never fall back to the path itself: that would count
        // hard links as duplicates). See design.md, "Open design notes".
    }
}
