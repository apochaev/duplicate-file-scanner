package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.PhysicalCopy;
import us.pochaev.dfs.scanner.domain.model.SkipReason;
import us.pochaev.dfs.scanner.domain.model.WalkEvent;
import us.pochaev.dfs.scanner.infra.port.FileIdentity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Stage 4 of the detection pipeline (DFS-004): groups the paths of each confirmed
 * {@link DuplicateSet} into {@link PhysicalCopy physical copies}, so hard links to one file count
 * as one copy and never as duplicates of each other (CLAUDE.md: "hard links are not duplicates").
 * <p>
 * A pure function over the previous stage's finished output ({@link FullHashConfirmer}): no
 * accumulation, nothing to merge. It holds no mutable state (only the {@link FileIdentity}), so
 * one instance may serve several concurrent scans as long as the port is thread-safe.
 * <p>
 * Per set (DFS-026): files are taken in ascending {@link java.nio.file.Path#compareTo path order};
 * the port is asked {@code identity.key(path)} exactly once per file, and files with equal keys
 * form one copy, so a set of n files costs n lookups whatever its copies and links (never a
 * pairwise comparison). Copies are ordered by their first link (the first file, in path order,
 * with that key), never by hash-map iteration order. The incoming {@link DuplicateSet#copies()} are
 * ignored and re-derived from {@link DuplicateSet#files()}. A file whose lookup throws
 * {@link java.io.IOException} is dropped from its set and reported to {@code onDrop}. A set left
 * with fewer than two copies is dropped.
 */
public final class HardLinkDetector {

    private final FileIdentity identity;
    private final Consumer<WalkEvent.Skipped> onDrop;

    /**
     * @throws NullPointerException if {@code identity} is null
     */
    public HardLinkDetector(FileIdentity identity) {
        this(identity, skipped -> { });
    }

    /**
     * Creates a detector that also reports each file it drops on an {@link IOException} to
     * {@code onDrop}, as {@code Skipped(path, UNREADABLE, "hard-link check failed: <message>")}
     * (DFS-005.AC-3).
     *
     * @throws NullPointerException if {@code identity} or {@code onDrop} is null
     */
    public HardLinkDetector(FileIdentity identity, Consumer<WalkEvent.Skipped> onDrop) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.onDrop = Objects.requireNonNull(onDrop, "onDrop");
    }

    /**
     * Regroups {@code sets} into physical copies. Output order: sets keep their input order, a
     * dropped set simply removed; within a set, {@code files} ascending by path, copies ordered by
     * their first link, links inside each copy ascending. Every returned set keeps its input
     * {@code size} and {@code digest}. The returned list is immutable.
     *
     * @throws NullPointerException if {@code sets} is null or contains null
     */
    public List<DuplicateSet> detect(List<DuplicateSet> sets) {
        Objects.requireNonNull(sets, "sets");
        List<DuplicateSet> result = new ArrayList<>();
        for (DuplicateSet set : sets) {
            Objects.requireNonNull(set, "set");
            DuplicateSet regrouped = regroup(set);
            if (regrouped != null) {
                result.add(regrouped);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Regroups one set by identity key: one {@code identity.key} call per file, in ascending path
     * order; equal keys form one copy; copies in order of their first link; a file whose lookup
     * throws {@link IOException} is reported to {@code onDrop} as
     * {@code Skipped(path, UNREADABLE, "hard-link check failed: " + PartialHashFilter.detail(e))}
     * and left out; returns null when fewer than two copies remain.
     */
    private DuplicateSet regroup(DuplicateSet set) {
        List<FileEntry> files = new ArrayList<>(set.files());
        files.sort(Comparator.comparing(FileEntry::path));

        // Filled in path order: insertion order is first-link order, so hash order never leaks.
        Map<Object, List<FileEntry>> copies = new LinkedHashMap<>();
        for (FileEntry file : files) {
            Object key;
            try {
                key = Objects.requireNonNull(identity.key(file.path()), "key");
            } catch (IOException e) {
                onDrop.accept(new WalkEvent.Skipped(file.path(), SkipReason.UNREADABLE,
                        "hard-link check failed: " + PartialHashFilter.detail(e)));
                continue;
            }
            copies.computeIfAbsent(key, k -> new ArrayList<>()).add(file);
        }

        if (copies.size() < 2) {
            return null;
        }

        List<FileEntry> survivingFiles = new ArrayList<>();
        List<PhysicalCopy> physicalCopies = new ArrayList<>(copies.size());
        for (List<FileEntry> links : copies.values()) {
            physicalCopies.add(new PhysicalCopy(List.copyOf(links)));
            survivingFiles.addAll(links);
        }
        survivingFiles.sort(Comparator.comparing(FileEntry::path));

        return new DuplicateSet(set.size(), set.digest(), survivingFiles, physicalCopies);
    }
}
