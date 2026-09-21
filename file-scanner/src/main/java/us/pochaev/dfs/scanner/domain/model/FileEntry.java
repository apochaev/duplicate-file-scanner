package us.pochaev.dfs.scanner.domain.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * A regular file found while scanning.
 */
public record FileEntry(Path path, long size, Instant lastModified) {

    public FileEntry {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(lastModified, "lastModified");
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative: " + size);
        }
    }
}
