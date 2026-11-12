package us.pochaev.dfs.server.analysis;

import java.util.List;
import java.util.Objects;

/**
 * Duplicate sets whose copies sit directly in the same folders with the same {@code copiesEach} per folder (DFS-067,
 * docs/design/folder-groups.md G1).
 *
 * @param files           the number of sets in the group (equal to {@code totalSets}; the API names both)
 * @param copies          physical copies over all its sets
 * @param reclaimableBytes for each set, (physical copies &minus; 1) &times; size, summed
 * @param folders         the folders that directly hold any of its paths, sorted by path, at least one
 * @param sets            the first 20 sets, largest reclaimable first (then by digest)
 * @param totalSets       every set of the group, not only the listed ones
 */
public record FolderGroup(long files, long copies, long reclaimableBytes, List<GroupFolder> folders,
                          List<GroupSet> sets, int totalSets) {

    public FolderGroup {
        Objects.requireNonNull(folders, "folders");
        Objects.requireNonNull(sets, "sets");
        folders = List.copyOf(folders);
        sets = List.copyOf(sets);
    }
}
