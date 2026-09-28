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

    /** The walker yielded a regular file; {@code filesSoFar} counts them from 1 across all locations. */
    record FileWalked(long filesSoFar) implements ScanEvent {

        public FileWalked {
            if (filesSoFar < 1) {
                throw new IllegalArgumentException("filesSoFar must be at least 1: " + filesSoFar);
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
