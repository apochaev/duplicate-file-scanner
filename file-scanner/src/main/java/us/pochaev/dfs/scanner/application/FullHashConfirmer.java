package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.infra.port.ContentHasher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stage 3 of the detection pipeline: confirms {@link CandidateGroup}s by a full SHA-256 hash of
 * each member and turns them into {@link DuplicateSet}s.
 * <p>
 * A pure function over the previous stage's finished output ({@link PartialHashFilter}): no
 * accumulation, nothing to merge. It holds no mutable state (only the hasher), so one instance
 * may serve several concurrent scans as long as the hasher is thread-safe.
 * <p>
 * Every group is fully hashed, whatever its size: groups at or under the sample threshold reach
 * this stage straight from {@link PartialHashFilter} unchanged. Members are hashed in ascending
 * {@link java.nio.file.Path#compareTo path order}; members with equal digests form one
 * {@link DuplicateSet} carrying the group's (scan-time) size. A member whose content cannot be
 * read ({@link java.io.IOException}) is dropped from its group without failing the rest of the
 * group; it is not reported until the progress listener exists (DFS-005). A member's current size
 * is not compared with the scan's: it is hashed as it is now.
 */
public final class FullHashConfirmer {

    private final ContentHasher hasher;

    /**
     * @throws NullPointerException if {@code hasher} is null
     */
    public FullHashConfirmer(ContentHasher hasher) {
        this.hasher = Objects.requireNonNull(hasher, "hasher");
    }

    /**
     * Confirms {@code groups}. Output order: each input group is replaced, in place, by the sets
     * it splits into (none, one or several), those ordered by their first (smallest) path; files
     * inside every set are in ascending path order. Any digest shared by fewer than two members
     * produces no set. The returned list is immutable.
     *
     * @throws NullPointerException if {@code groups} is null or contains null
     */
    public List<DuplicateSet> confirm(List<CandidateGroup> groups) {
        Objects.requireNonNull(groups, "groups");
        List<DuplicateSet> result = new ArrayList<>();
        for (CandidateGroup group : groups) {
            Objects.requireNonNull(group, "group");
            result.addAll(splitByFullHash(group));
        }
        return List.copyOf(result);
    }

    private List<DuplicateSet> splitByFullHash(CandidateGroup group) {
        List<FileEntry> sorted = new ArrayList<>(group.files());
        sorted.sort((a, b) -> a.path().compareTo(b.path()));

        Map<String, List<FileEntry>> byDigest = new LinkedHashMap<>();
        for (FileEntry file : sorted) {
            String digest;
            try {
                digest = hasher.fullHash(file.path());
            } catch (IOException e) {
                // Dropped without failing the rest of the group (DFS-003.AC-7).
                continue;
            }
            byDigest.computeIfAbsent(digest, k -> new ArrayList<>()).add(file);
        }

        List<DuplicateSet> sets = new ArrayList<>();
        for (Map.Entry<String, List<FileEntry>> entry : byDigest.entrySet()) {
            List<FileEntry> members = entry.getValue();
            if (members.size() >= 2) {
                sets.add(new DuplicateSet(group.size(), entry.getKey(), members));
            }
        }
        return sets;
    }
}
