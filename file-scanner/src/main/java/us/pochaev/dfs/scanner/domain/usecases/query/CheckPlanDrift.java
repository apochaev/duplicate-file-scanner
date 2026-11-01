package us.pochaev.dfs.scanner.domain.usecases.query;

import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.DriftReport;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanResult;

/**
 * Re-reads the size and last-modified time of every path of a previewed {@link DeletionPlan}, candidates and keepers,
 * and reports each one that no longer matches what the scan recorded (DFS-033, {@code ui.md} U7.2). A cheap,
 * read-only early warning: each path's attributes are read once without following links, no content is read and
 * nothing is written. It is not the delete-time proof, which stays the byte compare at execution (CLAUDE.md
 * decision 4). Runs synchronously on the caller's thread.
 */
public interface CheckPlanDrift {

    /**
     * @param scan the scan the plan was previewed from; it holds each path's recorded size and last-modified time
     * @param plan the previewed plan; its conflicts hold no paths to check
     * @return {@code Success} holding the report: the plan's id, the number of distinct paths read, and one entry per
     *         drifted path in plan order (each operation's links, then its keeper; a path met again is checked and
     *         reported once); or {@code Failure(PLAN_NOT_FROM_SCAN)} naming the first operation digest, link or keeper
     *         the scan does not hold, in which case nothing was read from disk
     * @throws NullPointerException if {@code scan} or {@code plan} is null
     */
    Result<DriftReport> check(ScanResult scan, DeletionPlan plan);
}
