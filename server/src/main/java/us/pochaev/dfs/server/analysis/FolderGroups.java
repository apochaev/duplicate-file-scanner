package us.pochaev.dfs.server.analysis;

import us.pochaev.dfs.scanner.domain.model.ScanResult;

import java.util.List;

/**
 * The folder groups of one completed scan (DFS-067; docs/design/folder-groups.md G1, G3, G6): a structure of its own,
 * next to {@link OverlapAnalysis} and independent of it (decision 4). Built once by {@link #of}; immutable, so any
 * thread may read it.
 * <p>
 * For each duplicate set, its folders are the normalized parents of its link paths, each with {@code copiesEach}: the
 * number of the set's physical copies that have a path there (a copy with two links in one folder counts once; a copy
 * with links in several folders appears in each of them, and still counts once in {@code copies} and in the reclaimable
 * figure). Sets with equal folder lists, path and {@code copiesEach} for path, form one {@link FolderGroup}. One pass
 * over the sets, one map entry per set; {@code otherGroups} of a folder is the number of groups listing it, minus one.
 * Paths are compared with the platform's {@link java.nio.file.Path} rules.
 * <p>
 * Order of {@link #groups()} (G3): {@code reclaimableBytes} descending, then the first folder's path, then the folder
 * lists compared folder by folder (path, then {@code copiesEach}; a list that is a prefix of another comes first).
 */
public final class FolderGroups {

    private FolderGroups() {
    }

    /** Groups {@code result}'s duplicate sets once. */
    public static FolderGroups of(ScanResult result) {
        throw new UnsupportedOperationException("not implemented: DFS-067");
    }

    /** Every group, in the fixed order. */
    public List<FolderGroup> groups() {
        throw new UnsupportedOperationException("not implemented: DFS-067");
    }
}
