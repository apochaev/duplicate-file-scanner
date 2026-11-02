package us.pochaev.dfs.server.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.PhysicalCopy;
import us.pochaev.dfs.scanner.domain.model.ScanResult;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static us.pochaev.dfs.server.analysis.ScanResults.MB;
import static us.pochaev.dfs.server.analysis.ScanResults.p;

/**
 * The scenarios of DFS-046 (docs/design/folder-overlap-analysis.md decisions 1 to 5), on scan results built in memory
 * under one root {@code /r}. Every file is 1 MB unless stated.
 */
class OverlapAnalysisTest {

    @Test
    @DisplayName("DFS-046.AC-1 Coverage spread over two folders shows in the summary, not in any one pair")
    void coverageSpreadOverTwoFolders() {
        ScanResults scan = ScanResults.underR();
        for (int i = 0; i < 1000; i++) {
            String other = i < 600 ? "/r/Photos/2019/" : "/r/Backup/2019/";
            scan.set(MB, "/r/Old/Pictures/f" + i + ".jpg", other + "f" + i + ".jpg");
        }
        OverlapAnalysis analysis = OverlapAnalysis.of(scan.build());

        FolderSummary pictures = summary(analysis, "/r/Old/Pictures");
        assertEquals(1000, pictures.totalFiles());
        assertEquals(1000, pictures.filesWithCopyElsewhere());

        FolderPair withPhotos = listedPair(analysis, "/r/Old/Pictures", "/r/Photos/2019");
        assertEquals(600, withPhotos.a().filesWithCopy());

        FolderPair withBackup = listedPair(analysis, "/r/Backup/2019", "/r/Old/Pictures");
        assertEquals(400, withBackup.b().filesWithCopy());

        for (FolderPair pair : analysis.pairs()) {
            for (PairSide side : List.of(pair.a(), pair.b())) {
                if (side.folder().equals(p("/r/Old/Pictures"))) {
                    assertTrue(side.filesWithCopy() <= 600, pair.toString());
                }
            }
        }
    }

    @Test
    @DisplayName("DFS-046.AC-2 One pair carries both directions, a before b, with full() per side")
    void onePairCarriesBothDirections() {
        ScanResults scan = ScanResults.underR();
        for (int i = 0; i < 340; i++) {
            scan.set(MB, "/r/Photos/2019/p" + i + ".jpg", "/r/Backup/2019/b" + i + ".jpg");
        }
        for (int i = 340; i < 400; i++) {
            scan.unique(MB, "/r/Photos/2019/p" + i + ".jpg");
        }
        OverlapAnalysis analysis = OverlapAnalysis.of(scan.build());

        List<FolderPair> pairs = analysis.pairs();
        assertEquals(1, pairs.size(), pairs.toString());
        FolderPair pair = pairs.getFirst();
        assertEquals(new PairSide(p("/r/Backup/2019"), 340, 340, 340 * MB, 340 * MB), pair.a());
        assertTrue(pair.a().full());
        assertEquals(new PairSide(p("/r/Photos/2019"), 340, 400, 340 * MB, 400 * MB), pair.b());
        assertFalse(pair.b().full());
    }

    @Test
    @DisplayName("DFS-046.AC-2 A side with no files is never full")
    void aSideWithNoFilesIsNeverFull() {
        OverlapAnalysis analysis = OverlapAnalysis.of(ScanResults.underR()
                .unique(MB, "/r/Photos/2019/a.jpg", "/r/Photos/2019/b.jpg", "/r/Photos/2019/c.jpg")
                .build());

        FolderPair pair = analysis.pair(p("/r/Nothing"), p("/r/Photos/2019"));

        assertEquals(new PairSide(p("/r/Nothing"), 0, 0, 0, 0), pair.a());
        assertFalse(pair.a().full());
        assertEquals(new PairSide(p("/r/Photos/2019"), 0, 3, 0, 3 * MB), pair.b());
    }

    @Test
    @DisplayName("DFS-046.AC-3 A file with copies in three folders counts in all three pairs and each summary once")
    void aFileWithCopiesInThreeFolders() {
        OverlapAnalysis analysis = OverlapAnalysis.of(ScanResults.underR()
                .set(MB, "/r/A/x.jpg", "/r/B/x.jpg", "/r/C/x.jpg")
                .build());

        assertEquals(List.of(folders("/r/A", "/r/B"), folders("/r/A", "/r/C"), folders("/r/B", "/r/C")),
                analysis.pairs().stream().map(OverlapAnalysisTest::folders).toList());
        for (FolderPair pair : analysis.pairs()) {
            assertEquals(1, pair.a().filesWithCopy(), pair.toString());
            assertEquals(1, pair.b().filesWithCopy(), pair.toString());
        }
        for (String folder : List.of("/r/A", "/r/B", "/r/C")) {
            assertEquals(1, summary(analysis, folder).filesWithCopyElsewhere(), folder);
        }
    }

