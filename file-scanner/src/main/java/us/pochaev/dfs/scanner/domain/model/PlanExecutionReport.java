package us.pochaev.dfs.scanner.domain.model;

import java.nio.file.Path;
import java.util.List;

/**
 * What one plan execution did (DFS-051 decision 9), as {@code GetPlanExecutionResult} returns it for a COMPLETED or
 * CANCELLED run.
 * <p>
 * Totals: {@code filesMoved} counts every {@link MovedFile} and {@code filesSkipped} every {@link SkippedFile} over all
 * outcomes; {@code bytesMoved} adds each {@link OperationOutcome.Moved}'s size once, and {@code bytesSkipped} each
 * {@link OperationOutcome.Skipped}'s and {@link OperationOutcome.PartlyMoved}'s size once (a partly moved copy still has
 * a link in place, so it frees nothing). With the not-attempted operations' sizes they add up to the plan's
 * {@code totals().bytes()}.
 *
 * @param runId           the run's ID, also the manifest's name and the quarantine folder's name
 * @param planId          the executed plan's {@link DeletionPlan#id()}
 * @param manifest        the absolute path of {@code <manifest folder>/<run ID>.jsonl}
 * @param outcomes        one outcome per plan operation, in plan order
 * @param cancelled       true when a cancel was recorded before the run's completion
 * @param filesMoved      paths moved
 * @param bytesMoved      bytes of operations moved whole
 * @param filesSkipped    paths skipped
 * @param bytesSkipped    bytes of operations skipped or partly moved
 * @param listenerFailure the message of the exception that detached the listener (its {@code toString()} when the
 *                        message is null or blank); empty when the listener never threw or there was none
 */
public record PlanExecutionReport(PlanExecutionId runId, String planId, Path manifest, List<OperationOutcome> outcomes,
                                  boolean cancelled, int filesMoved, long bytesMoved, int filesSkipped,
                                  long bytesSkipped, String listenerFailure) {

    public PlanExecutionReport {
    }
}
