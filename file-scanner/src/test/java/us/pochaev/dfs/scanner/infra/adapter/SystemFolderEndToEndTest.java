package us.pochaev.dfs.scanner.infra.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.pochaev.dfs.scanner.application.ScanPipeline;
import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.FolderTotal;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.scanner.domain.model.ScanEvent;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.model.SkipReason;
import us.pochaev.dfs.scanner.domain.model.WalkEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DFS-061: the real pipeline and the real walker over a tree written at test time. The real walker is
 * deliberate: the setting must be seen to reach it (design.md, "For the test-author").
 */
class SystemFolderEndToEndTest {

    private static byte[] bytes(char c) {
        byte[] b = new byte[6 * 1024];
        Arrays.fill(b, (byte) c);
        return b;
    }

    private static Path write(Path file, byte[] content) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.write(file, content);
    }

    private static Path drive(Path temp) throws IOException {
        Path root = temp.toRealPath().resolve("drive");
        write(root.resolve("docs/photo.jpg"), bytes('P'));
        write(root.resolve("$RECYCLE.BIN/S-1-5-21-1/$R5Y7A3U.ARQ"), bytes('P'));
        write(root.resolve("$RECYCLE.BIN/S-1-5-21-1/$I5Y7A3U.ARQ"), bytes('I'));
        return root;
    }

    private static ScanConfig config(boolean defaultExcludes) {
        ScanConfig d = ScanConfig.defaults();
        return new ScanConfig(d.minSize(), d.sampleSize(), d.partialHashThreshold(), defaultExcludes);
    }

    private static ScanResult scan(Path root, ScanConfig config, List<ScanEvent> events) throws IOException {
        ScanPipeline pipeline = new ScanPipeline(
                new NioLocationResolver(), new NioFileTreeWalker(), new NioContentHasher(), new NioFileIdentity());
        return pipeline.scan(List.of(root), config, events::add, () -> false);
    }

    @Test
    @DisplayName("DFS-061.AC-1, DFS-061.AC-2 A recycle bin copy is not a duplicate and the folder is skipped once")
    void aRecycleBinCopyIsNotADuplicate(@TempDir Path temp) throws IOException {
        Path root = drive(temp);

        ScanResult result = scan(root, config(true), new ArrayList<>());

        assertEquals(List.of(), result.sets());
        assertEquals(1, result.skipped().size(), result.skipped().toString());
        WalkEvent.Skipped skipped = result.skipped().getFirst();
        assertEquals(root.resolve("$RECYCLE.BIN"), skipped.path());
        assertEquals(SkipReason.SYSTEM_FOLDER, skipped.reason());
        assertEquals("operating-system folder, not scanned", skipped.detail());
        assertEquals(List.of(root.resolve("docs")), result.folders().stream().map(FolderTotal::folder).toList());
        assertEquals(0, result.excluded());
    }

    @Test
    @DisplayName("DFS-061.AC-2 Files under a skipped folder are not counted as found")
    void filesUnderASkippedFolderAreNotCountedAsFound(@TempDir Path temp) throws IOException {
        Path root = drive(temp);
        List<ScanEvent> events = new ArrayList<>();

        scan(root, config(true), events);

        long files = events.stream().filter(e -> e instanceof ScanEvent.FileWalked).count();
        assertEquals(1, files, events.toString());
    }

    @Test
    @DisplayName("DFS-061.AC-3 The core setting reaches the walker: default excludes off scans the folders")
    void defaultExcludesOffScansTheFolders(@TempDir Path temp) throws IOException {
        Path root = drive(temp);

        ScanResult result = scan(root, config(false), new ArrayList<>());

        assertEquals(1, result.sets().size(), result.sets().toString());
        DuplicateSet set = result.sets().getFirst();
        assertEquals(2, set.copies().size());
        assertEquals(List.of(), result.skipped());
    }

    @Test
    @DisplayName("DFS-061.AC-3 ScanConfig.defaults has default excludes on and the folders skipped")
    void defaultsHaveTheSkipOn(@TempDir Path temp) throws IOException {
        assertTrue(ScanConfig.defaults().defaultExcludes());
        Path root = drive(temp);

        ScanResult result = scan(root, ScanConfig.defaults(), new ArrayList<>());

        assertEquals(List.of(), result.sets());
        assertFalse(result.skipped().isEmpty());
    }

    @Test
    @DisplayName("DFS-061.AC-5 A system folder is never reported as QUARANTINE, nor the quarantine as SYSTEM_FOLDER")
    void quarantineAndSystemFoldersKeepTheirReasons(@TempDir Path temp) throws IOException {
        Path root = drive(temp);
        write(root.resolve(".dfs-quarantine/run1/x.bin"), bytes('X'));

        for (boolean on : new boolean[]{true, false}) {
            ScanResult result = scan(root, config(on), new ArrayList<>());

            List<WalkEvent.Skipped> quarantine = result.skipped().stream()
                    .filter(s -> s.path().equals(root.resolve(".dfs-quarantine"))).toList();
            assertEquals(1, quarantine.size(), result.skipped().toString());
            assertEquals(SkipReason.QUARANTINE, quarantine.getFirst().reason());
            assertEquals(on ? 2 : 1, result.skipped().size(), result.skipped().toString());
            if (on) {
                assertTrue(result.skipped().stream().anyMatch(s -> s.path().equals(root.resolve("$RECYCLE.BIN"))
                        && s.reason() == SkipReason.SYSTEM_FOLDER));
            }
        }
    }
}
