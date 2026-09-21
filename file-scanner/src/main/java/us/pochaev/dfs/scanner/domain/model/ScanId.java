package us.pochaev.dfs.scanner.domain.model;

import java.util.Objects;
import java.util.UUID;

public record ScanId(UUID value) {

    public ScanId {
        Objects.requireNonNull(value, "value");
    }

    public static ScanId random() {
        return new ScanId(UUID.randomUUID());
    }
}
