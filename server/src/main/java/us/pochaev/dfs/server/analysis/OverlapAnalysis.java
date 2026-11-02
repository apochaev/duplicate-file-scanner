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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The folder overlap analysis of one completed scan (docs/design/folder-overlap-analysis.md; docs/design/ui.md U5b).
 * Computed once by {@link #of}; immutable, so any thread may read it.
 * <p>
 * A file is a path, as {@code FolderTotal.files} counts it. A copy of a file is a <em>different</em> physical copy of
 * its set, so hard links never count (CLAUDE.md, "Hard links are not duplicates"). Paths are normalized and compared
 * with the platform's {@link Path} rules (case-insensitive on Windows).
 * <p>
 * Per set, every folder from a link's parent up to its root is visited once per link, and pairs are formed between the
 * immediate folders the set spans, so a set spread over n folders costs n(n&minus;1)/2 pair updates: the analysis'
 * inherent size. Ancestor pairs are never precomputed; {@link #pair} answers them on demand (decision 3).
 */
public final class OverlapAnalysis {

    private static final Comparator<FolderSummary> SUMMARY_ORDER = Comparator
            .comparingLong(FolderSummary::bytesWithCopyElsewhere).reversed()
            .thenComparing(Comparator.comparingLong(FolderSummary::internalBytes).reversed())
            .thenComparing(FolderSummary::folder);

    private static final Comparator<FolderPair> PAIR_ORDER = Comparator
            .comparingLong((FolderPair p) -> Math.max(p.a().bytesWithCopy(), p.b().bytesWithCopy())).reversed()
            .thenComparing((FolderPair p) -> p.a().folder())
            .thenComparing((FolderPair p) -> p.b().folder());

    private static final long[] NO_TOTAL = {0, 0};

    private final List<Path> roots;
    private final Map<Path, long[]> subtreeTotals;
    private final Map<Path, FolderSummary> summariesByFolder;
    private final List<FolderSummary> summaries;
    private final List<FolderPair> pairs;
    private final List<IndexedSet> sets;

    private OverlapAnalysis(List<Path> roots, Map<Path, long[]> subtreeTotals,
                            Map<Path, FolderSummary> summariesByFolder, List<FolderSummary> summaries,
                            List<FolderPair> pairs, List<IndexedSet> sets) {
        this.roots = roots;
        this.subtreeTotals = subtreeTotals;
        this.summariesByFolder = summariesByFolder;
        this.summaries = summaries;
        this.pairs = pairs;
        this.sets = sets;
    }

    /** Analyses {@code result} once. */
    public static OverlapAnalysis of(ScanResult result) {
        Objects.requireNonNull(result, "result");
        List<Path> roots = result.roots().stream().map(Path::normalize).toList();

        Map<Path, long[]> directTotals = new HashMap<>();
        Map<Path, long[]> subtreeTotals = new HashMap<>();
        for (FolderTotal total : result.folders()) {
            Path folder = total.folder().normalize();
            Path root = rootOf(roots, folder);
            if (root == null) {
                continue;
            }
            add(directTotals, folder, total.files(), total.bytes());
            for (Path p = folder; p != null; p = p.equals(root) ? null : p.getParent()) {
                add(subtreeTotals, p, total.files(), total.bytes());
            }
        }

        List<IndexedSet> sets = new ArrayList<>(result.sets().size());
        Map<Path, long[]> stats = new HashMap<>();
        Map<PairKey, long[]> pairCounts = new HashMap<>();
        for (DuplicateSet set : result.sets()) {
            IndexedSet indexed = IndexedSet.of(set);
            sets.add(indexed);
            countSubtrees(roots, indexed, stats);
            countPairs(indexed, pairCounts);
        }

        Map<Path, FolderSummary> summariesByFolder = new HashMap<>();
        List<FolderSummary> summaries = new ArrayList<>();
        stats.forEach((folder, s) -> {
            long[] total = subtreeTotals.getOrDefault(folder, NO_TOTAL);
            FolderSummary summary = new FolderSummary(folder, total[0], total[1], s[0], s[1], s[2], s[3]);
            summariesByFolder.put(folder, summary);
            if (s[0] > 0 || s[2] > 0) {
                summaries.add(summary);
            }
        });
        summaries.sort(SUMMARY_ORDER);

        List<FolderPair> pairs = new ArrayList<>();
        pairCounts.forEach((key, c) -> {
            if (c[0] == 0 && c[2] == 0) {
                return;
            }
            long[] totalA = directTotals.getOrDefault(key.a(), NO_TOTAL);
            long[] totalB = directTotals.getOrDefault(key.b(), NO_TOTAL);
            pairs.add(new FolderPair(
                    new PairSide(key.a(), c[0], totalA[0], c[1], totalA[1]),
                    new PairSide(key.b(), c[2], totalB[0], c[3], totalB[1])));
        });
        pairs.sort(PAIR_ORDER);

        return new OverlapAnalysis(roots, Map.copyOf(subtreeTotals), Map.copyOf(summariesByFolder),
                List.copyOf(summaries), List.copyOf(pairs), List.copyOf(sets));
    }

    /** Every folder with a file with a copy elsewhere or an internal duplicate, in the fixed order. */
    public List<FolderSummary> summaries() {
        return summaries;
    }

    /** The summary of any folder from a root down; empty for a folder outside every root. */
    public Optional<FolderSummary> summary(Path folder) {
        Path normalized = Objects.requireNonNull(folder, "folder").normalize();
        if (rootOf(roots, normalized) == null) {
            return Optional.empty();
        }
        FolderSummary known = summariesByFolder.get(normalized);
        if (known != null) {
            return Optional.of(known);
        }
        long[] total = subtreeTotals.getOrDefault(normalized, NO_TOTAL);
        return Optional.of(new FolderSummary(normalized, total[0], total[1], 0, 0, 0, 0));
    }

    /** Every pair of immediate folders with a non-zero side, in the fixed order. */
    public List<FolderPair> pairs() {
        return pairs;
    }

    /**
     * One pair for any two folders, each counted recursively, in either argument order.
     *
     * @throws IllegalArgumentException if the folders are equal, one contains the other, or either lies outside
     *                                  every root
     */
    public FolderPair pair(Path x, Path y) {
        Path nx = Objects.requireNonNull(x, "x").normalize();
        Path ny = Objects.requireNonNull(y, "y").normalize();
        if (rootOf(roots, nx) == null || rootOf(roots, ny) == null) {
            throw new IllegalArgumentException("folder outside every scanned root: " + x + ", " + y);
        }
        if (nx.startsWith(ny) || ny.startsWith(nx)) {
            throw new IllegalArgumentException("folders are equal or one contains the other: " + x + ", " + y);
        }
        boolean xFirst = nx.compareTo(ny) < 0;
        Path a = xFirst ? nx : ny;
        Path b = xFirst ? ny : nx;

        long filesA = 0;
        long bytesA = 0;
        long filesB = 0;
        long bytesB = 0;
        for (IndexedSet set : sets) {
            Map<Integer, Integer> inA = null;
            Map<Integer, Integer> inB = null;
            for (int i = 0; i < set.paths.length; i++) {
                Path path = set.paths[i];
                if (path.startsWith(a)) {
                    inA = increment(inA, set.copyOf[i]);
                } else if (path.startsWith(b)) {
                    inB = increment(inB, set.copyOf[i]);
                }
            }
            if (inA == null || inB == null) {
                continue;
            }
            long withCopyA = filesWithCopyIn(inA, inB);
            long withCopyB = filesWithCopyIn(inB, inA);
            filesA += withCopyA;
            bytesA += withCopyA * set.size;
            filesB += withCopyB;
            bytesB += withCopyB * set.size;
        }
        long[] totalA = subtreeTotals.getOrDefault(a, NO_TOTAL);
        long[] totalB = subtreeTotals.getOrDefault(b, NO_TOTAL);
        return new FolderPair(new PairSide(a, filesA, totalA[0], bytesA, totalA[1]),
                new PairSide(b, filesB, totalB[0], bytesB, totalB[1]));
    }

    /**
     * For every folder from each link's parent up to its root: how many links of each copy lie inside it. A file there
     * has a copy elsewhere when a different copy has a link outside the folder; a set whose k copies reach inside adds
     * k &minus; 1 internal duplicates.
     */
    private static void countSubtrees(List<Path> roots, IndexedSet set, Map<Path, long[]> stats) {
        Map<Path, Map<Integer, Integer>> inside = new HashMap<>();
        for (int i = 0; i < set.paths.length; i++) {
            Path folder = set.paths[i].getParent();
            Path root = folder == null ? null : rootOf(roots, folder);
            if (root == null) {
                continue;
            }
            int copy = set.copyOf[i];
            for (Path p = folder; p != null; p = p.equals(root) ? null : p.getParent()) {
                inside.computeIfAbsent(p, k -> new HashMap<>()).merge(copy, 1, Integer::sum);
            }
        }
        inside.forEach((folder, perCopy) -> {
            int fullyInside = 0;
            for (Map.Entry<Integer, Integer> e : perCopy.entrySet()) {
                if (e.getValue() == set.linksPerCopy[e.getKey()]) {
                    fullyInside++;
                }
            }
            // A copy with any link outside the folder survives deleting it.
            int outsideCopies = set.linksPerCopy.length - fullyInside;
            long elsewhere = 0;
            for (Map.Entry<Integer, Integer> e : perCopy.entrySet()) {
                boolean ownCopyFullyInside = e.getValue() == set.linksPerCopy[e.getKey()];
                if (outsideCopies >= 2 || (outsideCopies == 1 && ownCopyFullyInside)) {
                    elsewhere += e.getValue();
                }
            }
            long internal = perCopy.size() - 1;
            long[] s = stats.computeIfAbsent(folder, k -> new long[4]);
            s[0] += elsewhere;
            s[1] += elsewhere * set.size;
            s[2] += internal;
            s[3] += internal * set.size;
        });
    }

    /** Between every two immediate folders the set spans: each side's files with a different copy in the other. */
    private static void countPairs(IndexedSet set, Map<PairKey, long[]> pairCounts) {
        Map<Path, Map<Integer, Integer>> direct = new HashMap<>();
        for (int i = 0; i < set.paths.length; i++) {
            Path folder = set.paths[i].getParent();
            if (folder != null) {
                direct.computeIfAbsent(folder, k -> new HashMap<>()).merge(set.copyOf[i], 1, Integer::sum);
            }
        }
        if (direct.size() < 2) {
            return;
        }
        List<Path> folders = new ArrayList<>(direct.keySet());
        folders.sort(null);
        for (int i = 0; i < folders.size(); i++) {
            Map<Integer, Integer> inA = direct.get(folders.get(i));
            for (int j = i + 1; j < folders.size(); j++) {
                Map<Integer, Integer> inB = direct.get(folders.get(j));
                long withCopyA = filesWithCopyIn(inA, inB);
                long withCopyB = filesWithCopyIn(inB, inA);
                long[] c = pairCounts.computeIfAbsent(new PairKey(folders.get(i), folders.get(j)), k -> new long[4]);
                c[0] += withCopyA;
                c[1] += withCopyA * set.size;
                c[2] += withCopyB;
                c[3] += withCopyB * set.size;
            }
        }
    }

    /** Files counted in {@code mine} (links per copy) that have a different copy among {@code other}'s. */
    private static long filesWithCopyIn(Map<Integer, Integer> mine, Map<Integer, Integer> other) {
        long count = 0;
        for (Map.Entry<Integer, Integer> e : mine.entrySet()) {
            if (other.size() >= 2 || !other.containsKey(e.getKey())) {
                count += e.getValue();
            }
        }
        return count;
    }

    private static Map<Integer, Integer> increment(Map<Integer, Integer> counts, int copy) {
        Map<Integer, Integer> map = counts == null ? new HashMap<>() : counts;
        map.merge(copy, 1, Integer::sum);
        return map;
    }

    private static Path rootOf(List<Path> roots, Path path) {
        for (Path root : roots) {
            if (path.startsWith(root)) {
                return root;
            }
        }
        return null;
    }

    private static void add(Map<Path, long[]> totals, Path folder, long files, long bytes) {
        long[] total = totals.computeIfAbsent(folder, k -> new long[2]);
        total[0] += files;
        total[1] += bytes;
    }

    private record PairKey(Path a, Path b) {
    }

    /** One set flattened for repeated passes: each path with the index of its physical copy. */
    private static final class IndexedSet {
        final long size;
        final Path[] paths;
        final int[] copyOf;
        final int[] linksPerCopy;

        private IndexedSet(long size, Path[] paths, int[] copyOf, int[] linksPerCopy) {
            this.size = size;
            this.paths = paths;
            this.copyOf = copyOf;
            this.linksPerCopy = linksPerCopy;
        }

        static IndexedSet of(DuplicateSet set) {
            List<PhysicalCopy> copies = set.copies();
            int links = set.files().size();
            Path[] paths = new Path[links];
            int[] copyOf = new int[links];
            int[] linksPerCopy = new int[copies.size()];
            int i = 0;
            for (int c = 0; c < copies.size(); c++) {
                for (FileEntry link : copies.get(c).links()) {
                    paths[i] = link.path().normalize();
                    copyOf[i] = c;
                    i++;
                }
                linksPerCopy[c] = copies.get(c).links().size();
            }
            return new IndexedSet(set.size(), paths, copyOf, linksPerCopy);
        }
    }
}
