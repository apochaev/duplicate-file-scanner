package us.pochaev.dfs.scanner.domain.model;

/**
 * A scan's state at one moment, as {@code GetScanStatus} and {@code CancelScan} return it
 * (DFS-018 decision 2). Kept current from the same {@link ScanEvent}s the caller's
 * {@link ScanListener} receives. A QUEUED snapshot has a null phase and zero counts.
 *
 * @param status          the lifecycle status; never null
 * @param phase           the phase of the last {@link ScanEvent.PhaseStarted}; null until the
 *                        first one
 * @param filesWalked     the last {@link ScanEvent.FileWalked#filesSoFar()}; 0 until the first one
 * @param groupsDone      the last {@link ScanEvent.GroupDone#done()} of the current phase; reset to
 *                        0 by each {@link ScanEvent.PhaseStarted}
 * @param groupsTotal     the last {@link ScanEvent.GroupDone#total()} of the current phase; reset
 *                        to 0 by each {@link ScanEvent.PhaseStarted}
 * @param failureMessage  for a FAILED scan, {@code <exception class name>: <message>} of what
 *                        failed it; null otherwise
 * @param listenerFailure {@code <exception class name>: <message>} of the first exception the
 *                        caller's listener threw, after which it was detached; null while none
 */
public record ScanSnapshot(ScanStatus status, ScanPhase phase, long filesWalked, int groupsDone,
                           int groupsTotal, String failureMessage, String listenerFailure) {

    public ScanSnapshot {
        java.util.Objects.requireNonNull(status, "status");
    }
}
