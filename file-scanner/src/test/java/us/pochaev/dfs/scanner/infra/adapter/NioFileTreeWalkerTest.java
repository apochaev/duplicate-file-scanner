package us.pochaev.dfs.scanner.infra.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.SkipReason;
import us.pochaev.dfs.scanner.domain.model.WalkEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NioFileTreeWalkerTest {

    private final NioFileTreeWalker walker = new NioFileTreeWalker();

    @TempDir
    Path temp;

    private Path root;

    @BeforeEach
    void copyFixtures() throws IOException {
        root = Fixtures.copyInto(Files.createDirectory(temp.resolve("tree")));
    }

    @Test
    void walksEveryFixtureFileExactlyOnce() throws IOException {
        List<WalkEvent> events = walk(root);

        assertFound(events, root, Fixtures.FILES);
        assertEquals(List.of(), skipped(events));
    }

    @Test
    void reportsSizeAndModifiedTime() throws IOException {
        Instant modified = Instant.parse("2020-01-02T03:04:05Z");
        Files.setLastModifiedTime(root.resolve("basic/a.txt"), FileTime.from(modified));

        FileEntry entry = entry(walk(root), root.resolve("basic/a.txt"));

        assertEquals(18, entry.size());
        assertEquals(modified, entry.lastModified());
    }

    @Test
    void reportsEmptyFilesWithSizeZero() throws IOException {
        List<WalkEvent> events = walk(root);

        assertEquals(0, entry(events, root.resolve("empty/zero-a.dat")).size());
        assertEquals(0, entry(events, root.resolve("empty/zero-b.dat")).size());
    }

    @Test
    void emptyDirectoriesProduceNoEvents() throws IOException {
        Files.createDirectories(root.resolve("hollow/inner"));

        List<WalkEvent> events = walk(root);

        assertFound(events, root, Fixtures.FILES);
        assertEquals(List.of(), skipped(events));
    }

    @Test
    void includesHiddenAndReadOnlyFiles() throws IOException {
        Path extra = Files.createDirectory(root.resolve("extra"));
        Path hidden = Files.writeString(extra.resolve("hidden.txt"), "h");
        Path readOnly = Files.writeString(extra.resolve("readonly.txt"), "r");
        if (Fixtures.isWindows()) {
            Files.setAttribute(hidden, "dos:hidden", true);
        }
        assumeTrue(readOnly.toFile().setReadOnly(), "cannot mark file read-only");
        try {
            List<WalkEvent> events = walk(root);

            assertFound(events, root, plus(Fixtures.FILES, "extra/hidden.txt", "extra/readonly.txt"));
            assertEquals(List.of(), skipped(events));
        } finally {
            readOnly.toFile().setWritable(true);
        }
    }

    @Test
    void treatsNamesDifferingOnlyInCaseAsSeparateFiles() throws IOException {
        Path dir = Files.createDirectory(root.resolve("case"));
        Files.writeString(dir.resolve("Readme.txt"), "upper");
        Files.writeString(dir.resolve("readme.txt"), "lower");
        try (Stream<Path> names = Files.list(dir)) {
            assumeTrue(names.count() == 2, "file system is case-insensitive");
        }

        assertFound(walk(root), root, plus(Fixtures.FILES, "case/Readme.txt", "case/readme.txt"));
    }

    @Test
    void skipsSymlinkToFile() throws IOException {
        Path link = Files.createDirectory(root.resolve("links")).resolve("file-link");
        Fixtures.createSymlinkOrSkip(link, root.resolve("basic/a.txt"));

        List<WalkEvent> events = walk(root);

        assertFound(events, root, Fixtures.FILES);
        assertSkipped(events, link, SkipReason.SYMLINK);
    }

    @Test
    void doesNotFollowSymlinkToDirectory() throws IOException {
        Path link = Files.createDirectory(root.resolve("links")).resolve("dir-link");
        Fixtures.createSymlinkOrSkip(link, root.resolve("folders"));

        List<WalkEvent> events = walk(root);

        assertFound(events, root, Fixtures.FILES);
        assertSkipped(events, link, SkipReason.SYMLINK);
    }

    @Test
    void skipsBrokenSymlink() throws IOException {
        Path link = Files.createDirectory(root.resolve("links")).resolve("broken");
        Fixtures.createSymlinkOrSkip(link, root.resolve("does-not-exist"));

        List<WalkEvent> events = walk(root);

        assertFound(events, root, Fixtures.FILES);
        assertSkipped(events, link, SkipReason.SYMLINK);
    }

    @Test
    void doesNotFollowJunction() throws IOException {
        assumeTrue(Fixtures.isWindows(), "junctions exist only on Windows");
        Path junction = root.resolve("junction");
        Fixtures.createJunction(junction, root.resolve("folders"));

        List<WalkEvent> events = walk(root);

        assertFound(events, root, Fixtures.FILES);
        assertSkipped(events, junction, SkipReason.SPECIAL_FILE);
    }

    @Test
    void reportsHardLinksAsSeparateFiles() throws IOException {
        Fixtures.createHardLinkOrSkip(root.resolve("basic/a-hardlink.txt"), root.resolve("basic/a.txt"));

        List<WalkEvent> events = walk(root);

        assertFound(events, root, plus(Fixtures.FILES, "basic/a-hardlink.txt"));
        assertEquals(List.of(), skipped(events));
    }

    @Test
    void followsRootThatIsASymlink() throws IOException {
        Path link = temp.resolve("root-link");
        Fixtures.createSymlinkOrSkip(link, root);

        List<WalkEvent> events = walk(link);

        assertFound(events, link.toRealPath(), Fixtures.FILES);
        assertEquals(List.of(), skipped(events));
    }

    @Test
    void followsRootThatIsAJunction() throws IOException {
        assumeTrue(Fixtures.isWindows(), "junctions exist only on Windows");
        Path junction = temp.resolve("root-junction");
        Fixtures.createJunction(junction, root);

        List<WalkEvent> events = walk(junction);

        assertFound(events, junction.toRealPath(), Fixtures.FILES);
        assertEquals(List.of(), skipped(events));
    }

    @Test
    void reportsUnreadableDirectoryAndKeepsWalking() throws IOException {
        Path blocked = root.resolve("nested/level1/level2");
        Runnable restore = Fixtures.denyListing(blocked);
        try {
            List<WalkEvent> events = walk(root);

            Collection<String> expected = new ArrayList<>(Fixtures.FILES);
            expected.remove("nested/level1/level2/level3/level4/level5/deep.txt");
            assertFound(events, root, expected);
            assertSkipped(events, blocked, SkipReason.UNREADABLE);
        } finally {
            restore.run();
        }
    }

    @Test
    void stopsWhenListenerReturnsFalse() throws IOException {
        List<WalkEvent> events = new ArrayList<>();

        walker.walk(root, event -> {
            events.add(event);
            return false;
        });

        assertEquals(1, events.size());
    }

    @Test
    void rejectsMissingRoot() {
        assertThrows(NoSuchFileException.class, () -> walk(root.resolve("nope")));
    }

    @Test
    void rejectsFileAsRoot() {
        assertThrows(NotDirectoryException.class, () -> walk(root.resolve("basic/a.txt")));
    }

    private List<WalkEvent> walk(Path start) throws IOException {
        List<WalkEvent> events = new ArrayList<>();
        walker.walk(start, event -> {
            events.add(event);
            return true;
        });
        return events;
    }

    private static Collection<String> plus(Collection<String> base, String... extra) {
        List<String> all = new ArrayList<>(base);
        all.addAll(List.of(extra));
        return all;
    }

    /** Compares as sorted lists, so a file reported twice is a failure. */
    private static void assertFound(List<WalkEvent> events, Path base, Collection<String> expected) {
        List<String> actual = events.stream()
                .filter(WalkEvent.FileFound.class::isInstance)
                .map(e -> ((WalkEvent.FileFound) e).entry().path())
                .map(p -> base.relativize(p).toString().replace('\\', '/'))
                .sorted()
                .toList();
        assertEquals(expected.stream().sorted().toList(), actual);
    }

    private static List<WalkEvent.Skipped> skipped(List<WalkEvent> events) {
        return events.stream()
                .filter(WalkEvent.Skipped.class::isInstance)
                .map(WalkEvent.Skipped.class::cast)
                .toList();
    }

    private static void assertSkipped(List<WalkEvent> events, Path path, SkipReason reason) {
        List<WalkEvent.Skipped> skipped = skipped(events);
        assertEquals(1, skipped.size(), "expected exactly one skipped path but got " + skipped);
        assertEquals(path, skipped.getFirst().path());
        assertEquals(reason, skipped.getFirst().reason());
    }

    private static FileEntry entry(List<WalkEvent> events, Path path) {
        return events.stream()
                .filter(WalkEvent.FileFound.class::isInstance)
                .map(e -> ((WalkEvent.FileFound) e).entry())
                .filter(entry -> entry.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no entry for " + path));
    }
}
