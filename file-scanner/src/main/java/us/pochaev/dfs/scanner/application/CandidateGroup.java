package us.pochaev.dfs.scanner.application;

import java.util.List;
import us.pochaev.dfs.scanner.domain.model.FileEntry;

/**
 * The currency between detection-pipeline stages 1 to 3: two or more files sharing one size,
 * still possibly identical. Internal to {@code application}; the exported result of the
 * pipeline is {@code DuplicateSet} (DFS-003).
 *
 * <p>Carries {@link FileEntry} values rather than bare paths, because the size and modification
 * time seen by the scan are the baseline for the "unchanged since the scan" re-verification at
 * execution time.
 *
 * <p>Invariants, enforced by the constructor: {@code files} is non-null with no null element,
 * holds at least two entries, and every entry's size equals {@code size}. {@code files} is
 * stored as an immutable defensive copy, in the order given; sorting is {@link SizeIndex}'s job.
 *
 * @param size  the size in bytes shared by every file in the group
 * @param files the files in the group, at least two
 */
public record CandidateGroup(long size, List<FileEntry> files) {

    public CandidateGroup {
        throw new UnsupportedOperationException("not implemented: DFS-001");
    }
}
