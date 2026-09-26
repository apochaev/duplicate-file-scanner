package us.pochaev.dfs.scanner.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DFS-017.AC-1, {@link PhysicalCopy}'s half: its constructor enforces its invariants.
 */
class PhysicalCopyTest {

    private static final Instant WHEN = Instant.parse("2024-01-01T00:00:00Z");

    @Test
    @DisplayName("DFS-017.AC-1 A physical copy with no links is rejected")
    void rejectsNoLinks() {
        assertThrows(IllegalArgumentException.class, () -> new PhysicalCopy(List.of()));
    }

    @Test
    @DisplayName("DFS-017.AC-1 A physical copy with a null links list is rejected")
    void rejectsNullLinks() {
        assertThrows(NullPointerException.class, () -> new PhysicalCopy(null));
    }

    @Test
    @DisplayName("DFS-017.AC-1 A physical copy with a null entry is rejected")
    void rejectsNullEntry() {
        List<FileEntry> links = Arrays.asList(entry("a.bin"), null);

        assertThrows(NullPointerException.class, () -> new PhysicalCopy(links));
    }

    @Test
    @DisplayName("DFS-017.AC-1 A physical copy is unaffected when the list it was built from changes afterwards")
    void copiesItsLinks() {
        FileEntry a = entry("a.bin");
        FileEntry b = entry("b.bin");
        List<FileEntry> mutable = new ArrayList<>(List.of(a, b));

        PhysicalCopy copy = new PhysicalCopy(mutable);
        mutable.clear();

        assertEquals(List.of(a, b), copy.links());
        assertThrows(UnsupportedOperationException.class, () -> copy.links().add(a));
    }

    private static FileEntry entry(String name) {
        return new FileEntry(Path.of(name), 10, WHEN);
    }
}
