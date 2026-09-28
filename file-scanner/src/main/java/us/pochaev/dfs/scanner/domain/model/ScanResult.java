package us.pochaev.dfs.scanner.domain.model;

import java.util.List;

/**
 * The outcome of one completed scan (DFS-005).
 *
 * @param sets    the confirmed duplicate sets, in pipeline order: largest file size first, as
 *                {@code SizeIndex.groups()} produced them, paths ascending inside each set
 * @param skipped every path left out of the scan and why, in the order it happened: the walker's
 *                own skips, a location whose walk failed, and every file a stage dropped on an
 *                {@link java.io.IOException} (reported as {@link SkipReason#UNREADABLE}, the detail
 *                naming the stage)
 */
public record ScanResult(List<DuplicateSet> sets, List<WalkEvent.Skipped> skipped) {

    public ScanResult {
        throw new UnsupportedOperationException("not implemented: DFS-005");
        // implementer: requireNonNull both lists and take immutable copies (List.copyOf, which
        // also rejects null elements).
    }
}
