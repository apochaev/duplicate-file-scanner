package us.pochaev.dfs.scanner.infra.adapter;

import us.pochaev.dfs.scanner.infra.port.ExecutionLock;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private static final Object MONITOR = new Object();
    private static final Map<Path, Holder> HELD = new HashMap<>();

    private static final class Holder {
        final FileChannelExecutionLock owner;
        final FileChannel channel;
        final FileLock lock;
        int shares;

        Holder(FileChannelExecutionLock owner, FileChannel channel, FileLock lock) {
            this.owner = owner;
            this.channel = channel;
            this.lock = lock;
        }
    }

    @Override
    public Acquisition acquire(Path manifestFolder) throws IOException {
        Objects.requireNonNull(manifestFolder, "manifestFolder");
        Files.createDirectories(manifestFolder);
        Path key = manifestFolder.toRealPath();
        synchronized (MONITOR) {
            Holder held = HELD.get(key);
            if (held != null) {
                if (held.owner != this) {
                    return new Acquisition.HeldElsewhere();
                }
                held.shares++;
                return new Acquisition.Acquired(lease(key, held));
            }
            FileChannel channel = FileChannel.open(key.resolve(FILE_NAME),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            try {
                FileLock lock;
                try {
                    lock = channel.tryLock();
                } catch (OverlappingFileLockException e) {
                    lock = null;
                }
                if (lock == null) {
                    channel.close();
                    return new Acquisition.HeldElsewhere();
                }
                Holder holder = new Holder(this, channel, lock);
                holder.shares = 1;
                HELD.put(key, holder);
                return new Acquisition.Acquired(lease(key, holder));
            } catch (IOException | RuntimeException e) {
                try {
                    channel.close();
                } catch (IOException suppressed) {
                    e.addSuppressed(suppressed);
                }
                throw e;
            }
        }
    }

    private static Lease lease(Path key, Holder holder) {
        AtomicBoolean released = new AtomicBoolean();
        return () -> {
            if (!released.compareAndSet(false, true)) {
                return;
            }
            synchronized (MONITOR) {
                if (--holder.shares > 0) {
                    return;
                }
                HELD.remove(key, holder);
                try {
                    holder.lock.release();
                } catch (IOException | RuntimeException e) {
                    // closing the channel releases it anyway
                }
                try {
                    holder.channel.close();
                } catch (IOException | RuntimeException e) {
                    // nothing more to do
                }
            }
        };
    }
}
