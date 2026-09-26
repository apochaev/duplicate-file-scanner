package us.pochaev.dfs.scanner.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DFS-017: {@link DuplicateSet}'s physical copies. AC-1 covers the canonical constructor's
 * invariants, AC-2 the 3-argument constructor the full-hash stage keeps using, AC-3 grouping
 * hard-linked paths into one copy. DFS-003's own rules stay in {@code DuplicateSetTest}, unedited.
 */
class DuplicateSetCopiesTest {

    private static final Instant WHEN = Instant.parse("2024-01-01T00:00:00Z");

    private final FileEntry a = entry("a.bin", 10);
    private final FileEntry b = entry("b.bin", 10);
    private final FileEntry c = entry("c.bin", 10);

    @Test
    @DisplayName("DFS-017.AC-1 A set with only one physical copy is rejected")
    void rejectsOneCopy() {
        assertThrows(IllegalArgumentException.class,
                () -> new DuplicateSet(10, "digest", List.of(a, b), List.of(copy(a, b))));
    }

    @Test
    @DisplayName("DFS-017.AC-1 A blank digest is rejected by the canonical constructor")
    void rejectsBlankDigest() {
        assertThrows(IllegalArgumentException.class,
                () -> new DuplicateSet(10, " ", List.of(a, b), List.of(copy(a), copy(b))));
    }

    @Test
    @DisplayName("DFS-017.AC-1 A link whose size differs from the set's size is rejected")
    void rejectsSizeMismatch() {
        FileEntry big = entry("big.bin", 20);

        assertThrows(IllegalArgumentException.class,
                () -> new DuplicateSet(10, "digest", List.of(a, big), List.of(copy(a), copy(big))));
    }

    @Test
    @DisplayName("DFS-017.AC-1 The same path in two copies is rejected")
    void rejectsPathInTwoCopies() {
        assertThrows(IllegalArgumentException.class,
                () -> new DuplicateSet(10, "digest", List.of(a, b), List.of(copy(a, b), copy(b))));
    }

    @Test
    @DisplayName("DFS-017.AC-1 A copy naming a path that is not in files is rejected")
    void rejectsLinkOutsideFiles() {
        assertThrows(IllegalArgumentException.class,
                () -> new DuplicateSet(10, "digest", List.of(a, b), List.of(copy(a), copy(b, c))));
    }

    @Test
    @DisplayName("DFS-017.AC-1 A file that no copy holds is rejected")
    void rejectsFileMissingFromCopies() {
        assertThrows(IllegalArgumentException.class,
                () -> new DuplicateSet(10, "digest", List.of(a, b, c), List.of(copy(a), copy(b))));
    }

    @Test
    @DisplayName("DFS-017.AC-1 Null copies, or a null copy, are rejected")
    void rejectsNullCopies() {
        List<PhysicalCopy> withNull = new ArrayList<>();
        withNull.add(copy(a));
        withNull.add(null);

        assertThrows(NullPointerException.class, () -> new DuplicateSet(10, "digest", List.of(a, b), null));
        assertThrows(NullPointerException.class, () -> new DuplicateSet(10, "digest", List.of(a, b), withNull));
    }

    @Test
    @DisplayName("DFS-017.AC-1 A set is unaffected when the lists it was built from change afterwards")
    void copiesItsLists() {
        List<FileEntry> files = new ArrayList<>(List.of(a, b));
        List<PhysicalCopy> copies = new ArrayList<>(List.of(copy(a), copy(b)));

        DuplicateSet set = new DuplicateSet(10, "digest", files, copies);
        files.clear();
        copies.clear();

        assertEquals(List.of(a, b), set.files());
        assertEquals(List.of(copy(a), copy(b)), set.copies());
        assertThrows(UnsupportedOperationException.class, () -> set.copies().add(copy(c)));
    }

    @Test
    @DisplayName("DFS-017.AC-2 The 3-argument constructor makes each file its own single-link copy")
    void threeArgumentFormGivesSingleLinkCopies() {
        FileEntry fa = entry("a", 10);
        FileEntry fb = entry("b", 10);
        FileEntry fc = entry("c", 10);

        DuplicateSet set = new DuplicateSet(10, "digest", List.of(fa, fb, fc));

        assertEquals(List.of(copy(fa), copy(fb), copy(fc)), set.copies());
        assertEquals(List.of(fa, fb, fc), set.files());
    }

    @Test
    @DisplayName("DFS-017.AC-2 The 3-argument constructor still rejects a null files list with NullPointerException")
    void threeArgumentFormRejectsNullFiles() {
        List<FileEntry> none = null;

        assertThrows(NullPointerException.class, () -> new DuplicateSet(10, "digest", none));
    }

    @Test
    @DisplayName("DFS-017.AC-3 Hard-linked paths grouped into one copy stay together")
    void groupsLinksIntoOneCopy() {
        DuplicateSet set = new DuplicateSet(10, "digest", List.of(a, b, c), List.of(copy(a, b), copy(c)));

        assertEquals(2, set.copies().size());
        assertEquals(List.of(a, b), set.copies().get(0).links());
        assertEquals(List.of(c), set.copies().get(1).links());
        assertEquals(List.of(a, b, c), set.files());
    }

    private static PhysicalCopy copy(FileEntry... links) {
        return new PhysicalCopy(List.of(links));
    }

    private static FileEntry entry(String name, long size) {
        return new FileEntry(Path.of(name), size, WHEN);
    }
}
