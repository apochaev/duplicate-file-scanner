package us.pochaev.dfs.server.result;

import us.pochaev.dfs.server.analysis.FolderGroup;
import us.pochaev.dfs.server.analysis.FolderGroups;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A COMPLETED scan's folder groups as the groups endpoint serves them (DFS-067): filters and pages the ordered
 * {@link FolderGroups#groups()} list and maps its records to the API's own ({@link FolderGroupView},
 * {@link GroupFolderView}, {@link GroupSetView}). Immutable, so any request thread may read it; {@link GroupState}
 * builds it once per scan.
 * <p>
 * Paths given to it are absolute, normalized and inside a root (the controller checks them first); subtree tests use
 * {@link Path#startsWith}, so a folder is inside its own subtree.
 */
final class GroupIndex {

    private final FolderGroups groups;

    GroupIndex(FolderGroups groups) {
        this.groups = Objects.requireNonNull(groups, "groups");
    }

    /**
     * One page of the groups in G3 order: those with at least one folder at or under {@code under} (all when null),
     * whose {@code reclaimableBytes} reach {@code minBytes}. {@code hiddenSmall} counts the matching ones below
     * {@code minBytes}; {@code totalItems} the matching ones at or above it.
     *
     * @param under    absolute, normalized and inside a root, or null
     * @param minBytes 0 or more
     * @param page     0 or more
     * @param size     1 to 500
     */
    GroupPage page(Path under, long minBytes, int page, int size) {
        List<FolderGroup> matching = new ArrayList<>();
        int hidden = 0;
        for (FolderGroup group : groups.groups()) {
            if (under != null && group.folders().stream().noneMatch(f -> f.folder().startsWith(under))) {
                continue;
            }
            if (group.reclaimableBytes() < minBytes) {
                hidden++;
            } else {
                matching.add(group);
            }
        }
        long from = (long) page * size;
        List<FolderGroupView> items = new ArrayList<>();
        for (long i = from; i < Math.min(matching.size(), from + size); i++) {
            items.add(view(matching.get((int) i)));
        }
        return new GroupPage(items, page, size, matching.size(), hidden);
    }

    private static FolderGroupView view(FolderGroup g) {
        List<GroupFolderView> folders = g.folders().stream()
                .map(f -> new GroupFolderView(f.folder().toString(), f.copiesEach(), f.sharedFiles(), f.sharedBytes(),
                        f.folderFiles(), f.folderBytes(), f.otherGroups()))
                .toList();
        List<GroupSetView> sets = g.sets().stream()
                .map(s -> new GroupSetView(s.digest(), s.name(), s.size(), s.copies()))
                .toList();
        return new FolderGroupView(g.files(), g.copies(), g.reclaimableBytes(), folders, sets, g.totalSets());
    }
}
