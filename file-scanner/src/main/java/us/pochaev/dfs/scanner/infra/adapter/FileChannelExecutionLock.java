package us.pochaev.dfs.scanner.infra.adapter;

import us.pochaev.dfs.scanner.infra.port.ExecutionLock;

import java.io.IOException;
import java.nio.file.Path;

/**
 * {@link ExecutionLock} on {@code FileChannel.tryLock} (DFS-055 decision 3): an exclusive lock on the whole of
 * {@code <manifest folder>/execution.lock}, opened with {@code CREATE, WRITE} and never deleted.
 *
 * <p>An OS file lock belongs to the whole JVM, and a second {@code tryLock} on a region this JVM already holds throws
 * {@code OverlappingFileLockException}. So the held locks are kept in one process-wide registry keyed by the manifest
 * folder's real path (after it is created), each entry holding the channel, the lock, the owning instance of this class
 * and its share count. {@code acquire} by the owner adds a share; by any other instance it answers
 * {@code HeldElsewhere}; with no entry it calls {@code tryLock}: {@code null} (another process) is
 * {@code HeldElsewhere}, and so is an {@code OverlappingFileLockException} (other code in this JVM locked the file).
 * The last share's release releases the lock, closes the channel and removes the entry. The registry mirrors the JVM's
 * own lock table; it is the adapter's only state.
 */
public final class FileChannelExecutionLock implements ExecutionLock {

    /** The lock file's name inside the manifest folder. */
    public static final String FILE_NAME = "execution.lock";

    @Override
    public Acquisition acquire(Path manifestFolder) throws IOException {
        throw new UnsupportedOperationException("not implemented: DFS-055");
    }
}
