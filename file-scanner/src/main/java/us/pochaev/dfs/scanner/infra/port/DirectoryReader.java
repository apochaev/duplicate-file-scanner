package us.pochaev.dfs.scanner.infra.port;

import us.pochaev.dfs.scanner.domain.model.DirectoryEntry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads the direct children of one folder with their attributes (DFS-032 decision 5). Separate from
 * {@link FileTreeWalker}, which walks recursively and reports only regular files and skips.
 */
public interface DirectoryReader {

    /**
     * Checks {@code folder} without following links, then reads its direct children, each classified from its own
     * attributes read without following links: a regular file is a {@code FileItem}, a plain directory a
     * {@code FolderItem}, a symlink a {@code SkippedItem(SYMLINK)}, a junction or other special entry a
     * {@code SkippedItem(SPECIAL_FILE)}, and a child whose attributes cannot be read a {@code SkippedItem(UNREADABLE)}.
     * One bad child never makes the read fail. Nothing is followed and nothing below a child is read.
     *
     * @return one entry per direct child, in the order the file system returned them (not sorted)
     * @throws java.nio.file.NoSuchFileException   if {@code folder} does not exist
     * @throws java.nio.file.NotDirectoryException if {@code folder} is a regular file or other non-directory
     * @throws LinkNotFollowedException            if {@code folder} is a symlink, junction or other reparse point
     * @throws IOException                         if {@code folder} cannot be checked, opened or read to the end
     */
    List<DirectoryEntry> read(Path folder) throws IOException;
}
