package us.pochaev.dfs.scanner.infra.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.pochaev.dfs.scanner.domain.model.SkipReason;
import us.pochaev.dfs.scanner.domain.model.WalkEvent;
import us.pochaev.dfs.scanner.infra.port.ContentHasher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The NIO adapters' progress reports (DFS-024): entered directories and bytes read while hashing. */
class NioProgressTest {

    @TempDir
    Path temp;

    @Test
    @DisplayName("DFS-024.AC-1 The walker reports every directory it enters")
    void walkerReportsEveryDirectory() throws IOException {
        Path root = Files.createDirectory(temp.resolve("root"));
        Files.createDirectories(root.resolve("a/b/c"));
        Files.writeString(root.resolve("a/x.txt"), "x");
        Files.writeString(root.resolve("a/b/y.txt"), "y");

        List<Path> entered = entered(walk(root));

        assertEquals(List.of(root, root.resolve("a"), root.resolve("a/b"), root.resolve("a/b/c")),
                entered.stream().sorted().toList());
    }

    @Test
    @DisplayName("DFS-024.AC-1 A linked directory is skipped, not entered")
    void linkedDirectoryIsNotEntered() throws IOException {
        Path root = Files.createDirectory(temp.resolve("root"));
        Path data = Files.createDirectory(root.resolve("data"));
        Files.writeString(data.resolve("x.txt"), "x");
        Path link = root.resolve("link");
        Fixtures.createSymlinkOrSkip(link, data);

        List<WalkEvent> events = walk(root);

        assertEquals(List.of(root, data), entered(events).stream().sorted().toList());
        assertTrue(events.contains(new WalkEvent.Skipped(link, SkipReason.SYMLINK, "symbolic link")), events.toString());
    }

    @Test
    @DisplayName("DFS-024.AC-4 The real hasher reports bytes within a large file")
    void hasherReportsBytesWithinAFile() throws IOException {
        byte[] content = new byte[20 * 1024 * 1024];
        new Random(24).nextBytes(content);
        Path file = Files.write(temp.resolve("big.bin"), content);
        NioContentHasher hasher = new NioContentHasher();
        List<Long> reports = new ArrayList<>();

        String digest = hasher.fullHash(file, reports::add);

        assertTrue(reports.size() >= 3, reports.toString());
        long previous = 0;
        for (long report : reports) {
            assertTrue(report > previous, "reports must rise: " + reports);
            assertTrue(report - previous <= ContentHasher.PROGRESS_INTERVAL, "reports at most 8 MiB apart: " + reports);
            previous = report;
        }
        assertEquals(20_971_520L, reports.getLast());
        assertEquals(hasher.fullHash(file), digest);
    }

    @Test
    @DisplayName("DFS-024.AC-4 A hasher without progress support still works")
    void defaultMethodReportsNothing() throws IOException {
        ContentHasher plain = new ContentHasher() {
            @Override
            public String sampleHash(Path file, int sampleSize) {
                return "sample";
            }

            @Override
            public String fullHash(Path file) {
                return "full:" + file.getFileName();
            }
        };
        List<Long> reports = new ArrayList<>();

        assertEquals("full:f.bin", plain.fullHash(Path.of("f.bin"), reports::add));
        assertEquals(List.of(), reports);
    }

    private static List<WalkEvent> walk(Path root) throws IOException {
        List<WalkEvent> events = new ArrayList<>();
        new NioFileTreeWalker().walk(root, event -> {
            events.add(event);
            return true;
        });
        return events;
    }

    private static List<Path> entered(List<WalkEvent> events) {
        return events.stream()
                .filter(e -> e instanceof WalkEvent.DirectoryEntered)
                .map(e -> ((WalkEvent.DirectoryEntered) e).directory())
                .toList();
    }
}
