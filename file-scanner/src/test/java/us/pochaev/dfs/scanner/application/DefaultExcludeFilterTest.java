package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import us.pochaev.dfs.scanner.domain.model.FileEntry;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** {@link DefaultExcludeFilter} on hand-built entries (DFS-027); no file is read. */
class DefaultExcludeFilterTest {

    private static FileEntry entry(String path, long size) {
        return new FileEntry(Path.of(path), size, Instant.EPOCH);
    }

    @ParameterizedTest(name = "DFS-027.AC-1 A file named {0} is excluded and counted")
    @ValueSource(strings = {".DS_Store", "._photo.jpg", "._", "._.DS_Store", "._._x"})
    @DisplayName("DFS-027.AC-1 Excluded names")
    void excludedNames(String name) {
        DefaultExcludeFilter filter = new DefaultExcludeFilter();

        boolean kept = filter.keep(entry("media/a/" + name, 4096));

        assertFalse(kept);
        assertEquals(1, filter.excluded());
    }

    @ParameterizedTest(name = "DFS-027.AC-1 A file named {0} is kept and not counted")
    @ValueSource(strings = {".ds_store", ".DS_STORE", ".DS_Store.bak", "x._photo.jpg", "photo._", ".DS_Stor",
            "DS_Store", ".photo", "_photo.jpg", "photo.jpg"})
    @DisplayName("DFS-027.AC-1 Kept names")
    void keptNames(String name) {
        DefaultExcludeFilter filter = new DefaultExcludeFilter();

        boolean kept = filter.keep(entry("media/a/" + name, 4096));

        assertTrue(kept);
        assertEquals(0, filter.excluded());
    }

    @Test
    @DisplayName("DFS-027.AC-1 Only the file name is matched, never a folder in the path")
    void folderNamesAreNotMatched() {
        DefaultExcludeFilter filter = new DefaultExcludeFilter();

        assertTrue(filter.keep(entry("media/._folder/photo.jpg", 10)));
        assertTrue(filter.keep(entry("media/.DS_Store/photo.jpg", 10)));
        assertEquals(0, filter.excluded());
    }

    @Test
    @DisplayName("DFS-027.AC-3 The count grows by one per excluded file and only for excluded files")
    void countsEachExcludedFile() {
        DefaultExcludeFilter filter = new DefaultExcludeFilter();

        filter.keep(entry("a/.DS_Store", 1));
        filter.keep(entry("a/photo.jpg", 1));
        filter.keep(entry("b/._photo.jpg", 1));
        filter.keep(entry("b/.DS_Store", 1));

        assertEquals(3, filter.excluded());
    }

    @Test
    @DisplayName("DFS-027.AC-6 The name decides whatever the size, so an empty metadata file is excluded and counted")
    void sizeIsNotLookedAt() {
        DefaultExcludeFilter filter = new DefaultExcludeFilter();

        assertFalse(filter.keep(entry("a/.DS_Store", 0)));
        assertFalse(filter.keep(entry("a/._empty", 0)));
        assertTrue(filter.keep(entry("a/empty.txt", 0)));
        assertEquals(2, filter.excluded());
    }

    @Test
    @DisplayName("DFS-027.AC-5 A disabled filter keeps every entry and counts nothing")
    void disabledFilterKeepsEverything() {
        DefaultExcludeFilter filter = new DefaultExcludeFilter(false);

        assertTrue(filter.keep(entry("a/.DS_Store", 0)));
        assertTrue(filter.keep(entry("a/._photo.jpg", 4096)));
        assertEquals(0, filter.excluded());
    }

    @Test
    @DisplayName("DFS-027.AC-5 The no-argument filter is enabled, matching DEFAULT_ENABLED")
    void noArgumentFilterIsEnabled() {
        DefaultExcludeFilter filter = new DefaultExcludeFilter();

        assertEquals(DefaultExcludeFilter.DEFAULT_ENABLED, !filter.keep(entry("a/.DS_Store", 1)));
        assertTrue(DefaultExcludeFilter.DEFAULT_ENABLED);
    }

    @Test
    @DisplayName("DFS-027.AC-1 A path with no file name is kept")
    void pathWithoutFileNameIsKept() {
        DefaultExcludeFilter filter = new DefaultExcludeFilter();

        assertTrue(filter.keep(new FileEntry(Path.of("/"), 1, Instant.EPOCH)));
        assertEquals(0, filter.excluded());
    }

    @Test
    @DisplayName("DFS-027.AC-1 A null entry is rejected")
    void nullEntryIsRejected() {
        DefaultExcludeFilter filter = new DefaultExcludeFilter();
        try {
            filter.keep(null);
            fail("expected NullPointerException for a null entry");
        } catch (NullPointerException expected) {
            // expected once implemented
        }
    }

    @Test
    @DisplayName("DFS-027.AC-3 Two filters count independently")
    void filtersDoNotShareCounts() {
        DefaultExcludeFilter one = new DefaultExcludeFilter();
        DefaultExcludeFilter two = new DefaultExcludeFilter();

        one.keep(entry("a/.DS_Store", 1));
        one.keep(entry("a/._x", 1));
        two.keep(entry("a/.DS_Store", 1));

        assertEquals(2, one.excluded());
        assertEquals(1, two.excluded());
    }
}
