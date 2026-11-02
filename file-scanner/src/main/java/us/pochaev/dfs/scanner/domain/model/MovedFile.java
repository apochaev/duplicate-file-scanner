package us.pochaev.dfs.scanner.domain.model;

import java.nio.file.Path;

/**
 * One path an execution moved into quarantine (DFS-051 decision 5).
 *
 * @param original   where the file was, as the plan holds it
 * @param quarantine where it is now: {@code <volume root or anchor>/.dfs-quarantine/<run ID>/<path below it>}
 */
public record MovedFile(Path original, Path quarantine) {

    public MovedFile {
        java.util.Objects.requireNonNull(original, "original");
        java.util.Objects.requireNonNull(quarantine, "quarantine");
    }
}
