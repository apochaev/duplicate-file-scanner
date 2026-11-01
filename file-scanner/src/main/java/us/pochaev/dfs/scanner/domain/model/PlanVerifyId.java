package us.pochaev.dfs.scanner.domain.model;

import java.util.UUID;

/**
 * Identifies one asynchronous plan verify started by {@code StartPlanVerify} (DFS-042 decision 1). Known only to the
 * {@code PlanVerifyUseCases} set that issued it, and only in this process.
 *
 * @param value the random UUID; never null
 */
public record PlanVerifyId(UUID value) {

    public PlanVerifyId {
    }

    /** A new ID with a random UUID. */
    public static PlanVerifyId random() {
        return new PlanVerifyId(UUID.randomUUID());
    }
}
