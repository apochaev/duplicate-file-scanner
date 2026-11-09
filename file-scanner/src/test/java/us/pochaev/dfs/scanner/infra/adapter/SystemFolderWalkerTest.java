package us.pochaev.dfs.scanner.infra.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import us.pochaev.dfs.scanner.domain.model.SkipReason;
import us.pochaev.dfs.scanner.domain.model.WalkEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.abort;

/**
 * DFS-061: the walker leaves out known operating-system folders below the start. Every test calls the
 * three-argument walk explicitly (design.md, "For the test-author").
 */
class SystemFolderWalkerTest {

    private static final String DETAIL = "operating-system folder, not scanned";

    @TempDir
    Path temp;

    private final NioFileTreeWalker walker = new NioFileTreeWalker();

    private static final String[] KNOWN = {"$RECYCLE.BIN", "System Volume Information", "Recycled", "RECYCLER",
            ".Trashes", ".Spotlight-V100", ".fseventsd", ".TemporaryItems", ".DocumentRevisions-V100", ".Trash",
            "lost+found", ".Trash-1000", ".Trash-0"};

    private List<WalkEvent> walk(Path start, boolean skip) throws IOException {
        List<WalkEvent> events = new ArrayList<>();
        walker.walk(start, skip, e -> {
            events.add(e);
            return true;
        });
        return events;
    }

    private static Path file(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        return Files.writeString(path, "X");
    }

    private static List<Path> found(List<WalkEvent> events) {
        return events.stream().filter(e -> e instanceof WalkEvent.FileFound)
                .map(e -> ((WalkEvent.FileFound) e).entry().path()).sorted().toList();
    }

    private static List<WalkEvent.Skipped> skipped(List<WalkEvent> events) {
        return events.stream().filter(e -> e instanceof WalkEvent.Skipped).map(e -> (WalkEvent.Skipped) e).toList();
    }

    private Path drive() throws IOException {
        Path root = temp.toRealPath().resolve("drive");
        file(root.resolve("docs/x.bin"));
        return root;
    }

    private void assertSkippedOnly(Path root, String relative, boolean createCase) throws IOException {
        file(root.resolve(relative).resolve("x.bin"));

        List<WalkEvent> events = walk(root, true);

        assertEquals(List.of(root.resolve("docs/x.bin")), found(events), events.toString());
        List<WalkEvent.Skipped> skipped = skipped(events);
        assertEquals(1, skipped.size(), events.toString());
        assertEquals(root.resolve(relative), skipped.getFirst().path());
        assertEquals(SkipReason.SYSTEM_FOLDER, skipped.getFirst().reason());
        assertEquals(DETAIL, skipped.getFirst().detail());
    }

    @ParameterizedTest(name = "DFS-061.AC-1, DFS-061.AC-2 Each known name is skipped {0}")
    @ValueSource(strings = {"$RECYCLE.BIN", "System Volume Information", "Recycled", "RECYCLER", ".Trashes",
            ".Spotlight-V100", ".fseventsd", ".TemporaryItems", ".DocumentRevisions-V100", ".Trash", "lost+found",
            ".Trash-1000", ".Trash-0"})
    @DisplayName("DFS-061.AC-1, DFS-061.AC-2 Each known name is skipped")
    void eachKnownNameIsSkipped(String name) throws IOException {
        assertSkippedOnly(drive(), name, false);
    }

    @Test
    @DisplayName("DFS-061.AC-1 A known folder is skipped at any depth")
    void skippedAtAnyDepth() throws IOException {
        assertSkippedOnly(drive(), "deep/er/.Trashes", false);
    }

    @ParameterizedTest(name = "DFS-061.AC-1 Windows names match in any letter case {0}")
    @ValueSource(strings = {"$recycle.bin", "system volume information", "recycled", "Recycler"})
    @DisplayName("DFS-061.AC-1 Windows names match in any letter case")
    void windowsNamesMatchInAnyCase(String name) throws IOException {
        assertSkippedOnly(drive(), name, false);
    }

    @ParameterizedTest(name = "DFS-061.AC-1 Names that only resemble a known folder are scanned {0}")
    @ValueSource(strings = {"my.Trash", "lost+found2", ".Trash-", ".Trash-abc", "Trash", ".trashes", "LOST+FOUND",
            ".Trash-12a"})
    @DisplayName("DFS-061.AC-1 Names that only resemble a known folder are scanned")
    void lookAlikesAreScanned(String name) throws IOException {
        Path root = drive();
        file(root.resolve(name).resolve("x.bin"));

        List<WalkEvent> events = walk(root, true);

        assertEquals(List.of(root.resolve("docs/x.bin"), root.resolve(name).resolve("x.bin")).stream().sorted().toList(),
                found(events), events.toString());
        assertEquals(List.of(), skipped(events));
    }

    @Test
    @DisplayName("DFS-061.AC-2 A skipped folder is not entered, so it is not counted as a directory")
    void aSkippedFolderIsNotEntered() throws IOException {
        Path root = drive();
        file(root.resolve("$RECYCLE.BIN/S-1/x.bin"));

        List<WalkEvent> events = walk(root, true);

        assertEquals(List.of(), events.stream().filter(e -> e instanceof WalkEvent.DirectoryEntered
                && ((WalkEvent.DirectoryEntered) e).directory().startsWith(root.resolve("$RECYCLE.BIN"))).toList());
        assertEquals(1, skipped(events).size());
    }

