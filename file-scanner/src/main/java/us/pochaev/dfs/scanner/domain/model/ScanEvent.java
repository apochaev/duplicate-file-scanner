package us.pochaev.dfs.scanner.domain.model;

/** A progress event of a scan, sent to a {@link ScanListener} (DFS-005). */
public sealed interface ScanEvent {

    /** A phase begins. Sent once per phase, in {@link ScanPhase} order, even if it has no groups. */
    record PhaseStarted(ScanPhase phase) implements ScanEvent {

        public PhaseStarted {
            throw new UnsupportedOperationException("not implemented: DFS-005");
            // implementer: requireNonNull(phase).
        }
    }

    /** The walker yielded a regular file; {@code filesSoFar} counts them from 1 across all locations. */
    record FileWalked(long filesSoFar) implements ScanEvent {

        public FileWalked {
            throw new UnsupportedOperationException("not implemented: DFS-005");
            // implementer: reject filesSoFar < 1 with IllegalArgumentException.
        }
    }

    /**
     * A hashing or hard-link phase finished one of the {@code total} groups that entered it;
     * {@code done} rises from 1 to {@code total}.
     */
    record GroupDone(ScanPhase phase, int done, int total) implements ScanEvent {

        public GroupDone {
            throw new UnsupportedOperationException("not implemented: DFS-005");
            // implementer: requireNonNull(phase), reject phase == WALK, and reject
            // done < 1 or done > total with IllegalArgumentException.
        }
    }

    /** A path was left out of the scan: a walker skip, a failed location, or a stage drop. */
    record Skipped(WalkEvent.Skipped skipped) implements ScanEvent {

        public Skipped {
            throw new UnsupportedOperationException("not implemented: DFS-005");
            // implementer: requireNonNull(skipped).
        }
    }
}
