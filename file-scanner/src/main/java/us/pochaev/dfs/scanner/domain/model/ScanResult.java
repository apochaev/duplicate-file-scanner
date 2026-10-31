package us.pochaev.dfs.scanner.domain.model;

import java.nio.file.Path;
import java.util.List;

/**
 * The outcome of one completed scan (DFS-005), complete enough for a separate analysis tool to work
 * out folder overlaps without rescanning (DFS-030).
 *
 * @param roots    the locations the scan actually walked (DFS-030 decision 9): resolved real paths,
 *                 sorted ascending, with any location equal to or nested inside another left out
 * @param folders  one entry per folder that directly holds at least one counted file, sorted
 *                 ascending by {@link FolderTotal#folder()} (DFS-030 decision 2); a counted file that
 *                 a later stage drops stays in its folder's total
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
public record ScanResult(List<Path> roots, List<FolderTotal> folders, List<DuplicateSet> sets,
                         List<WalkEvent.Skipped> skipped, long excluded) {

    /**
     * Takes immutable copies of every list, in the order given (the producer, {@code ScanPipeline},
     * is responsible for the order documented above).
     *
     * @throws NullPointerException     if any list is null, or {@code roots} or {@code folders}
     *                                  contains null
     * @throws IllegalArgumentException if {@code excluded} is negative
     */
    public ScanResult {
        if (excluded < 0) {
            throw new IllegalArgumentException("excluded must not be negative: " + excluded);
        }
        roots = List.copyOf(java.util.Objects.requireNonNull(roots, "roots"));
        folders = List.copyOf(java.util.Objects.requireNonNull(folders, "folders"));
        sets = List.copyOf(java.util.Objects.requireNonNull(sets, "sets"));
        skipped = List.copyOf(java.util.Objects.requireNonNull(skipped, "skipped"));
    }
}
