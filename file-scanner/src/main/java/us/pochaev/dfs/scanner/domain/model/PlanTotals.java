package us.pochaev.dfs.scanner.domain.model;

/**
 * The totals of a {@link DeletionPlan} (DFS-022 decision 5; {@code ui.md} U7.1).
 *
 * @param files links moved, over every operation
 * @param bytes bytes freed: each operation's set size once
 * @param sets  sets with at least one operation
 */
public record PlanTotals(long files, long bytes, int sets) {

    public PlanTotals {
        if (files < 0 || bytes < 0 || sets < 0) {
            throw new IllegalArgumentException("totals must not be negative: " + files + ", " + bytes + ", " + sets);
        }
    }
}
