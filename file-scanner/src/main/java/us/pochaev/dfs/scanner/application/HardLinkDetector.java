package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.PhysicalCopy;
import us.pochaev.dfs.scanner.infra.port.FileIdentity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Stage 4 of the detection pipeline (DFS-004): groups the paths of each confirmed
 * {@link DuplicateSet} into {@link PhysicalCopy physical copies}, so hard links to one file count
 * as one copy and never as duplicates of each other (CLAUDE.md: "hard links are not duplicates").
 * <p>
 * A pure function over the previous stage's finished output ({@link FullHashConfirmer}): no
 * accumulation, nothing to merge. It holds no mutable state (only the {@link FileIdentity}), so
 * one instance may serve several concurrent scans as long as the port is thread-safe.
 * <p>
 * Per set: files are taken in ascending {@link java.nio.file.Path#compareTo path order}; each is
 * checked with {@code identity.isSameFile(file, firstLink)} against the first link of each copy
 * formed so far, in copy order, and joins the first copy it matches, or starts a new copy. The
 * incoming {@link DuplicateSet#copies()} are ignored and re-derived from {@link DuplicateSet#files()}.
 * A file whose check throws {@link java.io.IOException} is dropped from its set (not reported until
 * DFS-005's listener exists). A set left with fewer than two copies is dropped.
 */
public final class HardLinkDetector {

    private final FileIdentity identity;

    /**
     * @throws NullPointerException if {@code identity} is null
     */
    public HardLinkDetector(FileIdentity identity) {
        this.identity = Objects.requireNonNull(identity, "identity");
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

    private DuplicateSet regroup(DuplicateSet set) {
        List<FileEntry> files = new ArrayList<>(set.files());
        files.sort(Comparator.comparing(FileEntry::path));

        List<List<FileEntry>> copies = new ArrayList<>();
        for (FileEntry file : files) {
            List<FileEntry> matchedCopy = null;
            boolean dropped = false;
            for (List<FileEntry> copy : copies) {
                FileEntry firstLink = copy.get(0);
                try {
                    if (identity.isSameFile(file.path(), firstLink.path())) {
                        matchedCopy = copy;
                        break;
                    }
                } catch (IOException e) {
                    dropped = true;
                    break;
                }
            }
            if (dropped) {
                continue;
            }
            if (matchedCopy != null) {
                matchedCopy.add(file);
            } else {
                List<FileEntry> newCopy = new ArrayList<>();
                newCopy.add(file);
                copies.add(newCopy);
            }
        }

        if (copies.size() < 2) {
            return null;
        }

        List<FileEntry> survivingFiles = new ArrayList<>();
        List<PhysicalCopy> physicalCopies = new ArrayList<>();
        for (List<FileEntry> copy : copies) {
            physicalCopies.add(new PhysicalCopy(List.copyOf(copy)));
            survivingFiles.addAll(copy);
        }
        survivingFiles.sort(Comparator.comparing(FileEntry::path));

        return new DuplicateSet(set.size(), set.digest(), survivingFiles, physicalCopies);
    }
}
