package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.WalkEvent;
import us.pochaev.dfs.scanner.infra.port.ContentHasher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Stage 2 of the detection pipeline: splits {@link CandidateGroup}s by a head-and-tail sample
 * hash, so the full hash (DFS-003) is only spent on files that still look identical.
 * <p>
 * A pure function over the previous stage's finished output: no accumulation, nothing to merge.
 * It holds no mutable state (only the hasher and its two settings), so one instance may serve
 * several concurrent scans as long as the hasher is thread-safe.
 * <p>
 * A group whose size is at or under the skip threshold bypasses the sample entirely: the hasher
 * is never called for it and the group is returned unchanged. For a larger group, each member is
 * sampled in ascending {@link java.nio.file.Path#compareTo path order}; members with equal sample
 * digests form a new {@link CandidateGroup}. A member whose sample cannot be read
 * ({@link java.io.IOException}) is dropped from its group without failing the rest of the group;
 * it is not reported until the progress listener exists (DFS-005).
 */
public final class PartialHashFilter {

    /** Default number of bytes sampled from each end of a file: 64 KB. */
    public static final int DEFAULT_SAMPLE_SIZE = 64 * 1024;

    /** Default size in bytes at or under which the sample is skipped: 128 KB. */
    public static final long DEFAULT_SKIP_THRESHOLD = 128L * 1024;

    private final ContentHasher hasher;
    private final int sampleSize;
    private final long skipThreshold;
    private final Consumer<WalkEvent.Skipped> onDrop;

    /**
     * Creates a filter with the default sample size ({@value #DEFAULT_SAMPLE_SIZE} bytes) and
     * skip threshold ({@value #DEFAULT_SKIP_THRESHOLD} bytes).
     *
     * @throws NullPointerException if {@code hasher} is null
     */
    public PartialHashFilter(ContentHasher hasher) {
        this(hasher, DEFAULT_SAMPLE_SIZE, DEFAULT_SKIP_THRESHOLD);
    }

    /**
     * Creates a filter that samples {@code sampleSize} bytes from each end of every file larger
     * than {@code skipThreshold} bytes.
     *
     * @throws NullPointerException     if {@code hasher} is null
     * @throws IllegalArgumentException if {@code sampleSize} is not positive or
     *                                  {@code skipThreshold} is negative
     */
    public PartialHashFilter(ContentHasher hasher, int sampleSize, long skipThreshold) {
        this(hasher, sampleSize, skipThreshold, skipped -> { });
    }

    /**
     * Creates a filter like {@link #PartialHashFilter(ContentHasher, int, long)} that also reports
     * each member it drops on an {@link IOException} to {@code onDrop}, as
     * {@code Skipped(path, UNREADABLE, "partial hash failed: <message>")} (DFS-005.AC-3).
     *
     * @throws NullPointerException     if {@code hasher} or {@code onDrop} is null
     * @throws IllegalArgumentException if {@code sampleSize} is not positive or
     *                                  {@code skipThreshold} is negative
     */
    public PartialHashFilter(ContentHasher hasher, int sampleSize, long skipThreshold,
                             Consumer<WalkEvent.Skipped> onDrop) {
        Objects.requireNonNull(hasher, "hasher");
        Objects.requireNonNull(onDrop, "onDrop");
        if (sampleSize <= 0) {
            throw new IllegalArgumentException("sampleSize must be positive: " + sampleSize);
        }
        if (skipThreshold < 0) {
            throw new IllegalArgumentException("skipThreshold must not be negative: " + skipThreshold);
        }
        this.hasher = hasher;
        this.sampleSize = sampleSize;
        this.skipThreshold = skipThreshold;
        this.onDrop = onDrop;
    }

    /**
     * Filters {@code groups}. Output order: each input group is replaced, in place, by the groups
     * it splits into (none, one or several), those ordered by their first (smallest) path; members
     * inside every output group are in ascending path order. A group bypassing the sample is
     * passed through as the same instance. Any group left with fewer than two members is dropped.
     * The returned list is immutable.
     *
     * @throws NullPointerException if {@code groups} is null or contains null
     */
    public List<CandidateGroup> filter(List<CandidateGroup> groups) {
        Objects.requireNonNull(groups, "groups");
        List<CandidateGroup> result = new ArrayList<>();
        for (CandidateGroup group : groups) {
            Objects.requireNonNull(group, "group");
            if (group.size() <= skipThreshold) {
                result.add(group);
                continue;
            }
            result.addAll(splitBySample(group));
        }
        return List.copyOf(result);
    }

    private List<CandidateGroup> splitBySample(CandidateGroup group) {
        List<FileEntry> sorted = new ArrayList<>(group.files());
        sorted.sort((a, b) -> a.path().compareTo(b.path()));

        Map<String, List<FileEntry>> byDigest = new LinkedHashMap<>();
        for (FileEntry file : sorted) {
            String digest;
            try {
                digest = hasher.sampleHash(file.path(), sampleSize);
            } catch (IOException e) {
                // Dropped without failing the rest of the group (DFS-002.AC-7).
                onDrop.accept(new WalkEvent.Skipped(file.path(),
                        us.pochaev.dfs.scanner.domain.model.SkipReason.UNREADABLE,
                        "partial hash failed: " + detail(e)));
                continue;
            }
            byDigest.computeIfAbsent(digest, k -> new ArrayList<>()).add(file);
        }

        List<CandidateGroup> subgroups = new ArrayList<>();
        for (List<FileEntry> members : byDigest.values()) {
            if (members.size() >= 2) {
                subgroups.add(new CandidateGroup(group.size(), members));
            }
        }
        return subgroups;
    }

    static String detail(IOException e) {
        String message = e.getMessage();
        return message != null ? message : e.getClass().getSimpleName();
    }
}
