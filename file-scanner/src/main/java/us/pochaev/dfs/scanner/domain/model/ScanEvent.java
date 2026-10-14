package us.pochaev.dfs.scanner.domain.model;

import java.util.Objects;

/** A progress event of a scan, sent to a {@link ScanListener} (DFS-005). */
public sealed interface ScanEvent {

    /** A phase begins. Sent once per phase, in {@link ScanPhase} order, even if it has no groups. */
    record PhaseStarted(ScanPhase phase) implements ScanEvent {

        public PhaseStarted {
            Objects.requireNonNull(phase, "phase");
        }
    }

    /**
     * The walker yielded a regular file; {@code filesSoFar} counts them from 1 across all locations,
     * and {@code bytesSoFar} is the sum of their sizes so far (before any minimum-size filter, DFS-024).
     */
    record FileWalked(long filesSoFar, long bytesSoFar) implements ScanEvent {

        public FileWalked {
            if (filesSoFar < 1) {
                throw new IllegalArgumentException("filesSoFar must be at least 1: " + filesSoFar);
            }
            if (bytesSoFar < 0) {
                throw new IllegalArgumentException("bytesSoFar must not be negative: " + bytesSoFar);
            }
        }
    }

    /**
     * The walker entered a directory, a start location included; {@code directoriesSoFar} counts
     * them from 1 across all locations (DFS-024).
     */
    record DirectoryWalked(long directoriesSoFar) implements ScanEvent {

        public DirectoryWalked {
            if (directoriesSoFar < 1) {
                throw new IllegalArgumentException("directoriesSoFar must be at least 1: " + directoriesSoFar);
            }
        }
    }

    /**
     * Progress through the files of a {@link ScanPhase#PARTIAL_HASH} or {@link ScanPhase#FULL_HASH}
     * phase (DFS-024). {@code filesTotal} and {@code bytesTotal} are the phase input's file count and
     * size sum, fixed for the phase. Sent after each file (an unreadable one included), and in the
     * full-hash phase also while a file is read; {@code bytesDone} counts finished files by their
     * scan-time size plus the part of the current file read so far, never more than its size.
     */
    record HashProgress(ScanPhase phase, long filesDone, long filesTotal, long bytesDone, long bytesTotal)
            implements ScanEvent {

        public HashProgress {
            Objects.requireNonNull(phase, "phase");
            if (phase != ScanPhase.PARTIAL_HASH && phase != ScanPhase.FULL_HASH) {
                throw new IllegalArgumentException("HashProgress is sent only for the hash phases: " + phase);
            }
            if (filesDone < 0 || filesDone > filesTotal) {
                throw new IllegalArgumentException(
                        "filesDone must be between 0 and filesTotal (" + filesTotal + "): " + filesDone);
            }
            if (bytesDone < 0 || bytesDone > bytesTotal) {
                throw new IllegalArgumentException(
                        "bytesDone must be between 0 and bytesTotal (" + bytesTotal + "): " + bytesDone);
            }
        }
    }

    /**
     * A hashing or hard-link phase finished one of the {@code total} groups that entered it;
     * {@code done} rises from 1 to {@code total}.
     */
    record GroupDone(ScanPhase phase, int done, int total) implements ScanEvent {

        public GroupDone {
            Objects.requireNonNull(phase, "phase");
            if (phase == ScanPhase.WALK) {
                throw new IllegalArgumentException("GroupDone is not sent for WALK");
            }
            if (done < 1 || done > total) {
                throw new IllegalArgumentException("done must be between 1 and total (" + total + "): " + done);
            }
        }
    }

    /** A path was left out of the scan: a walker skip, a failed location, or a stage drop. */
    record Skipped(WalkEvent.Skipped skipped) implements ScanEvent {

        public Skipped {
            Objects.requireNonNull(skipped, "skipped");
        }
    }
}
