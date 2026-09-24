package us.pochaev.dfs.scanner.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Files with identical content, confirmed by a full-content hash: the exported result of the
 * detection pipeline (CLAUDE.md design decisions 1 and 3), presented to the user so they can
 * decide which copies to remove.
 *
 * <p>Carries {@link FileEntry} values (path, size, modification time) rather than bare paths:
 * they give the user-facing view each copy's folder and age, and are the "unchanged since the
 * scan" baseline for re-verification at execution time. The digest gives a set an identity
 * across scans. The hash is a filter, not the delete-time proof (decision 4); until hard-link
 * detection (DFS-004) lands, two paths to one physical file can appear in one set.
 *
 * <p>Invariants, enforced by the constructor: {@code digest} is non-null and not blank;
 * {@code files} is non-null with no null element, holds at least two entries, and every entry's
 * size equals {@code size}. {@code files} is stored as an immutable defensive copy; the full-hash
 * stage supplies it in ascending path order.
 *
 * @param size   the size in bytes shared by every file in the set (the scan-time size)
 * @param digest the lowercase hexadecimal SHA-256 of the files' full content
 * @param files  the files in the set, at least two, in ascending path order
 */
public record DuplicateSet(long size, String digest, List<FileEntry> files) {

    public DuplicateSet {
        Objects.requireNonNull(digest, "digest");
        if (digest.isBlank()) {
            throw new IllegalArgumentException("digest must not be blank");
        }
        Objects.requireNonNull(files, "files");
        files = List.copyOf(files);
        if (files.size() < 2) {
            throw new IllegalArgumentException("a duplicate set needs at least two files: " + files.size());
        }
        for (FileEntry file : files) {
            if (file.size() != size) {
                throw new IllegalArgumentException(
                        "file size " + file.size() + " does not match set size " + size);
            }
        }
    }
}
