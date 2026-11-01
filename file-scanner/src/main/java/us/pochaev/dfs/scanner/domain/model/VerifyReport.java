package us.pochaev.dfs.scanner.domain.model;

import java.util.List;

/**
 * What {@code VerifyPlanContent} found for one plan (DFS-034 decision 3).
 *
 * @param planId          the verified plan's {@link DeletionPlan#id()}
 * @param checks          one check per finished operation, in plan order; the operation a cancel interrupted is not
 *                        in it
 * @param cancelled       true when the cancel signal stopped the verify before every operation was checked
 * @param listenerFailure the message of the exception that detached the listener (its {@code toString()} when the
 *                        message is null or blank); empty when the listener never threw
 */
public record VerifyReport(String planId, List<OperationCheck> checks, boolean cancelled, String listenerFailure) {

    public VerifyReport {
    }
}
