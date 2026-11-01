package us.pochaev.dfs.scanner.infra.adapter;

import us.pochaev.dfs.scanner.domain.model.SkipReason;

import java.nio.file.attribute.BasicFileAttributes;

/**
 * How {@link NioFileTreeWalker} and {@link NioDirectoryReader} classify one entry from its attributes read without
 * following links (DFS-032 decision 5), shared so the two cannot drift. {@code isOther()} is what catches Windows
 * junctions and other reparse points, which also report {@code isDirectory()}.
 */
enum EntryKind {
    /** A regular file. */
    REGULAR_FILE(null, null),
    /** A plain directory: not a link, junction or other reparse point. */
    DIRECTORY(null, null),
    /** A symbolic link, to anything. */
    SYMLINK(SkipReason.SYMLINK, "symbolic link"),
    /** A directory that is also {@code isOther()}: a junction, mount point or other reparse point. */
    SPECIAL_DIRECTORY(SkipReason.SPECIAL_FILE, "reparse point or special directory"),
    /** Anything else: a reparse-point file, device, FIFO or socket. */
    SPECIAL_FILE(SkipReason.SPECIAL_FILE, "not a regular file");

    private final SkipReason reason;
    private final String detail;

    EntryKind(SkipReason reason, String detail) {
        this.reason = reason;
        this.detail = detail;
    }

    /** Why an entry of this kind is skipped; {@code null} for {@link #REGULAR_FILE} and {@link #DIRECTORY}. */
    SkipReason reason() {
        return reason;
    }

    /** The skip detail the walker reports for this kind; {@code null} for the two listed kinds. */
    String detail() {
        return detail;
    }

    /**
     * Checked in this order: {@code isSymbolicLink()} is {@link #SYMLINK}; {@code isOther()} is
     * {@link #SPECIAL_DIRECTORY} when {@code isDirectory()}, else {@link #SPECIAL_FILE}; then {@code isDirectory()} is
     * {@link #DIRECTORY}; {@code isRegularFile()} is {@link #REGULAR_FILE}; anything else is {@link #SPECIAL_FILE}.
     */
    static EntryKind of(BasicFileAttributes attrs) {
        if (attrs.isSymbolicLink()) {
            return SYMLINK;
        }
        if (attrs.isOther()) {
            return attrs.isDirectory() ? SPECIAL_DIRECTORY : SPECIAL_FILE;
        }
        if (attrs.isDirectory()) {
            return DIRECTORY;
        }
        return attrs.isRegularFile() ? REGULAR_FILE : SPECIAL_FILE;
    }
}
