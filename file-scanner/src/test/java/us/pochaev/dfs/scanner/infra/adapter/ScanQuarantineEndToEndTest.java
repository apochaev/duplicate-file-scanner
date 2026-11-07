package us.pochaev.dfs.scanner.infra.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.pochaev.dfs.scanner.application.ScanPipeline;
import us.pochaev.dfs.scanner.domain.model.FolderTotal;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.model.SkipReason;
import us.pochaev.dfs.scanner.domain.model.WalkEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * DFS-052.AC-3: the real pipeline over real files. {@code A/photo.jpg} (2 MB, generated in code)
 * and a byte-identical copy under {@code .dfs-quarantine/run-1/A/}, created at test time.
 */
class ScanQuarantineEndToEndTest {

    private static final int SIZE = 2 * 1024 * 1024;

    @Test
    @DisplayName("DFS-052.AC-3 A quarantined copy is not a duplicate")
    void aQuarantinedCopyIsNotADuplicate(@TempDir Path dir) throws IOException {
        assertQuarantineLeftOut(dir, ScanConfig.defaults());
    }

    @Test
    @DisplayName("DFS-052.AC-3 Turning off default excludes does not bring quarantines back")
    void turningOffDefaultExcludesDoesNotBringQuarantinesBack(@TempDir Path dir) throws IOException {
        ScanConfig defaults = ScanConfig.defaults();
        assertQuarantineLeftOut(dir, new ScanConfig(defaults.minSize(), defaults.sampleSize(),
                defaults.partialHashThreshold(), false));
    }

    private static void assertQuarantineLeftOut(Path dir, ScanConfig config) throws IOException {
        Path root = dir.toRealPath();
        byte[] bytes = new byte[SIZE];
        for (int i = 0; i < SIZE; i++) {
            bytes[i] = (byte) (i * 17 + (i >> 9));
        }
        Files.createDirectories(root.resolve("A"));
        Files.write(root.resolve("A/photo.jpg"), bytes);
        Path quarantine = root.resolve(".dfs-quarantine");
        Files.createDirectories(quarantine.resolve("run-1/A"));
        Files.write(quarantine.resolve("run-1/A/photo.jpg"), bytes);
        ScanPipeline pipeline = new ScanPipeline(
                new NioLocationResolver(), new NioFileTreeWalker(), new NioContentHasher(), new NioFileIdentity());

        ScanResult result = pipeline.scan(List.of(root), config, event -> {
        }, () -> false);

        assertEquals(List.of(), result.sets());
        assertEquals(List.of(root.resolve("A")), result.folders().stream().map(FolderTotal::folder).toList());
        assertFalse(result.folders().stream().anyMatch(f -> f.folder().startsWith(quarantine)));
        assertEquals(1, result.skipped().size(), result.skipped().toString());
        WalkEvent.Skipped skipped = result.skipped().getFirst();
        assertEquals(quarantine, skipped.path());
        assertEquals(SkipReason.QUARANTINE, skipped.reason());
        assertEquals(0, result.excluded());
    }
}
