package us.pochaev.dfs.scanner.domain.model;

import java.util.UUID;

/**
 * Identifies one plan execution started by {@code StartPlanExecution} (DFS-051 decision 7). It is also the run ID:
 * the manifest is {@code <manifest folder>/<value>.jsonl} and quarantined files go under
 * {@code .dfs-quarantine/<value>/} (decisions 5, 6). Known to the job state only of the {@code PlanExecutionUseCases}
 * set that issued it, and only in this process; the manifest outlives both.
 *
 * @param value the random UUID; never null
 */
public record PlanExecutionId(UUID value) {

    public PlanExecutionId {
    }

    /** A new ID with a random UUID. */
    public static PlanExecutionId random() {
        throw new UnsupportedOperationException("not implemented: DFS-051");
    }
}
