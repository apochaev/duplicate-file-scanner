package us.pochaev.dfs.scannerspring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import us.pochaev.dfs.scanner.domain.model.ExecutionConfig;

import java.nio.file.Path;
import java.util.List;

/**
 * The plan execution settings under {@code dfs.execution} (DFS-051 decision 10), the scanner's first
 * {@code @ConfigurationProperties} (CLAUDE.md decision 6). Strings, turned into absolute paths by {@link #toConfig()},
 * so binding needs no path converter. Binding creates nothing on disk (decision 13).
 *
 * @param manifestFolder    {@code dfs.execution.manifest-folder}; default {@code ${user.home}/.dfs/manifests}
 * @param quarantineAnchors {@code dfs.execution.quarantine-anchors}; default none (the story's {@code design.md},
 *                          "Open design notes")
 */
@ConfigurationProperties("dfs.execution")
public record ExecutionProperties(String manifestFolder, List<String> quarantineAnchors) {

    public ExecutionProperties {
        if (manifestFolder == null || manifestFolder.isBlank()) {
            manifestFolder = Path.of(System.getProperty("user.home"), ".dfs", "manifests").toString();
        }
        quarantineAnchors = quarantineAnchors == null ? List.of() : List.copyOf(quarantineAnchors);
    }

    /** The core's config: each path made absolute and normalized. */
    public ExecutionConfig toConfig() {
        return new ExecutionConfig(Path.of(manifestFolder).toAbsolutePath().normalize(),
                quarantineAnchors.stream().map(a -> Path.of(a).toAbsolutePath().normalize()).toList());
    }
}
