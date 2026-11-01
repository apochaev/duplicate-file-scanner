package us.pochaev.dfs.scanner.domain.model;

import java.nio.file.Path;

/**
 * A single-set pick (DFS-022 decision 2): in the set with {@code digest}, the copy that has the link {@code keep} is
 * the keeper and every other copy is quarantined; rules and exceptions are ignored for that set.
 *
 * @param digest the {@link DuplicateSet#digest()} of the set
 * @param keep   a link of the copy to keep, exactly as the scan recorded it
 */
public record KeepCopy(String digest, Path keep) {

    public KeepCopy {
        java.util.Objects.requireNonNull(digest, "digest");
        java.util.Objects.requireNonNull(keep, "keep");
    }
}
