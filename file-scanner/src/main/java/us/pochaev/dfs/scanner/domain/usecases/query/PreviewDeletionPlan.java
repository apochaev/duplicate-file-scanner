package us.pochaev.dfs.scanner.domain.usecases.query;

import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.PlanRequest;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanResult;

/**
 * Applies deletion rules, exceptions and picks to a completed scan and returns the plan they produce (DFS-022). A
 * pure query: it reads only its arguments, never the disk or any scanner state (CLAUDE.md, "Preview is a pure
 * query"), and every plan it returns leaves each set at least one surviving copy.
 */
public interface PreviewDeletionPlan {

    /**
     * @return {@code Success} holding the plan; or {@code Failure} with {@code INVALID_RULE}, {@code UNKNOWN_SET},
     *         {@code PATH_NOT_IN_SET} or {@code DUPLICATE_PICK} for the first problem in the request (rules in order,
     *         then picks in order), with nothing planned
     * @throws NullPointerException if {@code scan} or {@code request} is null
     */
    Result<DeletionPlan> preview(ScanResult scan, PlanRequest request);
}