    @Test
    @DisplayName("DFS-061.AC-3 With the switch off the folders are walked like any other")
    void switchOffWalksThem() throws IOException {
        Path root = drive();
        for (String name : KNOWN) {
            file(root.resolve(name).resolve("x.bin"));
        }

        List<WalkEvent> events = walk(root, false);

        assertEquals(KNOWN.length + 1, found(events).size(), events.toString());
        assertEquals(List.of(), skipped(events));
    }

    @Test
    @DisplayName("DFS-061.AC-4 A start inside a system folder is walked")
    void aStartInsideASystemFolderIsWalked() throws IOException {
        Path root = drive();
        Path start = root.resolve("$RECYCLE.BIN/S-1");
        file(start.resolve("a.bin"));
        file(start.resolve("copy/b.bin"));

        List<WalkEvent> events = walk(start, true);

        assertEquals(List.of(start.resolve("a.bin"), start.resolve("copy/b.bin")), found(events));
        assertEquals(List.of(), skipped(events));
    }

    @Test
    @DisplayName("DFS-061.AC-4 A start named like a system folder is walked, folders below it are still skipped")
    void aStartNamedLikeASystemFolderIsWalked() throws IOException {
        Path root = drive();
        Path start = root.resolve("$RECYCLE.BIN");
        file(start.resolve("System Volume Information/x.bin"));
        file(start.resolve("S-1/x.bin"));

        List<WalkEvent> events = walk(start, true);

        assertEquals(List.of(start.resolve("S-1/x.bin")), found(events));
        List<WalkEvent.Skipped> skipped = skipped(events);
        assertEquals(1, skipped.size(), events.toString());
        assertEquals(start.resolve("System Volume Information"), skipped.getFirst().path());
        assertEquals(SkipReason.SYSTEM_FOLDER, skipped.getFirst().reason());
    }

    @ParameterizedTest(name = "DFS-061.AC-5 The quarantine folder is skipped as QUARANTINE, system-folder switch {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("DFS-061.AC-5 The quarantine folder is skipped as QUARANTINE with the switch on or off")
    void quarantineIsAlwaysQuarantine(boolean skip) throws IOException {
        Path root = drive();
        file(root.resolve(".dfs-quarantine/run1/x.bin"));

        List<WalkEvent> events = walk(root, skip);

        assertEquals(List.of(root.resolve("docs/x.bin")), found(events));
        List<WalkEvent.Skipped> skipped = skipped(events);
        assertEquals(1, skipped.size(), events.toString());
        assertEquals(root.resolve(".dfs-quarantine"), skipped.getFirst().path());
        assertEquals(SkipReason.QUARANTINE, skipped.getFirst().reason());
    }

    @Test
    @DisplayName("DFS-061.AC-6 A regular file with a known name is scanned")
    void aRegularFileWithAKnownNameIsScanned() throws IOException {
        Path root = drive();
        file(root.resolve("a/lost+found"));
        file(root.resolve("b/.Trash"));

        List<WalkEvent> events = walk(root, true);

        assertEquals(List.of(root.resolve("a/lost+found"), root.resolve("b/.Trash"), root.resolve("docs/x.bin")),
                found(events));
        assertEquals(List.of(), skipped(events));
    }

    @Test
    @DisplayName("DFS-061.AC-6 A symbolic link with a known name keeps its SYMLINK reason")
    void aSymlinkWithAKnownNameKeepsItsReason() throws IOException {
        Path root = drive();
        Path link = root.resolve(".Trashes");
        try {
            Files.createSymbolicLink(link, root.resolve("docs"));
        } catch (UnsupportedOperationException | IOException e) {
            abort("symbolic links are not available here: " + e.getMessage());
        }

        List<WalkEvent> events = walk(root, true);

        List<WalkEvent.Skipped> skipped = skipped(events);
        assertEquals(1, skipped.size(), events.toString());
        assertEquals(link, skipped.getFirst().path());
        assertEquals(SkipReason.SYMLINK, skipped.getFirst().reason());
    }

    // --- SystemFolderNames, the name table itself

    @ParameterizedTest(name = "DFS-061.AC-1 matches the known name {0}")
    @ValueSource(strings = {"$RECYCLE.BIN", "$recycle.bin", "System Volume Information", "SYSTEM VOLUME INFORMATION",
            "Recycled", "RECYCLER", "recycler", ".Trashes", ".Spotlight-V100", ".fseventsd", ".TemporaryItems",
            ".DocumentRevisions-V100", ".Trash", "lost+found", ".Trash-1000", ".Trash-0"})
    @DisplayName("DFS-061.AC-1 matches the known name")
    void matchesKnownNames(String name) {
        org.junit.jupiter.api.Assertions.assertTrue(SystemFolderNames.matches(name), name);
    }

    @ParameterizedTest(name = "DFS-061.AC-1 does not match the look-alike {0}")
    @ValueSource(strings = {"my.Trash", "lost+found2", ".Trash-", ".Trash-abc", "Trash", ".trashes", "LOST+FOUND",
            ".TRASH", ".fseventsd2", "docs", ""})
    @DisplayName("DFS-061.AC-1 does not match the look-alike")
    void doesNotMatchLookAlikes(String name) {
        org.junit.jupiter.api.Assertions.assertFalse(SystemFolderNames.matches(name), name);
    }
}
