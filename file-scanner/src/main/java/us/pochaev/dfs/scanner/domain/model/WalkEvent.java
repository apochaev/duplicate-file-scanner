package us.pochaev.dfs.scanner.domain.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One observation made while walking a directory tree.
 */
public sealed interface WalkEvent {

    record FileFound(FileEntry entry) implements WalkEvent {

        public FileFound {
            Objects.requireNonNull(entry, "entry");
        }
    }

    record Skipped(Path path, SkipReason reason, String detail) implements WalkEvent {

        public Skipped {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(detail, "detail");
        }
    }
}
