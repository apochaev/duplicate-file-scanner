package us.pochaev.dfs.scanner.infra.adapter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.pochaev.dfs.scanner.infra.port.ExecutionLock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** The {@code FileChannel} execution lock (DFS-055 decision 3) over real files, with a child JVM as the other process. */
class FileChannelExecutionLockTest {

    @TempDir
    Path tmp;

    private final List<ExecutionLock.Lease> leases = new ArrayList<>();

    @AfterEach
    void releaseEverything() {
        leases.forEach(ExecutionLock.Lease::release);
    }

    private ExecutionLock.Lease acquired(ExecutionLock lock, Path folder) throws IOException {
        ExecutionLock.Acquisition result = lock.acquire(folder);
        ExecutionLock.Lease lease = assertInstanceOf(ExecutionLock.Acquisition.Acquired.class, result).lease();
        leases.add(lease);
        return lease;
    }

    private Path lockFile(Path folder) {
        return folder.resolve(FileChannelExecutionLock.FILE_NAME);
    }

    @Test
    @DisplayName("DFS-055.AC-3 Acquiring creates the manifest folder and the lock file")
    void acquireCreatesFolderAndFile() throws Exception {
        Path folder = tmp.resolve("a/b/manifests");

        acquired(new FileChannelExecutionLock(), folder);

        assertTrue(Files.isDirectory(folder));
        assertTrue(Files.isRegularFile(lockFile(folder)));
        assertEquals("execution.lock", FileChannelExecutionLock.FILE_NAME);
    }

    @Test
    @DisplayName("DFS-055.AC-3 One instance can take several shares; another instance is refused as held elsewhere")
    void sharesBelongToOneInstance() throws Exception {
        Path folder = tmp.resolve("m");
        FileChannelExecutionLock owner = new FileChannelExecutionLock();

        acquired(owner, folder);
        acquired(owner, folder);

        assertInstanceOf(ExecutionLock.Acquisition.HeldElsewhere.class,
                new FileChannelExecutionLock().acquire(folder));
    }

    @Test
    @DisplayName("DFS-055.AC-3 The lock is held while any share is, and free once the last is released")
    void lockIsHeldUntilTheLastShareIsReleased() throws Exception {
        Path folder = tmp.resolve("m");
        FileChannelExecutionLock owner = new FileChannelExecutionLock();
        ExecutionLock.Lease first = acquired(owner, folder);
        ExecutionLock.Lease second = acquired(owner, folder);

        assertEquals("HELD", ExecutionLockProbe.tryLock(lockFile(folder)));
        first.release();
        assertEquals("HELD", ExecutionLockProbe.tryLock(lockFile(folder)));
        second.release();
        assertEquals("FREE", ExecutionLockProbe.tryLock(lockFile(folder)));
        acquired(new FileChannelExecutionLock(), folder);
    }

    @Test
    @DisplayName("DFS-055.AC-3 Releasing a lease twice gives back only one share")
    void releaseIsIdempotent() throws Exception {
        Path folder = tmp.resolve("m");
        FileChannelExecutionLock owner = new FileChannelExecutionLock();
        ExecutionLock.Lease first = acquired(owner, folder);
        ExecutionLock.Lease second = acquired(owner, folder);

        first.release();
        first.release();

        assertEquals("HELD", ExecutionLockProbe.tryLock(lockFile(folder)));
        second.release();
        assertEquals("FREE", ExecutionLockProbe.tryLock(lockFile(folder)));
    }

    @Test
    @DisplayName("DFS-055.AC-3 A manifest folder that is a file is an IOException")
    void folderThatIsAFileFails() throws Exception {
        Path notAFolder = tmp.resolve("file");
        Files.write(notAFolder, new byte[] {1});

        try {
            new FileChannelExecutionLock().acquire(notAFolder);
            fail("expected an IOException");
        } catch (IOException expected) {
            // expected
        }
    }

    @Test
    @DisplayName("DFS-055.AC-3 A lock held by another process is held elsewhere, and free again when that process ends")
    void anotherProcessHoldsTheLock() throws Exception {
        Path folder = tmp.resolve("m");
        Files.createDirectories(folder);
        FileChannelExecutionLock lock = new FileChannelExecutionLock();

        try (ExecutionLockProbe.Holder other = ExecutionLockProbe.hold(lockFile(folder))) {
            assertEquals("LOCKED", other.firstLine());

            assertInstanceOf(ExecutionLock.Acquisition.HeldElsewhere.class, lock.acquire(folder));
        }

        acquired(lock, folder);
    }
}
