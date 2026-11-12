package us.pochaev.dfs.server.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import us.pochaev.dfs.scanner.domain.model.ScanResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The folder groups of a scan result, on hand-built results (DFS-067 G1, G3, G5). */
class FolderGroupsTest {

    private static final long MB = ScanResults.MB;

    private static List<FolderGroup> groups(ScanResults scan) {
        ScanResult result = scan.build();
        return FolderGroups.of(result).groups();
    }

    private static List<Path> folderPaths(FolderGroup group) {
        return group.folders().stream().map(GroupFolder::folder).toList();
    }

    private static List<Path> paths(String... slashPaths) {
        List<Path> out = new ArrayList<>();
        for (String p : slashPaths) {
            out.add(ScanResults.p(p));
        }
        return out;
    }

    private static void folder(GroupFolder f, String path, int copiesEach, long sharedFiles, long sharedBytes,
                               long folderFiles, long folderBytes, int otherGroups) {
        assertEquals(ScanResults.p(path), f.folder());
        assertEquals(copiesEach, f.copiesEach(), f.toString());
        assertEquals(sharedFiles, f.sharedFiles(), f.toString());
        assertEquals(sharedBytes, f.sharedBytes(), f.toString());
        assertEquals(folderFiles, f.folderFiles(), f.toString());
        assertEquals(folderBytes, f.folderBytes(), f.toString());
        assertEquals(otherGroups, f.otherGroups(), f.toString());
    }

    @Test
    @DisplayName("DFS-067.AC-1, DFS-067.AC-2, DFS-067.AC-3 Five RAW files shared by a folder and a bundle subfolder form one group")
    void rawFilesInABundle() {
        String top = "/r/Gemini Bridges";
        String deep = top + "/x.auh/Contents/History/resources";
        ScanResults scan = ScanResults.underR();
        String[] names = {"A.ARW", "B.ARW", "C.ARW", "D.ARW", "E.ARW"};
        for (String name : names) {
            scan.set(41 * MB, top + "/" + name, deep + "/" + name);
        }
        for (int i = 0; i < 185; i++) {
            scan.unique(1_000, top + "/u" + i + ".bin");
        }
        for (int i = 0; i < 7; i++) {
            scan.unique(2_000, deep + "/v" + i + ".bin");
        }
        // The folders between hold files of their own, so they exist in the scan, yet no group lists them.
        scan.unique(500, top + "/x.auh/m.bin", top + "/x.auh/Contents/m.bin", top + "/x.auh/Contents/History/m.bin");

        List<FolderGroup> groups = groups(scan);

        assertEquals(1, groups.size(), groups.toString());
        FolderGroup g = groups.get(0);
        assertEquals(5, g.files());
        assertEquals(10, g.copies());
        assertEquals(5 * 41 * MB, g.reclaimableBytes());
        assertEquals(5, g.totalSets());
        assertEquals(paths(top, deep), folderPaths(g));
        folder(g.folders().get(0), top, 1, 5, 5 * 41 * MB, 190, 5 * 41 * MB + 185 * 1_000, 0);
        folder(g.folders().get(1), deep, 1, 5, 5 * 41 * MB, 12, 5 * 41 * MB + 7 * 2_000, 0);
    }

    @Test
    @DisplayName("DFS-067.AC-1, DFS-067.AC-3 Two copies in one folder and one in its parent")
    void twoCopiesInOneFolderAndOneInItsParent() {
        long size = 87_700;
        ScanResults scan = ScanResults.underR().set(size, "/r/Sunset/p.prproj", "/r/Sunset/Auto-Save/p-1.prproj",
                "/r/Sunset/Auto-Save/p.prproj");

        List<FolderGroup> groups = groups(scan);

        assertEquals(1, groups.size(), groups.toString());
        FolderGroup g = groups.get(0);
        assertEquals(paths("/r/Sunset", "/r/Sunset/Auto-Save"), folderPaths(g));
        folder(g.folders().get(0), "/r/Sunset", 1, 1, size, 1, size, 0);
        folder(g.folders().get(1), "/r/Sunset/Auto-Save", 2, 2, 2 * size, 2, 2 * size, 0);
        assertEquals(3, g.copies());
        assertEquals(2 * size, g.reclaimableBytes());
    }

