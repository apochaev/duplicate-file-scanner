package us.pochaev.dfs.server.analysis;

import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.FolderTotal;
import us.pochaev.dfs.scanner.domain.model.PhysicalCopy;
import us.pochaev.dfs.scanner.domain.model.ScanResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The folder groups of one completed scan (DFS-067; docs/design/folder-groups.md G1, G3, G6): a structure of its own,
 * next to {@link OverlapAnalysis} and independent of it (decision 4). Built once by {@link #of}; immutable, so any
 * thread may read it.
 * <p>
 * For each duplicate set, its folders are the normalized parents of its link paths, each with {@code copiesEach}: the
 * number of the set's physical copies that have a path there (a copy with two links in one folder counts once; a copy
 * with links in several folders appears in each of them, and still counts once in {@code copies} and in the reclaimable
 * figure). Sets with equal folder lists, path and {@code copiesEach} for path, form one {@link FolderGroup}. One pass
 * over the sets, one map entry per set; {@code otherGroups} of a folder is the number of groups listing it, minus one.
 * Paths are compared with the platform's {@link java.nio.file.Path} rules.
 * <p>
 * Order of {@link #groups()} (G3): {@code reclaimableBytes} descending, then the first folder's path, then the folder
 * lists compared folder by folder (path, then {@code copiesEach}; a list that is a prefix of another comes first).
 */
public final class FolderGroups {

    private static final int LISTED_SETS = 20;

    private final List<FolderGroup> groups;

    private FolderGroups(List<FolderGroup> groups) {
        this.groups = List.copyOf(groups);
    }

    /** Groups {@code result}'s duplicate sets once. */
    public static FolderGroups of(ScanResult result) {
        Map<Key, Acc> accs = new HashMap<>();
        for (DuplicateSet set : result.sets()) {
            Map<Path, long[]> perFolder = new TreeMap<>();
            for (PhysicalCopy copy : set.copies()) {
                Set<Path> seen = new HashSet<>();
                for (FileEntry link : copy.links()) {
                    Path parent = link.path().getParent();
                    if (parent == null) {
                        continue;
                    }
                    parent = parent.normalize();
                    long[] f = perFolder.computeIfAbsent(parent, k -> new long[2]);
                    if (seen.add(parent)) {
                        f[0]++;
                    }
                    f[1]++;
                }
            }
            if (perFolder.isEmpty()) {
                continue;
            }
            List<Path> folders = new ArrayList<>(perFolder.size());
            List<Integer> each = new ArrayList<>(perFolder.size());
            for (Map.Entry<Path, long[]> e : perFolder.entrySet()) {
                folders.add(e.getKey());
                each.add((int) e.getValue()[0]);
            }
            Acc acc = accs.computeIfAbsent(new Key(folders, each), k -> new Acc(k.folders.size()));
            long reclaimable = (set.copies().size() - 1L) * set.size();
            acc.sets++;
            acc.copies += set.copies().size();
            acc.reclaimable += reclaimable;
            int i = 0;
            for (long[] f : perFolder.values()) {
                acc.sharedFiles[i] += f[1];
                acc.sharedBytes[i] += f[1] * set.size();
                i++;
            }
            String name = set.files().isEmpty() ? "" : String.valueOf(set.files().get(0).path().getFileName());
            acc.listing.add(new Listed(new GroupSet(set.digest(), name, set.size(), set.copies().size()),
                    reclaimable));
        }

        Map<Path, Integer> groupsPerFolder = new HashMap<>();
        for (Key key : accs.keySet()) {
            for (Path folder : key.folders) {
                groupsPerFolder.merge(folder, 1, Integer::sum);
            }
        }
        Map<Path, FolderTotal> totals = new HashMap<>();
        for (FolderTotal t : result.folders()) {
            totals.put(t.folder().normalize(), t);
        }

        List<Map.Entry<Key, Acc>> entries = new ArrayList<>(accs.entrySet());
        entries.sort(Comparator
                .<Map.Entry<Key, Acc>>comparingLong(e -> -e.getValue().reclaimable)
                .thenComparing(e -> e.getKey().folders.get(0))
                .thenComparing((a, b) -> compareLists(a.getKey(), b.getKey())));
        List<FolderGroup> out = new ArrayList<>(entries.size());
        for (Map.Entry<Key, Acc> e : entries) {
            Key key = e.getKey();
            Acc acc = e.getValue();
            List<GroupFolder> folders = new ArrayList<>(key.folders.size());
            for (int i = 0; i < key.folders.size(); i++) {
                Path folder = key.folders.get(i);
                FolderTotal t = totals.get(folder);
                folders.add(new GroupFolder(folder, key.each.get(i), acc.sharedFiles[i], acc.sharedBytes[i],
                        t == null ? 0 : t.files(), t == null ? 0 : t.bytes(), groupsPerFolder.get(folder) - 1));
            }
            acc.listing.sort(Comparator.<Listed>comparingLong(l -> -l.reclaimable)
                    .thenComparing(l -> l.set.digest()));
            List<GroupSet> sets = new ArrayList<>();
            for (int i = 0; i < Math.min(LISTED_SETS, acc.listing.size()); i++) {
                sets.add(acc.listing.get(i).set);
            }
            out.add(new FolderGroup(acc.sets, acc.copies, acc.reclaimable, folders, sets, acc.sets));
        }
        return new FolderGroups(out);
    }

    private static int compareLists(Key a, Key b) {
        int n = Math.min(a.folders.size(), b.folders.size());
        for (int i = 0; i < n; i++) {
            int c = a.folders.get(i).compareTo(b.folders.get(i));
            if (c != 0) {
                return c;
            }
            c = Integer.compare(a.each.get(i), b.each.get(i));
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(a.folders.size(), b.folders.size());
    }

    /** Every group, in the fixed order. */
    public List<FolderGroup> groups() {
        return groups;
    }

    private record Key(List<Path> folders, List<Integer> each) {
    }

    private record Listed(GroupSet set, long reclaimable) {
    }

    private static final class Acc {
        int sets;
        long copies;
        long reclaimable;
        final long[] sharedFiles;
        final long[] sharedBytes;
        final List<Listed> listing = new ArrayList<>();

        Acc(int folders) {
            sharedFiles = new long[folders];
            sharedBytes = new long[folders];
        }
    }
}
