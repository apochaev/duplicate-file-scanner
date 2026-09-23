package us.pochaev.dfs.scanner.application;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import us.pochaev.dfs.scanner.domain.model.FileEntry;

/**
 * Stage 1 of the detection pipeline: accumulates {@link FileEntry} values and groups them by
 * size into {@link CandidateGroup}s, dropping every size seen only once.
 *
 * <p><b>Not thread-safe.</b> One instance belongs to one scan, or to one worker's partition of
 * a scan. Partial indexes are combined with {@link #merge(SizeIndex)}; singletons can only be
 * dropped once all partitions are merged, which is why dropping happens in {@link #groups()}
 * and not in {@link #add(FileEntry)}.
 *
 * <p>Entries smaller than the minimum size are ignored by {@link #add(FileEntry)}; the default
 * minimum is {@value #DEFAULT_MIN_SIZE}, so empty files are excluded.
 */
public final class SizeIndex {

    /** The default minimum size in bytes: empty files are excluded. */
    public static final long DEFAULT_MIN_SIZE = 1L;

    private final long minSize;
    private final Map<Long, List<FileEntry>> bySize = new HashMap<>();

    /** Creates an index with the default minimum size, {@value #DEFAULT_MIN_SIZE}. */
    public SizeIndex() {
        this(DEFAULT_MIN_SIZE);
    }

    /**
     * Creates an index that ignores entries smaller than {@code minSize} bytes.
     *
     * @throws IllegalArgumentException if {@code minSize} is negative
     */
    public SizeIndex(long minSize) {
        if (minSize < 0) {
            throw new IllegalArgumentException("minSize must not be negative: " + minSize);
        }
        this.minSize = minSize;
    }

    /**
     * Adds one entry; an entry below the minimum size is ignored.
     *
     * @throws NullPointerException if {@code entry} is null
     */
    public void add(FileEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (entry.size() < minSize) {
            return;
        }
        bySize.computeIfAbsent(entry.size(), size -> new ArrayList<>()).add(entry);
    }

    /**
     * Copies {@code other}'s entries into this index. {@code other} is left unchanged and shares
     * no mutable state with this index afterwards. Merging an index into itself changes nothing.
     *
     * @throws NullPointerException     if {@code other} is null
     * @throws IllegalArgumentException if {@code other} was built with a different minimum size
     */
    public void merge(SizeIndex other) {
        Objects.requireNonNull(other, "other");
        if (other.minSize != this.minSize) {
            throw new IllegalArgumentException(
                    "cannot merge indexes with different minimum sizes: " + this.minSize + " vs " + other.minSize);
        }
        if (other == this) {
            return;
        }
        for (Map.Entry<Long, List<FileEntry>> group : other.bySize.entrySet()) {
            bySize.computeIfAbsent(group.getKey(), size -> new ArrayList<>()).addAll(group.getValue());
        }
    }

    /**
     * Returns the candidate groups accumulated so far, as an immutable snapshot that later
     * {@code add}/{@code merge} calls do not affect. Groups are ordered by size, largest first;
     * files within a group are ordered by {@link java.nio.file.Path#compareTo} ascending. Equal
     * paths within a group collapse to one entry, the one with the latest modification time.
     * Any group left with fewer than two files is dropped. The result does not depend on the
     * order entries were added or merged in. The index itself is not cleared.
     */
    public List<CandidateGroup> groups() {
        List<CandidateGroup> result = new ArrayList<>();
        for (Map.Entry<Long, List<FileEntry>> group : bySize.entrySet()) {
            long size = group.getKey();
            List<FileEntry> sorted = new ArrayList<>(group.getValue());
            sorted.sort(Comparator.comparing(FileEntry::path));

            List<FileEntry> collapsed = new ArrayList<>();
            for (FileEntry entry : sorted) {
                if (!collapsed.isEmpty()) {
                    FileEntry last = collapsed.getLast();
                    if (last.path().equals(entry.path())) {
                        if (entry.lastModified().isAfter(last.lastModified())) {
                            collapsed.set(collapsed.size() - 1, entry);
                        }
                        continue;
                    }
                }
                collapsed.add(entry);
            }

            if (collapsed.size() >= 2) {
                result.add(new CandidateGroup(size, collapsed));
            }
        }

        result.sort(Comparator.comparingLong(CandidateGroup::size).reversed());
        return List.copyOf(result);
    }
}