    @Test
    @DisplayName("DFS-067.AC-1 Same folders but different copy counts are different groups; equal shapes merge")
    void differentCopyCountsAreDifferentGroups() {
        ScanResults scan = ScanResults.underR()
                .set(1_000, "/r/A/s1.bin", "/r/B/s1.bin")
                .set(2_000, "/r/A/s2.bin", "/r/A/s2b.bin", "/r/B/s2.bin")
                .set(3_000, "/r/A/s3.bin", "/r/B/s3.bin");

        List<FolderGroup> groups = groups(scan);

        assertEquals(2, groups.size(), groups.toString());
        // Both groups reclaim 4000, so they are told apart by their file counts here, not by position.
        FolderGroup shared = groups.stream().filter(g -> g.files() == 2).findFirst().orElseThrow();
        FolderGroup other = groups.stream().filter(g -> g.files() == 1).findFirst().orElseThrow();
        assertEquals(4_000, shared.reclaimableBytes());
        assertEquals(4, shared.copies());
        assertEquals(1, shared.folders().get(0).copiesEach());
        assertEquals(1, shared.folders().get(1).copiesEach());
        assertEquals(2, other.folders().get(0).copiesEach());
        assertEquals(1, other.folders().get(1).copiesEach());
        assertEquals(3, other.copies());
        assertEquals(4_000, other.reclaimableBytes());
        assertEquals(1, shared.folders().get(0).otherGroups());
        assertEquals(1, other.folders().get(0).otherGroups());
    }

    @Test
    @DisplayName("DFS-067.AC-1, DFS-067.AC-2, DFS-067.AC-3 Copies side by side in one folder form a one-folder group")
    void sideBySideCopies() {
        ScanResults scan = ScanResults.underR()
                .set(3 * MB, "/r/Photos/IMG_001.jpg", "/r/Photos/IMG_001 (1).jpg");

        List<FolderGroup> groups = groups(scan);

        assertEquals(1, groups.size(), groups.toString());
        FolderGroup g = groups.get(0);
        assertEquals(paths("/r/Photos"), folderPaths(g));
        folder(g.folders().get(0), "/r/Photos", 2, 2, 6 * MB, 2, 6 * MB, 0);
        assertEquals(1, g.files());
        assertEquals(2, g.copies());
        assertEquals(3 * MB, g.reclaimableBytes());
    }

    @Test
    @DisplayName("DFS-067.AC-3 A folder in two groups counts the other group")
    void otherGroups() {
        ScanResults scan = ScanResults.underR()
                .set(1_000, "/r/Old/a.bin", "/r/Photos2019/a.bin")
                .set(2_000, "/r/Old/b.bin", "/r/Photos2020/b.bin");

        List<FolderGroup> groups = groups(scan);

        assertEquals(2, groups.size(), groups.toString());
        for (FolderGroup g : groups) {
            for (GroupFolder f : g.folders()) {
                int expected = f.folder().equals(ScanResults.p("/r/Old")) ? 1 : 0;
                assertEquals(expected, f.otherGroups(), g.toString());
            }
        }
        assertEquals(paths("/r/Old", "/r/Photos2020"), folderPaths(groups.get(0)));
        assertEquals(paths("/r/Old", "/r/Photos2019"), folderPaths(groups.get(1)));
    }

