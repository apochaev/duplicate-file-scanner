package us.pochaev.dfs.scanner.domain.model;

import java.nio.file.Path;
import java.time.Instant;

/**
 * One direct child of a listed folder (DFS-032 decision 2): what the disk says about it, read without following
 * links. Not matched against any scan.
 */
public sealed interface DirectoryEntry {

    /** The child's file name within its folder. */
    String name();

    /** {@code folder.resolve(name)}, in the form the caller passed the folder in. */
    Path path();

    /** A regular file, empty files and names the scan leaves out by default included. */
    record FileItem(String name, Path path, long size, Instant lastModified) implements DirectoryEntry {

        public FileItem {
        }
    }

    /** A plain directory (not a link, junction or other reparse point); its contents are not listed. */
    record FolderItem(String name, Path path, Instant lastModified) implements DirectoryEntry {

        public FolderItem {
        }
    }

    /**
     * A child left out: a symlink ({@link SkipReason#SYMLINK}), a junction, other reparse point, device, FIFO or
     * socket ({@link SkipReason#SPECIAL_FILE}), or a child whose attributes could not be read
     * ({@link SkipReason#UNREADABLE}, with the exception's message as {@code detail}). Never followed.
     */
    record SkippedItem(String name, Path path, SkipReason reason, String detail) implements DirectoryEntry {

        public SkippedItem {
        }
    }
}
