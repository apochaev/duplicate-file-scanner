package us.pochaev.dfs.server.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import us.pochaev.dfs.scanner.domain.model.ScanResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DFS-046 decision 5: a synthetic scan the size of the user's 3.1 TB drive (150,000 files in 9,000 folders), with
 * 50,000 small sets and one set spread over 500 folders, must analyse in under 10 seconds. The measured time and pair
 * count are printed for docs/design/folder-overlap-analysis.md.
 */
class OverlapAnalysisScaleTest {

    private static final int FOLDERS = 9_000;
    private static final int FILES = 150_000;
    private static final int SMALL_SETS = 50_000;
    private static final int WIDE_SET = 500;

    @Test
    @DisplayName("DFS-046.AC-10 A drive-sized scan result is analysed in under 10 seconds")
    void driveSizedScanIsAnalysedInUnderTenSeconds() {
        ScanResult result = generate();

        long start = System.nanoTime();
        OverlapAnalysis analysis = assertTimeout(Duration.ofSeconds(10), () -> OverlapAnalysis.of(result));
        long millis = (System.nanoTime() - start) / 1_000_000;
        int pairs = analysis.pairs().size();

        System.out.printf("DFS-046.AC-10 OverlapAnalysis.of: %d ms, %d pairs, %d summaries%n",
                millis, pairs, analysis.summaries().size());
        assertTrue(pairs >= WIDE_SET * (WIDE_SET - 1) / 2, "pairs: " + pairs);
    }

    /** Folders /r/dNN/fNNNN, 100 per parent; set copies land in distinct folders chosen by a seeded random. */
    private static ScanResult generate() {
        Random random = new Random(46);
        ScanResults scan = ScanResults.underR();
        int file = 0;

        List<String> wide = new ArrayList<>();
        for (int i = 0; i < WIDE_SET; i++) {
            wide.add(folder(i) + "/wide.bin");
        }
        scan.set(4 * ScanResults.MB, wide.toArray(String[]::new));
        file += WIDE_SET;

        for (int s = 0; s < SMALL_SETS; s++) {
            int copies = s % 2 == 0 ? 2 : 3;
            List<Integer> chosen = new ArrayList<>();
            String[] paths = new String[copies];
            for (int c = 0; c < copies; c++) {
                int folder;
                do {
                    folder = random.nextInt(FOLDERS);
                } while (chosen.contains(folder));
                chosen.add(folder);
                paths[c] = folder(folder) + "/s" + s + ".bin";
            }
            scan.set(1_000 + s, paths);
            file += copies;
        }

        for (int u = 0; file < FILES; u++, file++) {
            scan.unique(500 + u, folder(random.nextInt(FOLDERS)) + "/u" + u + ".bin");
        }
        assertEquals(FILES, scan.fileCount());
        ScanResult result = scan.build();
        assertEquals(FOLDERS, result.folders().size());
        return result;
    }

    private static String folder(int index) {
        return "/r/d%02d/f%04d".formatted(index / 100, index);
    }
}
