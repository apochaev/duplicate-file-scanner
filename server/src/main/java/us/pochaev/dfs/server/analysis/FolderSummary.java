package us.pochaev.dfs.server.analysis;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One folder's whole subtree in the overlap analysis (docs/design/folder-overlap-analysis.md, decisions 1, 3 and 4):
 * "can this folder go?".
 *
 * @param folder                  the folder, normalized
 * @param totalFiles              counted files in its subtree, from {@code ScanResult.folders} summed up the tree
 * @param totalBytes              their summed size
 * @param filesWithCopyElsewhere  files in the subtree with a different physical copy of their set outside the subtree;
 *                                each file counts once however many copies it has; hard links never count
 * @param bytesWithCopyElsewhere  their summed size
 * @param internalDuplicateFiles  for each set with k &ge; 2 physical copies inside the subtree, k &minus; 1
 * @param internalBytes           for each such set, (k &minus; 1) &times; size
 */
public record FolderSummary(Path folder, long totalFiles, long totalBytes,
                            long filesWithCopyElsewhere, long bytesWithCopyElsewhere,
                            long internalDuplicateFiles, long internalBytes) {

    public FolderSummary {
        Objects.requireNonNull(folder, "folder");
    }
}
