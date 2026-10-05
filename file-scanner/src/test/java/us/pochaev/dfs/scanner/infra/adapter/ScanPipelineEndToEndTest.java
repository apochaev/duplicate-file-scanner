package us.pochaev.dfs.scanner.infra.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.pochaev.dfs.scanner.application.ScanPipeline;
import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.PhysicalCopy;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.scanner.domain.model.ScanResult;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * DFS-005.AC-17: the whole scan pipeline wired with the real NIO adapters
 * ({@link NioLocationResolver}, {@link NioFileTreeWalker}, {@link NioContentHasher},
 * {@link NioFileIdentity}) over real files in a {@code @TempDir}, following the precedent of
 * {@code PartialHashFilterRealFileTest} (design.md). Lives in this package, alongside
 * {@link Fixtures}, which package-private test helper it uses directly, per design.md's "Test
 * files the test-author needs".
 * <p>
 * The temporary directory holds the committed fixtures ({@link Fixtures#copyInto}), a 200 KB
 * identical pair and a 200 KB near-duplicate pair generated in code (never committed), and a hard
 * link created at test time ({@link Fixtures#createHardLinkOrSkip}, skipped through
 * {@code Assumptions} when it cannot be created).
 * <p>
 * The test constructs {@link ScanPipeline} and calls {@link ScanPipeline#scan}, which is currently
 * stubbed to throw {@code UnsupportedOperationException("not implemented: DFS-005")}
 * unconditionally (as is {@link ScanConfig#defaults()}, needed to call it). That is why this test
 * fails red for that one reason today.
 */
class ScanPipelineEndToEndTest {

    @Test
    @DisplayName("DFS-005.AC-17 The real pipeline on real files finds the identical pair, "
            + "excludes the near-duplicate and the empty files, and treats the hard link as one physical copy")
    void realPipelineOnRealFiles(@TempDir Path dir) throws IOException {
        Path root = Fixtures.copyInto(Files.createDirectory(dir.resolve("tree")));
        Path big = Files.createDirectory(root.resolve("big"));

        int size = 200 * 1024;
        Path same1 = big.resolve("same1.bin");
        Path same2 = big.resolve("same2.bin");
        Path near1 = big.resolve("near1.bin");
        Path near2 = big.resolve("near2.bin");
        writePattern(same1, size, 0, -1);
        writePattern(same2, size, 0, -1);
        // the near pair uses its own base so it differs from the same pair too
        writePattern(near1, size, 1, -1);
        writePattern(near2, size, 1, size / 2); // differs from near1 only in the middle byte

        Path link = big.resolve("link.bin");
        Fixtures.createHardLinkOrSkip(link, same1);

        ScanPipeline pipeline = new ScanPipeline(
                new NioLocationResolver(), new NioFileTreeWalker(), new NioContentHasher(), new NioFileIdentity());

        ScanResult result = pipeline.scan(List.of(root), ScanConfig.defaults(), event -> {
        }, () -> false);

        DuplicateSet bigSet = result.sets().stream()
                .filter(set -> set.files().stream().anyMatch(f -> f.path().equals(same1)))
                .findFirst()
                .orElseGet(() -> fail("no duplicate set found containing same1.bin"));

        Set<Path> filePaths = bigSet.files().stream().map(FileEntry::path).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(same1, same2, link), filePaths);

        PhysicalCopy same1LinkCopy = bigSet.copies().stream()
                .filter(copy -> copy.links().stream().anyMatch(f -> f.path().equals(same1)))
                .findFirst()
                .orElseGet(() -> fail("no physical copy found containing same1.bin"));
        Set<Path> same1CopyPaths = same1LinkCopy.links().stream().map(FileEntry::path).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(same1, link), same1CopyPaths, "same1.bin and link.bin should be one physical copy");
        assertEquals(2, bigSet.copies().size(), "same1/link should be one copy, same2 the other");

        for (DuplicateSet set : result.sets()) {
            assertFalse(set.files().stream().anyMatch(f -> f.path().equals(near1) || f.path().equals(near2)),
                    "no set should hold the near-duplicate pair");
            assertFalse(set.files().stream().anyMatch(f -> f.size() == 0),
                    "no set should hold an empty file");
        }
    }

    private static void writePattern(Path path, int size, int base, int diffOffset) throws IOException {
        try (OutputStream out = Files.newOutputStream(path)) {
            for (int i = 0; i < size; i++) {
                byte b = (byte) (i + base);
                if (i == diffOffset) {
                    b = (byte) (b + 1);
                }
                out.write(b);
            }
        }
    }
}
