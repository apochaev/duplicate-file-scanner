package us.pochaev.dfs.scanner.domain.model;

import java.util.List;

/**
 * The outcome of one completed scan (DFS-005).
 *
 * @param sets     the confirmed duplicate sets, in pipeline order: largest file size first, as
 *                 {@code SizeIndex.groups()} produced them, paths ascending inside each set
 * @param skipped  every path left out of the scan and why, in the order it happened: the walker's
 *                 own skips, a location whose walk failed, and every file a stage dropped on an
 *                 {@link java.io.IOException} (reported as {@link SkipReason#UNREADABLE}, the detail
 *                 naming the stage)
 * @param excluded how many regular files the built-in exclude list left out by name (DFS-027),
 *                 counted whatever their size and never listed one by one; 0 when none matched or
 *                 when {@link ScanConfig#defaultExcludes()} is off; never negative
 */
public record ScanResult(List<DuplicateSet> sets, List<WalkEvent.Skipped> skipped, long excluded) {

    /**
     * @throws NullPointerException     if {@code sets} or {@code skipped} is null
     * @throws IllegalArgumentException if {@code excluded} is negative
     */
    public ScanResult {
        java.util.Objects.requireNonNull(sets, "sets");
        java.util.Objects.requireNonNull(skipped, "skipped");
        if (excluded < 0) {
            throw new IllegalArgumentException("excluded must not be negative: " + excluded);
        }
        sets = List.copyOf(sets);
        skipped = List.copyOf(skipped);
    }
}