    @Test
    @DisplayName("DFS-046.AC-4 Hard links in other folders are not copies")
    void hardLinksInOtherFoldersAreNotCopies() {
        OverlapAnalysis analysis = OverlapAnalysis.of(ScanResults.underR()
                .linkedSet(MB, List.of(List.of("/r/A/x.jpg", "/r/B/x.jpg"), List.of("/r/C/x.jpg")))
                .build());

        assertEquals(List.of(folders("/r/A", "/r/C"), folders("/r/B", "/r/C")),
                analysis.pairs().stream().map(OverlapAnalysisTest::folders).toList());
        for (FolderPair pair : analysis.pairs()) {
            assertEquals(1, pair.a().filesWithCopy(), pair.toString());
            assertEquals(1, pair.b().filesWithCopy(), pair.toString());
        }
        assertEquals(1, summary(analysis, "/r/A").filesWithCopyElsewhere());
        assertEquals(1, summary(analysis, "/r/B").filesWithCopyElsewhere());
        assertEquals(new PairSide(p("/r/A"), 0, 1, 0, MB), analysis.pair(p("/r/A"), p("/r/B")).a());
    }

    /**
     * A set of one physical copy cannot exist ({@link DuplicateSet} needs two), so a scan reports two links of one
     * file with no other copy only in its folder totals; the analysis must count nothing for them.
     */
    @Test
    @DisplayName("DFS-046.AC-4 A set of hard links alone adds nothing")
    void aSetOfHardLinksAloneAddsNothing() {
        Instant modified = Instant.parse("2026-01-01T00:00:00Z");
        List<FileEntry> links = List.of(new FileEntry(p("/r/A/y.jpg"), MB, modified),
                new FileEntry(p("/r/B/y.jpg"), MB, modified));
        assertThrows(IllegalArgumentException.class,
                () -> new DuplicateSet(MB, "%064x".formatted(1), links, List.of(new PhysicalCopy(links))));

        OverlapAnalysis analysis = OverlapAnalysis.of(ScanResults.underR()
                .unique(MB, "/r/A/y.jpg", "/r/B/y.jpg")
                .build());

        assertEquals(List.of(), analysis.summaries());
        assertEquals(List.of(), analysis.pairs());
        assertEquals(new FolderSummary(p("/r/A"), 1, MB, 0, 0, 0, 0), summary(analysis, "/r/A"));
        assertEquals(new FolderSummary(p("/r"), 2, 2 * MB, 0, 0, 0, 0), summary(analysis, "/r"));
    }

    @Test
    @DisplayName("DFS-046.AC-5 A copy elsewhere means a copy outside the folder's subtree")
    void aCopyElsewhereMeansOutsideTheSubtree() {
        OverlapAnalysis analysis = OverlapAnalysis.of(ScanResults.underR()
                .set(MB, "/r/Backup/2019/a/x.jpg", "/r/Backup/2019/b/x.jpg")
                .build());

        assertEquals(1, summary(analysis, "/r/Backup/2019/a").filesWithCopyElsewhere());
        assertEquals(1, summary(analysis, "/r/Backup/2019/b").filesWithCopyElsewhere());
        FolderSummary backup = summary(analysis, "/r/Backup/2019");
        assertEquals(0, backup.filesWithCopyElsewhere());
        assertEquals(1, backup.internalDuplicateFiles());
    }

    @Test
    @DisplayName("DFS-046.AC-6 Internal duplicates with no copy elsewhere, and never a pair of a folder with itself")
    void internalDuplicatesWithNoCopyElsewhere() {
        OverlapAnalysis analysis = OverlapAnalysis.of(ScanResults.underR()
                .set(25 * MB, "/r/Photos/2019/IMG_001.jpg", "/r/Photos/2019/IMG_001 (1).jpg")
                .build());

        assertEquals(new FolderSummary(p("/r/Photos/2019"), 2, 50 * MB, 0, 0, 1, 25 * MB),
                summary(analysis, "/r/Photos/2019"));
        assertEquals(List.of(), analysis.pairs());
    }

    @Test
    @DisplayName("DFS-046.AC-6 Internal duplicates are counted alongside copies elsewhere, not added to them")
    void internalDuplicatesCountedAlongsideCopiesElsewhere() {
        OverlapAnalysis analysis = OverlapAnalysis.of(ScanResults.underR()
                .set(10 * MB, "/r/P/a.jpg", "/r/P/b.jpg", "/r/Q/c.jpg")
                .build());

        assertEquals(new FolderSummary(p("/r/P"), 2, 20 * MB, 2, 20 * MB, 1, 10 * MB), summary(analysis, "/r/P"));
    }

