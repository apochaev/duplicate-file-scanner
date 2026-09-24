package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.infra.adapter.NioContentHasher;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DFS-002.AC-2 and DFS-002.AC-9 depend on real byte content past the sample threshold (a
 * same-size pair differing only past the sampled head/tail), so per CLAUDE.md's fixture rules
 * these two scenarios run {@link PartialHashFilter} against the real {@link NioContentHasher} and
 * real temporary files rather than the {@code RecordingContentHasher} fake used elsewhere in
 * {@link PartialHashFilterTest}. Files are generated in code at test time and never committed.
 */
class PartialHashFilterRealFileTest {

    private static final Instant WHEN = Instant.parse("2024-01-01T10:00:00Z");

    @Test
    @DisplayName("DFS-002.AC-2 Files identical in the sample but differing only in the middle survive")
    void filesDifferingOnlyInTheMiddleSurvive(@TempDir Path dir) throws IOException {
        int size = 300 * 1024; // over the 128 KB default skip threshold
        Path fileA = dir.resolve("a.bin");
        Path fileB = dir.resolve("b.bin");
        writePattern(fileA, size, -1);
        writePattern(fileB, size, size / 2); // differs only past the 64 KB default sample

        CandidateGroup group = new CandidateGroup(size, List.of(entry(fileA, size), entry(fileB, size)));
        PartialHashFilter filter = new PartialHashFilter(new NioContentHasher());

        List<CandidateGroup> result = filter.filter(List.of(group));

        assertEquals(1, result.size());
        assertEquals(2, result.getFirst().files().size());
    }

    @Test
    @DisplayName("DFS-002.AC-9 A custom sample size and threshold change stage behavior")
    void customSampleSizeAndThresholdChangeBehavior(@TempDir Path dir) throws IOException {
        int size = 10 * 1024;
        int sampleSize = 4 * 1024;
        long skipThreshold = 8 * 1024;
        Path fileA = dir.resolve("a.bin");
        Path fileB = dir.resolve("b.bin");
        writePattern(fileA, size, -1);
        writePattern(fileB, size, size / 2); // differs only past the 4 KB custom sample

        CandidateGroup group = new CandidateGroup(size, List.of(entry(fileA, size), entry(fileB, size)));
        PartialHashFilter filter = new PartialHashFilter(new NioContentHasher(), sampleSize, skipThreshold);

        List<CandidateGroup> result = filter.filter(List.of(group));

        assertEquals(1, result.size());
        assertEquals(2, result.getFirst().files().size());
    }

    /**
     * Writes {@code size} bytes of a repeating pattern, optionally flipping one byte at
     * {@code diffOffset} (pass -1 for no difference). Generated at test time; nothing is
     * committed.
     */
    private static void writePattern(Path path, int size, int diffOffset) throws IOException {
        try (OutputStream out = Files.newOutputStream(path)) {
            for (int i = 0; i < size; i++) {
                byte b = (byte) i;
                if (i == diffOffset) {
                    b = (byte) (b + 1);
                }
                out.write(b);
            }
        }
    }

    private static FileEntry entry(Path path, long size) {
        return new FileEntry(path, size, WHEN);
    }
}
