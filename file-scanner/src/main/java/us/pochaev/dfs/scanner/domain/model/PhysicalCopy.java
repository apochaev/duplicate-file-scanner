package us.pochaev.dfs.scanner.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * One physical file within a {@link DuplicateSet}, and every path that reaches it: a file with
 * several hard links is one copy with several links. Deleting one link of a copy frees no space
 * while another link remains, so a set counts its duplicates by copies, never by paths
 * (CLAUDE.md: "hard links are not duplicates").
 *
 * <p>Invariants, enforced by the constructor: {@code links} is non-null with no null element and
 * holds at least one entry. It is stored as an immutable defensive copy, in the order given; the
 * hard-link stage (DFS-004) supplies ascending path order.
 *
 * @param links the paths that reach this physical file, at least one
 */
public record PhysicalCopy(List<FileEntry> links) {

    public PhysicalCopy {
        Objects.requireNonNull(links, "links");
        links = List.copyOf(links);
        if (links.isEmpty()) {
            throw new IllegalArgumentException("a physical copy needs at least one link");
        }
    }
}