    @Test
    @DisplayName("DFS-046.AC-7 Ancestor pairs come only from the query, counted recursively in either order")
    void ancestorPairsComeOnlyFromTheQuery() {
        OverlapAnalysis analysis = OverlapAnalysis.of(backupAndPhotos());

        assertEquals(List.of(folders("/r/Backup/2019", "/r/Photos/2019"), folders("/r/Backup/2020", "/r/Photos/2020")),
                analysis.pairs().stream().map(OverlapAnalysisTest::folders).toList());

        FolderPair pair = analysis.pair(p("/r/Photos"), p("/r/Backup"));
        assertEquals(new PairSide(p("/r/Backup"), 2, 2, 2 * MB, 2 * MB), pair.a());
        assertEquals(new PairSide(p("/r/Photos"), 2, 2, 2 * MB, 2 * MB), pair.b());
    }

    @ParameterizedTest(name = "DFS-046.AC-7 pair({0}, {1}) is refused")
    @DisplayName("DFS-046.AC-7 Refused pair queries")
    @CsvSource({
            "/r/Backup,         /r/Backup",
            "/r/Backup,         /r/Backup/2019",
            "/r/Backup/2019,    /r/Backup",
            "/r/Backup,         /elsewhere/folder",
            "/elsewhere/folder, /r/Backup",
    })
    void refusedPairQueries(String x, String y) {
        OverlapAnalysis analysis = OverlapAnalysis.of(backupAndPhotos());

        assertThrows(IllegalArgumentException.class, () -> analysis.pair(p(x), p(y)));
    }

    @Test
    @DisplayName("DFS-046.AC-8 Summaries roll up to the root through folders without files, and stop there")
    void summariesRollUpToTheRootAndStopThere() {
        OverlapAnalysis analysis = OverlapAnalysis.of(ScanResults.underR()
                .set(MB, "/r/Media/Old/Pictures/a.jpg", "/r/Photos/a.jpg")
                .build());

        List<FolderSummary> summaries = analysis.summaries();
        for (String folder : List.of("/r/Media/Old/Pictures", "/r/Media/Old", "/r/Media", "/r/Photos")) {
            assertTrue(summaries.contains(new FolderSummary(p(folder), 1, MB, 1, MB, 0, 0)), folder + " in " + summaries);
        }
        for (FolderSummary summary : summaries) {
            assertTrue(summary.folder().startsWith(p("/r")), "above the root: " + summary.folder());
        }
        FolderSummary root = summary(analysis, "/r");
        assertEquals(2, root.totalFiles());
        assertEquals(0, root.filesWithCopyElsewhere());
        assertEquals(Optional.empty(), analysis.summary(p("/elsewhere")));
        assertEquals(Optional.empty(), analysis.summary(p("/")));
    }

    @Test
    @DisplayName("DFS-046.AC-9 Summaries and pairs come in the fixed order, non-empty entries only")
    void fixedOrderAndOnlyNonEmptyEntries() {
        OverlapAnalysis analysis = OverlapAnalysis.of(ScanResults.underR()
                .set(30 * MB, "/r/A/one.bin", "/r/B/one.bin")
                .set(50 * MB, "/r/C/two.bin", "/r/D/two.bin")
                .set(40 * MB, "/r/E/p.bin", "/r/E/q.bin")
                .unique(MB, "/r/F/u.bin")
                .build());

        // /r has no copy outside itself but 120 MB of internal bytes (30 + 50 + 40), so it precedes /r/E (40 MB).
        assertEquals(List.of(p("/r/C"), p("/r/D"), p("/r/A"), p("/r/B"), p("/r"), p("/r/E")),
                analysis.summaries().stream().map(FolderSummary::folder).toList());
        assertEquals(List.of(folders("/r/C", "/r/D"), folders("/r/A", "/r/B")),
                analysis.pairs().stream().map(OverlapAnalysisTest::folders).toList());
    }

    private static ScanResult backupAndPhotos() {
        return ScanResults.underR()
                .set(MB, "/r/Backup/2019/x.jpg", "/r/Photos/2019/x.jpg")
                .set(MB, "/r/Backup/2020/y.jpg", "/r/Photos/2020/y.jpg")
                .build();
    }

    private static FolderSummary summary(OverlapAnalysis analysis, String folder) {
        return analysis.summary(p(folder)).orElseThrow(() -> new AssertionError("no summary for " + folder));
    }

    private static FolderPair listedPair(OverlapAnalysis analysis, String a, String b) {
        for (FolderPair pair : analysis.pairs()) {
            if (folders(pair).equals(folders(a, b))) {
                return pair;
            }
        }
        return fail("pairs() holds no pair of " + a + " and " + b);
    }

    private static List<Path> folders(FolderPair pair) {
        return List.of(pair.a().folder(), pair.b().folder());
    }

    private static List<Path> folders(String a, String b) {
        return List.of(p(a), p(b));
    }
}
