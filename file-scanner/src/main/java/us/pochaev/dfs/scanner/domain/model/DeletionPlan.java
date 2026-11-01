package us.pochaev.dfs.scanner.domain.model;

import java.util.List;

/**
 * The frozen result of previewing a {@link PlanRequest} on a {@link ScanResult} (DFS-022 decisions 1, 5;
 * {@code ui.md} U5-U7). Nothing has touched the disk; a later execution runs exactly this plan and recomputes
 * {@code id} to detect an edit.
 *
 * @param id         lowercase hex SHA-256 of the operations and conflicts in their canonical encoding (the story's
 *                   {@code design.md})
 * @param operations ordered by source (rules by index, then picks by digest), then by first link
 * @param conflicts  ordered by digest
 * @param totals     links moved, bytes freed and sets touched
 * @param leftBehind one entry per rule, in rule order
 */
public record DeletionPlan(String id, List<Operation> operations, List<Conflict> conflicts, PlanTotals totals,
                           List<LeftBehind> leftBehind) {
}
