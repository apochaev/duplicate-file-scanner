package us.pochaev.dfs.scanner.infra.port;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The cross-process lock that lets at most one plan execution per manifest folder move files at a time (DFS-055
 * decision 3): an exclusive OS lock on {@code <manifest folder>/execution.lock}. A plan execution takes a share of it
 * before its job is recorded and releases that share once the job is terminal; the OS lock is held while any share is.
 *
 * <p>Shares exist so the in-process rule stays the job store's (decision 5): every start of one use-case set (one
 * instance of this port) gets past the lock, and the store's conditional create decides which one runs. Another
 * instance of this port, in this process or another, sees the lock as held elsewhere. Thread-safe.
 */
public interface ExecutionLock {

    /**
     * Takes one share of the lock on {@code <manifestFolder>/execution.lock} for this instance, creating the folder
     * (and its parents) and the file if absent. The file is never deleted; it is reused by every later execution.
     *
     * @param manifestFolder the absolute manifest folder ({@code ExecutionConfig.manifestFolder})
     * @return {@link Acquisition.Acquired} with a lease to release once the execution is terminal; or
     *         {@link Acquisition.HeldElsewhere} when another process, or another instance of this port in this
     *         process, holds the lock (nothing to release then)
     * @throws IOException          if the folder or the file cannot be created, opened or locked for any other reason
     * @throws NullPointerException if {@code manifestFolder} is null
     */
    Acquisition acquire(Path manifestFolder) throws IOException;

    /** What {@link #acquire} found. */
    sealed interface Acquisition {

        /**
         * This instance now holds one share of the lock.
         *
         * @param lease releases that share
         */
        record Acquired(Lease lease) implements Acquisition {
        }

        /** Another process, or another instance of this port in this process, holds the lock. */
        record HeldElsewhere() implements Acquisition {
        }
    }

    /** One share of the lock, taken by one {@link #acquire} call. */
    interface Lease {

        /**
         * Gives the share back; the OS lock is released (and the file closed) when this instance's last share is.
         * Idempotent: a second call does nothing. Never throws: a failure to release is swallowed, since closing the
         * file, or the process ending, releases the OS lock anyway.
         */
        void release();
    }
}
