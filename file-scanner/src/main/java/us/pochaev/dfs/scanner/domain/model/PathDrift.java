package us.pochaev.dfs.scanner.domain.model;

import java.nio.file.Path;

/**
 * One plan path that no longer matches what the scan recorded (DFS-033 decision 2).
 *
 * @param path   the path as the plan and the scan hold it
 * @param role   whether the path was met first as a link of an operation or as its keeper
 * @param digest the digest of the operation the path was met in first
 * @param kind   the first kind that applies, in {@link DriftKind}'s precedence
 * @param detail empty, except for {@link DriftKind#UNREADABLE}: the exception's message, or its {@code toString()}
 *               when the message is null or blank
 */
public record PathDrift(Path path, Role role, String digest, DriftKind kind, String detail) {

    public PathDrift {
        java.util.Objects.requireNonNull(path, "path");
        java.util.Objects.requireNonNull(role, "role");
        java.util.Objects.requireNonNull(digest, "digest");
        java.util.Objects.requireNonNull(kind, "kind");
        java.util.Objects.requireNonNull(detail, "detail");
    }

    /** The part a path plays in the plan. */
    public enum Role {
        /** A link of a {@link Operation.Quarantine}: the plan would move it. */
        CANDIDATE,
        /** The keeper of an operation: the plan keeps it and verifies candidates against it. */
        KEEPER
    }
}
