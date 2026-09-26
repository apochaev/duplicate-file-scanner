package us.pochaev.dfs.scanner.domain.model;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Files with identical content, confirmed by a full-content hash: the exported result of the
 * detection pipeline (CLAUDE.md design decisions 1 and 3), presented to the user so they can
 * decide which copies to remove.
 *
 * <p>Carries {@link FileEntry} values (path, size, modification time) rather than bare paths:
 * they give the user-facing view each copy's folder and age, and are the "unchanged since the
 * scan" baseline for re-verification at execution time. The digest gives a set an identity
 * across scans. The hash is a filter, not the delete-time proof (decision 4).
 *
 * <p>{@code files} lists every path; {@code copies} groups those paths into physical files
 * (DFS-017), since hard links to one file are one copy, not duplicates of each other. Deleting a
 * link frees no space while another link of its copy remains. The 3-argument constructor treats
 * every file as its own single-link copy, as the full-hash stage produces them before hard-link
 * detection (DFS-004) regroups them.
 *
 * <p>Invariants, enforced by the canonical constructor: {@code digest} is non-null and not blank;
 * {@code files} and {@code copies} are non-null with no null element; there are at least two
 * physical copies; every file's size equals {@code size}; and {@code copies} exactly partition
 * {@code files}: every file appears as a link of exactly one copy, and no copy holds a link that
 * is not one of {@code files}. Both lists are stored as immutable defensive copies. The record
 * validates but does not sort: the stages supply files in ascending path order, and copies
 * ordered by their first link.
 *
 * @param size   the size in bytes shared by every file in the set (the scan-time size)
 * @param digest the lowercase hexadecimal SHA-256 of the files' full content
 * @param files  every path in the set, in ascending path order
 * @param copies the physical files those paths reach, at least two
 */
public record DuplicateSet(long size, String digest, List<FileEntry> files, List<PhysicalCopy> copies) {

    public DuplicateSet {
        Objects.requireNonNull(digest, "digest");
        if (digest.isBlank()) {
            throw new IllegalArgumentException("digest must not be blank");
        }
        Objects.requireNonNull(files, "files");
        Objects.requireNonNull(copies, "copies");
        files = List.copyOf(files);
        copies = List.copyOf(copies);
        if (copies.size() < 2) {
            throw new IllegalArgumentException(
                    "a duplicate set needs at least two physical copies: " + copies.size());
        }
        Map<Path, FileEntry> byPath = new HashMap<>();
        for (FileEntry file : files) {
            if (file.size() != size) {
                throw new IllegalArgumentException(
                        "file size " + file.size() + " does not match set size " + size);
            }
            if (byPath.put(file.path(), file) != null) {
                throw new IllegalArgumentException("path listed twice in files: " + file.path());
            }
        }
        Set<Path> linked = new HashSet<>();
        for (PhysicalCopy copy : copies) {
            for (FileEntry link : copy.links()) {
                if (!link.equals(byPath.get(link.path()))) {
                    throw new IllegalArgumentException("copy holds a link that is not in files: " + link.path());
                }
                if (!linked.add(link.path())) {
                    throw new IllegalArgumentException("path appears in two copies: " + link.path());
                }
            }
        }
        if (linked.size() != byPath.size()) {
            throw new IllegalArgumentException(
                    "copies do not cover every file: " + linked.size() + " of " + byPath.size());
        }
    }

    /**
     * A set in which every file is its own physical copy, as the full-hash stage produces it.
     *
     * @throws NullPointerException     if {@code digest} or {@code files} is null, or {@code files}
     *                                  contains null
     * @throws IllegalArgumentException if the canonical constructor's invariants do not hold
     */
    public DuplicateSet(long size, String digest, List<FileEntry> files) {
        this(size, digest, files, singleLinkCopies(files));
    }

    private static List<PhysicalCopy> singleLinkCopies(List<FileEntry> files) {
        Objects.requireNonNull(files, "files");
        return files.stream().map(file -> new PhysicalCopy(List.of(file))).toList();
    }
}
