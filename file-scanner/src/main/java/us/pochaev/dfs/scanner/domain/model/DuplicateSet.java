package us.pochaev.dfs.scanner.domain.model;

import java.util.List;

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
        throw new UnsupportedOperationException("not implemented: DFS-003");
        // implementer: enforce the invariants above here, after removing this throw:
        //   - Objects.requireNonNull(digest, "digest"); reject digest.isBlank()
        //   - Objects.requireNonNull(files, "files"); files = List.copyOf(files)
        //     (List.copyOf also rejects a null element)
        //   - reject files.size() < 2
        //   - reject any entry whose size() != size
        // Each IllegalArgumentException / NullPointerException message names the broken rule
        // (DFS-003.AC-6). Do not sort here: ascending path order is the stage's job, as for
        // CandidateGroup. Deliberately not in the stub itself: a precondition check that runs
        // before this throw would let an argument-validation test pass against the stub (L-013).
    }
}
