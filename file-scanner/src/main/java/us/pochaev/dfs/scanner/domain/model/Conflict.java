package us.pochaev.dfs.scanner.domain.model;

import java.util.List;

/**
 * A duplicate set the plan leaves untouched because one of its entries' keepers would itself be quarantined
 * (DFS-022 decision 3). A plan with conflicts cannot be executed ({@code ui.md} U7.1).
 *
 * @param digest  the set's digest
 * @param sources every rule that would have produced an entry in that set, by ascending index
 */
public record Conflict(String digest, List<Source> sources) {

    public Conflict {
        java.util.Objects.requireNonNull(digest, "digest");
        sources = List.copyOf(sources);
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("sources must not be empty");
        }
    }
}
