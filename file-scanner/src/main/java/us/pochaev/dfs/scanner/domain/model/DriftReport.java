package us.pochaev.dfs.scanner.domain.model;

import java.util.List;

/**
 * What {@code CheckPlanDrift} found for one plan (DFS-033 decision 2).
 *
 * @param planId  the checked plan's {@link DeletionPlan#id()}
 * @param checked how many distinct paths were read (candidates and keepers, a shared keeper once); never negative
 * @param drifts  only the paths that drifted, in plan order; empty for a clean plan
 */
public record DriftReport(String planId, int checked, List<PathDrift> drifts) {

    public DriftReport {
    }
}
