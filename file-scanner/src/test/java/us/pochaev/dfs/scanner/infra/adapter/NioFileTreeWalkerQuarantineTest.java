package us.pochaev.dfs.scanner.infra.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.pochaev.dfs.scanner.domain.model.SkipReason;
import us.pochaev.dfs.scanner.domain.model.WalkEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * DFS-052.AC-1 and AC-2: {@link NioFileTreeWalker} prunes plain directories named exactly
 * {@code .dfs-quarantine}. Real directories in a {@code @TempDir}; the quarantine copy is created
 * at test time, never committed. Every scenario has the plain directory {@code A}, so every walk
 * reaches the quarantine check.
 */
class NioFileTreeWalkerQuarantineTest {

    private static final String QUARANTINE = ".dfs-quarantine";
    private static final int SIZE = 2 * 1024 * 1024;

    private final NioFileTreeWalker walker = new NioFileTreeWalker();

    @TempDir
    Path temp;

    private Path root;
    private byte[] photo;

    @BeforeEach
    void createTree() throws IOException {
        root = temp.toRealPath();
        photo = new byte[SIZE];
        for (int i = 0; i < SIZE; i++) {
            photo[i] = (byte) (i * 31 + (i >> 8));
        }
        write(root.resolve("A/photo.jpg"));
        write(root.resolve(QUARANTINE + "/run-1/A/photo.jpg"));
    }

    @Test
    @DisplayName("DFS-052.AC-1 The walker prunes a quarantine folder and reports it")
    void prunesAQuarantineFolderAndReportsIt() throws IOException {
        List<WalkEvent> events = walk(root);

        assertEquals(List.of(root.resolve("A/photo.jpg")), found(events));
        List<WalkEvent.Skipped> skipped = skipped(events);
        assertEquals(1, skipped.size(), skipped.toString());
        assertEquals(root.resolve(QUARANTINE), skipped.getFirst().path());
        assertEquals(SkipReason.QUARANTINE, skipped.getFirst().reason());
        assertEquals(NioFileTreeWalker.QUARANTINE_DETAIL, skipped.getFirst().detail());
        assertFalse(events.stream().anyMatch(e -> e.toString().contains("run-1")), "visited under the quarantine");
    }

    @Test
    @DisplayName("DFS-052.AC-1 A deeper quarantine folder is pruned too")
    void aDeeperQuarantineFolderIsPrunedToo() throws IOException {
        Path deep = root.resolve("A/B/" + QUARANTINE);
        write(deep.resolve("run-2/x.bin"));

        List<WalkEvent> events = walk(root);

        assertEquals(List.of(root.resolve("A/photo.jpg")), found(events));
        List<WalkEvent.Skipped> skipped = skipped(events);
        assertEquals(List.of(root.resolve(QUARANTINE), deep), skipped.stream().map(WalkEvent.Skipped::path).sorted().toList());
        assertEquals(List.of(SkipReason.QUARANTINE, SkipReason.QUARANTINE),
                skipped.stream().map(WalkEvent.Skipped::reason).toList());
    }

    @Test
    @DisplayName("DFS-052.AC-2 A directory named .DFS-Quarantine is walked")
    void otherCasingIsWalked() throws IOException {
        assertDirectoryWalked(".DFS-Quarantine");
    }

    @Test
    @DisplayName("DFS-052.AC-2 A directory named .dfs-quarantine-old is walked")
    void longerNameIsWalked() throws IOException {
        assertDirectoryWalked(".dfs-quarantine-old");
    }

    private void assertDirectoryWalked(String name) throws IOException {
        // the Background quarantine goes first: on a case-insensitive disk ".DFS-Quarantine" would be the same folder
        for (String gone : List.of("/run-1/A/photo.jpg", "/run-1/A", "/run-1", "")) {
            Files.delete(root.resolve(QUARANTINE + gone));
        }
        write(root.resolve(name + "/y.bin"));

        List<WalkEvent> events = walk(root);

        assertEquals(sorted(root.resolve("A/photo.jpg"), root.resolve(name + "/y.bin")), found(events));
        assertEquals(List.of(), skipped(events));
    }

    @Test
    @DisplayName("DFS-052.AC-2 A regular file named .dfs-quarantine is yielded")
    void aRegularFileNamedLikeAQuarantineIsYielded() throws IOException {
        Path file = root.resolve("C/" + QUARANTINE);
        write(file);

        List<WalkEvent> events = walk(root);

        assertEquals(sorted(root.resolve("A/photo.jpg"), file), found(events));
        List<WalkEvent.Skipped> skipped = skipped(events);
        assertEquals(1, skipped.size(), skipped.toString());
        assertEquals(root.resolve(QUARANTINE), skipped.getFirst().path());
    }

    @Test
    @DisplayName("DFS-052.AC-2 A symbolic link named .dfs-quarantine is still a symlink and never followed")
    void aSymlinkNamedLikeAQuarantineIsStillASymlink() throws IOException {
        Path link = root.resolve("B/" + QUARANTINE);
        Files.createDirectories(link.getParent());
        Fixtures.createSymlinkOrSkip(link, root.resolve("A"));

        List<WalkEvent> events = walk(root);

        assertEquals(List.of(root.resolve("A/photo.jpg")), found(events));
        assertEquals(SkipReason.SYMLINK, reasonOf(events, link));
    }

    @Test
    @DisplayName("DFS-052.AC-2 A junction named .dfs-quarantine is still a special file and never followed")
    void aJunctionNamedLikeAQuarantineIsStillSpecial() throws IOException {
        assumeTrue(Fixtures.isWindows(), "junctions exist only on Windows");
        Path link = root.resolve("B/" + QUARANTINE);
        Files.createDirectories(link.getParent());
        Fixtures.createJunction(link, root.resolve("A"));

        List<WalkEvent> events = walk(root);

        assertEquals(List.of(root.resolve("A/photo.jpg")), found(events));
        assertEquals(SkipReason.SPECIAL_FILE, reasonOf(events, link));
    }

    /** The reason of the one skip at {@code path}; the real quarantine folder is skipped as well. */
    private static SkipReason reasonOf(List<WalkEvent> events, Path path) {
        List<WalkEvent.Skipped> at = skipped(events).stream().filter(s -> s.path().equals(path)).toList();
        assertEquals(1, at.size(), skipped(events).toString());
        return at.getFirst().reason();
    }

    private static List<Path> sorted(Path... paths) {
        return java.util.Arrays.stream(paths).sorted().toList();
    }

    private void write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, photo);
    }

    private List<WalkEvent> walk(Path start) throws IOException {
        List<WalkEvent> events = new ArrayList<>();
        walker.walk(start, event -> {
            events.add(event);
            return true;
        });
        return events;
    }

    private static List<Path> found(List<WalkEvent> events) {
        return events.stream()
                .filter(WalkEvent.FileFound.class::isInstance)
                .map(e -> ((WalkEvent.FileFound) e).entry().path())
                .sorted()
                .toList();
    }

    private static List<WalkEvent.Skipped> skipped(List<WalkEvent> events) {
        return events.stream()
                .filter(WalkEvent.Skipped.class::isInstance)
                .map(WalkEvent.Skipped.class::cast)
                .toList();
    }
}
