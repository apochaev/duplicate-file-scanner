package us.pochaev.dfs.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.PhysicalCopy;
import us.pochaev.dfs.scanner.domain.model.ScanResult;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link TextReport} on scan results built in the test (DFS-023 decision 10). */
class TextReportTest {

    private final Locale defaultLocale = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(defaultLocale);
    }

    @Test
    @DisplayName("DFS-023.AC-4 Sets are ordered by reclaimable bytes, then size, then first path")
    void setsAreOrdered() {
        ScanResult scan = new ScanResult(List.of(
                set("A", 1000, "/m/a2", "/m/a1"),
                set("B", 100, "/z/b1", "/z/b2", "/z/b3", "/z/b4"),
                set("C", 300, "/n/c2", "/n/c1", "/n/c4", "/n/c3"),
                set("D", 900, "/p/d1", "/p/d2"),
                set("E", 900, "/k/e1", "/k/e2")), List.of(), 0);

        List<String> lines = render(scan);

        List<String> headings = lines.stream().filter(l -> l.startsWith("#")).toList();
        assertEquals(List.of(
                "#1  1000 B reclaimable  (2 copies x 1000 B, sha256 A)",
                "#2  900 B reclaimable  (2 copies x 900 B, sha256 E)",
                "#3  900 B reclaimable  (2 copies x 900 B, sha256 D)",
                "#4  900 B reclaimable  (4 copies x 300 B, sha256 C)",
                "#5  300 B reclaimable  (4 copies x 100 B, sha256 B)"), headings);
        assertEquals(List.of("    " + Path.of("/m/a1"), "    " + Path.of("/m/a2")), lines.subList(1, 3));
        int c = lines.indexOf("#4  900 B reclaimable  (4 copies x 300 B, sha256 C)");
        assertEquals(List.of("    " + Path.of("/n/c1"), "    " + Path.of("/n/c2"),
                "    " + Path.of("/n/c3"), "    " + Path.of("/n/c4")), lines.subList(c + 1, c + 5));
    }

    @ParameterizedTest(name = "[{index}] {0}: {1} -> {2}")
    @CsvSource({
            "ROOT,    1,             1 B",
            "ROOT,    1023,          1023 B",
            "ROOT,    1024,          1.0 KB",
            "GERMANY, 1536,          1.5 KB",
            "ROOT,    1048576,       1.0 MB",
            "FRANCE,  4509715660,    4.2 GB",
            "ROOT,    1099511627776, 1.0 TB"})
    @DisplayName("DFS-023.AC-5 Sizes and counts are formatted the same in every locale")
    void sizesAndCounts(String locale, long bytes, String text) {
        Locale.setDefault(switch (locale) {
            case "GERMANY" -> Locale.GERMANY;
            case "FRANCE" -> Locale.FRANCE;
            default -> Locale.ROOT;
        });

        assertEquals(text, TextReport.size(bytes));
        assertEquals("3,330", TextReport.count(3330));
    }

    @Test
    @DisplayName("DFS-028.AC-2 A set of 7,993 copies shows the separator in its header")
    void largeCopyCountHasSeparator() {
        String[] paths = new String[7993];
        for (int i = 0; i < paths.length; i++) {
            paths[i] = String.format("/s/d%02d/f%05d", i % 50, i);
        }

        String header = render(new ScanResult(List.of(set("a", 4096, paths)), List.of(), 0)).getFirst();

        assertTrue(header.contains("(7,993 copies x "), header);
    }

    @Test
    @DisplayName("DFS-028.AC-2 A small set's header is unchanged")
    void smallCopyCountIsUnchanged() {
        String header = render(new ScanResult(List.of(set("a", 15, "/a", "/b", "/c")), List.of(), 0)).getFirst();

        assertTrue(header.contains("(3 copies x 15 B"), header);
    }

    @Test
    @DisplayName("DFS-027.AC-3 The summary line ends with the excluded count")
    void summaryEndsWithExcludedCount() {
        List<String> lines = render(new ScanResult(List.of(set("A", 4096, "/a", "/b")), List.of(), 29565));

        assertEquals("Summary: 1 sets, 2 files, 4.0 KB reclaimable, 0 skipped, 29,565 excluded", lines.getLast());
    }

    @Test
    @DisplayName("DFS-027.AC-3 With nothing excluded the summary ends with 0 excluded")
    void summaryShowsZeroExcluded() {
        List<String> lines = render(new ScanResult(List.of(), List.of(), 0));

        assertEquals(List.of("No duplicates found.", "Summary: 0 sets, 0 files, 0 B reclaimable, 0 skipped, 0 excluded"),
                lines);
    }

    private static List<String> render(ScanResult scan) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TextReport.write(scan, new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return bytes.toString(StandardCharsets.UTF_8).lines().toList();
    }

    /** A set whose digest is its name, with one single-link copy per path, in the order given. */
    private static DuplicateSet set(String name, long size, String... paths) {
        List<FileEntry> files = java.util.Arrays.stream(paths)
                .map(p -> new FileEntry(Path.of(p), size, Instant.EPOCH)).toList();
        return new DuplicateSet(size, name, files, files.stream().map(f -> new PhysicalCopy(List.of(f))).toList());
    }
}
