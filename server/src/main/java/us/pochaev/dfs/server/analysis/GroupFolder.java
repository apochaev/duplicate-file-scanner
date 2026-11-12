package us.pochaev.dfs.server.analysis;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One folder of a {@link FolderGroup}: a folder that directly holds link paths of the group's sets (DFS-067,
 * docs/design/folder-groups.md G1, G2 and G5). Parents of such a folder are never listed.
 *
 * @param folder      the folder, normalized
 * @param copiesEach  how many physical copies of each set of the group have a path directly in this folder (the same
 *                    number for every set of the group, by the group's definition); a copy with several links in the
 *                    folder counts once
 * @param sharedFiles the folder's link paths in the group's sets (two links of one copy here count twice)
 * @param sharedBytes their summed size in bytes
 * @param folderFiles the folder's direct counted files, from {@code ScanResult.folders}; 0 when it has no entry
 * @param folderBytes their summed size
 * @param otherGroups how many other groups of the scan list this folder
 */
public record GroupFolder(Path folder, int copiesEach, long sharedFiles, long sharedBytes,
                          long folderFiles, long folderBytes, int otherGroups) {

    public GroupFolder {
        Objects.requireNonNull(folder, "folder");
    }
}
