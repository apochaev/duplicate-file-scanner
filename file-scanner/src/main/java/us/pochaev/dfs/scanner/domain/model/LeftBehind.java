package us.pochaev.dfs.scanner.domain.model;

import java.nio.file.Path;

/**
 * What stays in one rule's quarantine folder after the plan (DFS-022 decision 6): the scanned files and bytes at or
 * under {@code folder}, summed from {@link ScanResult#folders()}, minus the links and bytes the plan quarantines there.
 *
 * @param ruleIndex the rule's position in {@link PlanRequest#rules()}
 * @param folder    the rule's {@link KeepAndQuarantine#quarantineIn()}
 * @param files     scanned files left at or under it
 * @param bytes     their summed size in bytes
 */
public record LeftBehind(int ruleIndex, Path folder, long files, long bytes) {
}
