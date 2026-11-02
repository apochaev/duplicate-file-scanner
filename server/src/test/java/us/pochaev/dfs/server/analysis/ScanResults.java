package us.pochaev.dfs.server.analysis;

import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.FolderTotal;
import us.pochaev.dfs.scanner.domain.model.PhysicalCopy;
import us.pochaev.dfs.scanner.domain.model.ScanResult;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds {@link ScanResult} values in memory, without touching disk. Paths are written {@code /}-separated from the
 * file system root ({@code "/r/A/x.jpg"}); on Windows they land under the current drive's root, so they are absolute.
 * Folder totals are derived from every file added, as the scan would count them.
 */
final class ScanResults {

    static final long MB = 1024L * 1024L;
    private static final Path FS_ROOT = Path.of("/").toAbsolutePath();
    private static final Instant MODIFIED = Instant.parse("2026-01-01T00:00:00Z");

    private final List<Path> roots = new ArrayList<>();
    private final List<FileEntry> allFiles = new ArrayList<>();
    private final List<DuplicateSet> sets = new ArrayList<>();
    private int digests;

    private ScanResults(String... roots) {
        for (String root : roots) {
            this.roots.add(p(root));
        }
    }

    /** A builder for a scan of {@code /r}. */
    static ScanResults underR() {
        return new ScanResults("/r");
    }

    /** {@code "/r/A"} as an absolute platform path. */
    static Path p(String slashPath) {
        if (!slashPath.startsWith("/")) {
            throw new IllegalArgumentException("write paths from the root: " + slashPath);
        }
        return FS_ROOT.resolve(slashPath.substring(1)).normalize();
    }

    /** One set in which every path is its own physical copy. */
    ScanResults set(long size, String... paths) {
        List<List<String>> copies = new ArrayList<>();
        for (String path : paths) {
            copies.add(List.of(path));
        }
        return linkedSet(size, copies);
    }

    /** One set whose physical copies are given as lists of hard-linked paths. */
    ScanResults linkedSet(long size, List<List<String>> copies) {
        List<FileEntry> files = new ArrayList<>();
        List<PhysicalCopy> physical = new ArrayList<>();
        for (List<String> links : copies) {
            List<FileEntry> entries = links.stream().map(link -> new FileEntry(p(link), size, MODIFIED)).toList();
            files.addAll(entries);
            physical.add(new PhysicalCopy(entries));
        }
        files.sort(Comparator.comparing(FileEntry::path));
        allFiles.addAll(files);
        sets.add(new DuplicateSet(size, "%064x".formatted(++digests), files, physical));
        return this;
    }

    /** Files with no duplicate, counted only in their folders' totals. */
    ScanResults unique(long size, String... paths) {
        for (String path : paths) {
            allFiles.add(new FileEntry(p(path), size, MODIFIED));
        }
        return this;
    }

    ScanResult build() {
        Map<Path, long[]> totals = new TreeMap<>();
        for (FileEntry file : allFiles) {
            long[] total = totals.computeIfAbsent(file.path().getParent(), folder -> new long[2]);
            total[0]++;
            total[1] += file.size();
        }
        List<FolderTotal> folders = new ArrayList<>();
        totals.forEach((folder, total) -> folders.add(new FolderTotal(folder, total[0], total[1])));
        List<Path> sortedRoots = new ArrayList<>(roots);
        sortedRoots.sort(null);
        return new ScanResult(sortedRoots, folders, sets, List.of(), 0);
    }

    /** How many files have been added so far (generated results check their own size with it). */
    int fileCount() {
        return allFiles.size();
    }
}
