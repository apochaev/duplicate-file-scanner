package us.pochaev.dfs.cli;

import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.PhysicalCopy;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.model.WalkEvent;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The human-readable scan report written by {@code --format=text} (DFS-023): a two-line header
 * saying what was scanned, when and with which settings (DFS-030 decision 8), then one entry per
 * duplicate set, largest reclaimable space first, then the skipped paths, then a summary line.
 * Formatting is locale-independent and adds only ASCII, so tests can match it exactly.
 */
final class TextReport {

    private static final String[] UNITS = {"KB", "MB", "GB", "TB"};
    private static final int DIGEST_CHARS = 12;

    /** Sets by reclaimable bytes, largest first; ties by larger size, then by first copy path (decision 4). */
    private static final Comparator<Entry> ORDER = Comparator
            .comparingLong(Entry::reclaimable).reversed()
            .thenComparing(Comparator.comparingLong(Entry::size).reversed())
            .thenComparing(entry -> entry.copies().getFirst().getFirst());

    private TextReport() {
    }

    static void write(ScanResult scan, ScanConfig config, Instant startedAt, Instant finishedAt, PrintStream out) {
        for (String text : header(scan.roots(), config, startedAt, finishedAt)) {
            line(out, text);
        }
        List<Entry> entries = scan.sets().stream().map(Entry::of).sorted(ORDER).toList();
        long files = 0;
        long reclaimable = 0;
        if (entries.isEmpty()) {
            line(out, "No duplicates found.");
        }
        int n = 0;
        for (Entry entry : entries) {
            line(out, "#" + ++n + "  " + size(entry.reclaimable()) + " reclaimable  (" + count(entry.copies().size())
                    + " copies x " + size(entry.size()) + ", sha256 " + shortDigest(entry.digest()) + ")");
            for (List<Path> links : entry.copies()) {
                line(out, "    " + links.getFirst());
                for (Path link : links.subList(1, links.size())) {
                    line(out, "      (hard link) " + link);
                }
                files += links.size();
            }
            reclaimable += entry.reclaimable();
        }
        if (!scan.skipped().isEmpty()) {
            line(out, "Skipped (" + count(scan.skipped().size()) + "):");
            for (WalkEvent.Skipped skipped : scan.skipped()) {
                line(out, "    " + skipped.path() + "  " + skipped.reason().name() + "  " + skipped.detail());
            }
        }
        line(out, "Summary: " + count(entries.size()) + " sets, " + count(files) + " files, "
                + size(reclaimable) + " reclaimable, " + count(scan.skipped().size()) + " skipped, "
                + count(scan.excluded()) + " excluded");
        out.flush();
    }

    /**
     * The report's two header lines (DFS-030 decision 8): {@code Scan of <roots joined by ", ">},
     * then {@code "  <start> to <finish>, minimum size <size(minSize)>, default excludes on|off"}.
     * Times are UTC as {@code yyyy-MM-dd HH:mm:ssZ}; the finish drops its date when it falls on the
     * start's date ({@code 2026-10-31 09:12:03Z to 11:47:55Z}), and keeps it otherwise.
     */
    static List<String> header(List<Path> roots, ScanConfig config, Instant startedAt, Instant finishedAt) {
        throw new UnsupportedOperationException("not implemented: DFS-030");
    }

    /** Bytes in 1024-based units with one decimal, rounded half-up; below 1024 a whole number of B. */
    static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int unit = 0;
        long divisor = 1024;
        while (unit < UNITS.length - 1 && bytes / divisor >= 1024) {
            unit++;
            divisor *= 1024;
        }
        BigDecimal value = BigDecimal.valueOf(bytes).divide(BigDecimal.valueOf(divisor), 1, RoundingMode.HALF_UP);
        return value.toPlainString() + " " + UNITS[unit];
    }

    /** A count with {@code ,} thousands separators, whatever the default locale. */
    static String count(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static String shortDigest(String digest) {
        return digest.length() <= DIGEST_CHARS ? digest : digest.substring(0, DIGEST_CHARS);
    }

    private static void line(PrintStream out, String text) {
        out.print(text);
        out.print('\n');
    }

    /** A set as the report shows it: copies and their links in path order. */
    private record Entry(long size, String digest, long reclaimable, List<List<Path>> copies) {

        static Entry of(DuplicateSet set) {
            List<List<Path>> copies = set.copies().stream()
                    .map(TextReport.Entry::links)
                    .sorted(Comparator.comparing(List::getFirst))
                    .toList();
            return new Entry(set.size(), set.digest(), set.size() * (copies.size() - 1), copies);
        }

        private static List<Path> links(PhysicalCopy copy) {
            return copy.links().stream().map(FileEntry::path).sorted().toList();
        }
    }
}
