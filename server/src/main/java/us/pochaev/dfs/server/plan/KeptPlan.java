package us.pochaev.dfs.server.plan;

import java.util.List;

/**
 * One previewed plan as the server keeps it (DFS-054 decisions 1, 3): already mapped to the API's records and sorted
 * once, so every page is a slice. Immutable; any request thread may read it.
 * <p>
 * Built only by {@link PlanViews#keptPlan}: {@code operations} ordered by size (largest first), then digest, then first
 * link; {@code conflicts} by size (largest first), then digest.
 *
 * @param summary    the plan's summary, its counts equal to the two lists' sizes
 * @param operations every quarantine operation, in page order
 * @param conflicts  every conflict, in page order
 */
record KeptPlan(PlanSummary summary, List<PlanOperation> operations, List<PlanConflict> conflicts) {

    /**
     * One page of the operations; past the end, no items and the same total.
     *
     * @param page 0 or more
     * @param size 1 to 500
     */
    OperationPage operationPage(int page, int size) {
        return new OperationPage(slice(operations, page, size), page, size, operations.size());
    }

    private static <T> List<T> slice(List<T> all, int page, int size) {
        long from = (long) page * size;
        if (from >= all.size()) {
            return List.of();
        }
        return List.copyOf(all.subList((int) from, (int) Math.min(all.size(), from + size)));
    }

    /**
     * One page of the conflicts; past the end, no items and the same total.
     *
     * @param page 0 or more
     * @param size 1 to 500
     */
    ConflictPage conflictPage(int page, int size) {
        return new ConflictPage(slice(conflicts, page, size), page, size, conflicts.size());
    }
}
