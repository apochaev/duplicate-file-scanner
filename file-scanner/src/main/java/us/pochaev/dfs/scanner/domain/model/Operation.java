package us.pochaev.dfs.scanner.domain.model;

import java.nio.file.Path;
import java.util.List;

/**
 * One typed step of a {@link DeletionPlan} ({@code ui.md} U5). The first version has only {@link Quarantine}; a later
 * {@code Move} or {@code Consolidate} is a new variant.
 */
public sealed interface Operation {

    /** The rule or pick that produced this operation. */
    Source source();

    /**
     * Move one physical copy, all its links together, to the quarantine (DFS-022 decisions 4, 5). Frees
     * {@code size} bytes once.
     *
     * @param digest the set's digest
     * @param size   the set's file size in bytes
     * @param links  every link of the quarantined copy, sorted by path
     * @param keeper the first link (by path) of the copy this one is kept against
     * @param source the rule or pick that produced it
     */
    record Quarantine(String digest, long size, List<Path> links, Path keeper, Source source) implements Operation {

        public Quarantine {
            java.util.Objects.requireNonNull(digest, "digest");
            java.util.Objects.requireNonNull(keeper, "keeper");
            java.util.Objects.requireNonNull(source, "source");
            links = List.copyOf(links);
            if (links.isEmpty()) {
                throw new IllegalArgumentException("links must not be empty");
            }
            if (size < 0) {
                throw new IllegalArgumentException("size must not be negative: " + size);
            }
        }
    }
}