    @Test
    @DisplayName("DFS-067.AC-4 Hard links add no copy: a copy with links in two folders is listed in both")
    void hardLinksAddNoCopy() {
        ScanResults scan = ScanResults.underR()
                .linkedSet(5_000, List.of(List.of("/r/A/f", "/r/B/f"), List.of("/r/C/f")));

        List<FolderGroup> groups = groups(scan);

        assertEquals(1, groups.size(), groups.toString());
        FolderGroup g = groups.get(0);
        assertEquals(2, g.copies());
        assertEquals(5_000, g.reclaimableBytes());
        assertEquals(paths("/r/A", "/r/B", "/r/C"), folderPaths(g));
        for (GroupFolder f : g.folders()) {
            assertEquals(1, f.copiesEach(), f.toString());
            assertEquals(1, f.sharedFiles(), f.toString());
            assertEquals(5_000, f.sharedBytes(), f.toString());
        }
    }

    @Test
    @DisplayName("DFS-067.AC-4 Two links of one copy in one folder count once in copiesEach, twice in sharedFiles")
    void twoLinksInOneFolder() {
        ScanResults scan = ScanResults.underR()
                .linkedSet(5_000, List.of(List.of("/r/D/f", "/r/D/g"), List.of("/r/E/f")));

        List<FolderGroup> groups = groups(scan);

        assertEquals(1, groups.size(), groups.toString());
        FolderGroup g = groups.get(0);
        assertEquals(2, g.copies());
        assertEquals(5_000, g.reclaimableBytes());
        assertEquals(paths("/r/D", "/r/E"), folderPaths(g));
        folder(g.folders().get(0), "/r/D", 1, 2, 10_000, 2, 10_000, 0);
        folder(g.folders().get(1), "/r/E", 1, 1, 5_000, 1, 5_000, 0);
    }

    @Test
    @DisplayName("DFS-067.AC-5 Heaviest group first, then first folder path, then the folder lists")
    void order() {
        ScanResults scan = ScanResults.underR()
                // three groups reclaiming 1000 each, added out of order
                .set(1_000, "/r/B/t1.bin", "/r/Z/t1.bin")
                .set(1_000, "/r/A/t2.bin", "/r/Y/t2.bin")
                .set(1_000, "/r/A/t3.bin", "/r/X/t3.bin")
                // lightest and heaviest
                .set(175 * 1024, "/r/L1/small.bin", "/r/L2/small.bin")
                .set(206 * MB, "/r/H1/big.bin", "/r/H2/big.bin");

        List<FolderGroup> groups = groups(scan);

        assertEquals(5, groups.size(), groups.toString());
        assertEquals(paths("/r/H1", "/r/H2"), folderPaths(groups.get(0)));
        assertEquals(paths("/r/L1", "/r/L2"), folderPaths(groups.get(1)));
        assertEquals(paths("/r/A", "/r/X"), folderPaths(groups.get(2)));
        assertEquals(paths("/r/A", "/r/Y"), folderPaths(groups.get(3)));
        assertEquals(paths("/r/B", "/r/Z"), folderPaths(groups.get(4)));
        assertEquals(206 * MB, groups.get(0).reclaimableBytes());
    }

    @Test
    @DisplayName("DFS-067.AC-8 A group of 25 sets lists its first 20, largest reclaimable first, and counts all 25")
    void firstTwentySets() {
        ScanResults scan = ScanResults.underR();
        for (int i = 0; i < 25; i++) {
            scan.set(1_000 + i, "/r/A/s" + i + ".bin", "/r/B/s" + i + ".bin");
        }

        List<FolderGroup> groups = groups(scan);

        assertEquals(1, groups.size(), groups.toString());
        FolderGroup g = groups.get(0);
        assertEquals(25, g.totalSets());
        assertEquals(25, g.files());
        assertEquals(50, g.copies());
        assertEquals(25 * 1_000 + 300, g.reclaimableBytes());
        assertEquals(20, g.sets().size());
        for (int i = 0; i < 20; i++) {
            GroupSet s = g.sets().get(i);
            int index = 24 - i;
            assertEquals("s" + index + ".bin", s.name());
            assertEquals(1_000 + index, s.size());
            assertEquals(2, s.copies());
            assertEquals("%064x".formatted(index + 1), s.digest());
        }
    }
}
