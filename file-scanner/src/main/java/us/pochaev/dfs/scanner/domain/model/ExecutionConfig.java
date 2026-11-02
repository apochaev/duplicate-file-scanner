package us.pochaev.dfs.scanner.domain.model;

import java.nio.file.Path;
import java.util.List;

/**
 * Settings of plan execution (DFS-051 decisions 6, 10), set by the app; the core picks no default. A plain record so
 * {@code @ConfigurationProperties} can map onto it (CLAUDE.md, Spring-friendliness rules).
 *
 * @param manifestFolder    absolute folder holding one {@code <run ID>.jsonl} manifest per run; created on first use
 * @param quarantineAnchors absolute folders that stand in for a volume root (the story's {@code design.md}, "Open
 *                          design notes"): a file below one of them is quarantined under the deepest such anchor,
 *                          {@code <anchor>/.dfs-quarantine/<run ID>/<path below the anchor>}; any other file under its
 *                          volume root (decision 5). Empty by default
 */
public record ExecutionConfig(Path manifestFolder, List<Path> quarantineAnchors) {

    public ExecutionConfig {
        java.util.Objects.requireNonNull(manifestFolder, "manifestFolder");
        java.util.Objects.requireNonNull(quarantineAnchors, "quarantineAnchors");
        quarantineAnchors = List.copyOf(quarantineAnchors);
        if (!manifestFolder.isAbsolute()) {
            throw new IllegalArgumentException("manifestFolder must be absolute: " + manifestFolder);
        }
        for (Path anchor : quarantineAnchors) {
            if (!anchor.isAbsolute()) {
                throw new IllegalArgumentException("quarantine anchor must be absolute: " + anchor);
            }
        }
    }

    /** No anchors: every file is quarantined under its own volume root (decision 5). */
    public ExecutionConfig(Path manifestFolder) {
        this(manifestFolder, List.of());
    }
}
