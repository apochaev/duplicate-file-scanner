package us.pochaev.dfs.scanner.infra.adapter;

import us.pochaev.dfs.scanner.infra.port.FileIdentity;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/**
 * {@link FileIdentity} on the platform's own file identity (DFS-026). On Windows, for a path of the
 * default file system, the key comes from {@link WindowsFileId} (volume serial number and 128-bit
 * file ID, through the FFM API), because {@code BasicFileAttributes.fileKey()} is null there.
 * Everywhere else the key is {@code fileKey()} itself (device and inode on Linux and macOS), read
 * from the attributes without opening the file. Stateless: safe to share across threads and
 * concurrent scans.
 */
public final class NioFileIdentity implements FileIdentity {

    /** Decided once: {@code fileKey()} is always null on Windows (verified), so it is not tried. */
    private static final boolean WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException on Windows, if the JVM refuses the native call (native access
     *                               not enabled); the message names {@code --enable-native-access}
     *                               (DFS-026.AC-8)
     */
    @Override
    public Object key(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (WINDOWS && path.getFileSystem() == FileSystems.getDefault()) {
            return WindowsFileId.key(path);
        }
        Object fileKey = Files.readAttributes(path, BasicFileAttributes.class).fileKey();
        if (fileKey == null) {
            // Never fall back to the path: that would count hard links as duplicates.
            throw new IOException("no file key: " + path);
        }
        return fileKey;
    }
}
