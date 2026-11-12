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
 * DFS-067 (design.md, scale budget): the same synthetic 150,000-file scan as {@code OverlapAnalysisScaleTest} (its
 * generator is copied here, that test stays unchanged), 50,000 small sets and one set spread over 500 folders, must
 * be grouped in under 3 seconds (measured by the architect: 0.6 s cold, 0.36 to 0.42 s warm; 49,991 groups).
 */
class FolderGroupsScaleTest {

    private static final int FOLDERS = 9_000;
    private static final int FILES = 150_000;
    private static final int SMALL_SETS = 50_000;
    private static final int WIDE_SET = 500;

    @Test
    @DisplayName("DFS-067.AC-1, DFS-067.AC-2 A drive-sized scan result is grouped in under 3 seconds")
    void driveSizedScanIsGroupedInUnderThreeSeconds() {
        ScanResult result = generate();

        long start = System.nanoTime();
        List<FolderGroup> groups = assertTimeout(Duration.ofSeconds(3), () -> FolderGroups.of(result).groups());
        long millis = (System.nanoTime() - start) / 1_000_000;

        System.out.printf("DFS-067 FolderGroups.of: %d ms, %d groups%n", millis, groups.size());
        assertTrue(groups.size() >= SMALL_SETS - 20 && groups.size() <= SMALL_SETS + 1, "groups: " + groups.size());
        assertTrue(groups.stream().anyMatch(g -> g.folders().size() == WIDE_SET),
                "no group lists the " + WIDE_SET + " folders of the wide set");
        assertEquals(WIDE_SET, groups.stream().filter(g -> g.folders().size() == WIDE_SET).findFirst()
                .orElseThrow().copies());
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
