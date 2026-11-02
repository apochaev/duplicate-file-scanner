package us.pochaev.dfs.server.analysis;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One direction of a {@link FolderPair}: how many of this folder's files have a copy in the other folder
 * (CLAUDE.md design decision 1's "which files in B have a copy in A").
 *
 * @param folder        this side's folder, normalized
 * @param filesWithCopy its files with a different physical copy of their set in the other folder
 * @param totalFiles    its counted files
 * @param bytesWithCopy the summed size of {@code filesWithCopy}
 * @param totalBytes    the summed size of {@code totalFiles}
 */
public record PairSide(Path folder, long filesWithCopy, long totalFiles, long bytesWithCopy, long totalBytes) {

    public PairSide {
        Objects.requireNonNull(folder, "folder");
    }

    /** True exactly when this side has at least one file and every one of them has a copy in the other folder. */
    public boolean full() {
        return totalFiles > 0 && filesWithCopy == totalFiles;
    }
}
