package us.pochaev.dfs.scanner.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * DFS-003.AC-6: {@link DuplicateSet}'s compact constructor enforces its invariants.
 *
 * <p>Every test here uses try/catch rather than {@code assertThrows}: the stub throws
 * {@code UnsupportedOperationException("not implemented: DFS-003")} unconditionally, before any
 * precondition check, and {@code assertThrows} against a narrower expected type would report its
 * own {@code AssertionFailedError} instead of letting the stub's exception propagate. A
 * try/fail/catch block lets the stub's exception surface uncaught, which is what the red gate
 * requires. The last test below constructs a valid {@code DuplicateSet}, so it fails against the
 * stub for the same reason (the stub throws unconditionally on every construction).
 */
class DuplicateSetTest {

    private static final Instant WHEN = Instant.parse("2024-01-01T00:00:00Z");

    @Test
    @DisplayName("DFS-003.AC-6 A blank digest is rejected")
    void rejectsBlankDigest() {
        List<FileEntry> files = List.of(entry("a.bin", 10), entry("b.bin", 10));

        try {
            new DuplicateSet(10, "   ", files);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    @DisplayName("DFS-003.AC-6 A null digest is rejected")
    void rejectsNullDigest() {
        List<FileEntry> files = List.of(entry("a.bin", 10), entry("b.bin", 10));

        try {
            new DuplicateSet(10, null, files);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
    }

    @Test
    @DisplayName("DFS-003.AC-6 A single file is rejected")
    void rejectsSingleFile() {
        List<FileEntry> files = List.of(entry("a.bin", 10));

        try {
            new DuplicateSet(10, "digest", files);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    @DisplayName("DFS-003.AC-6 A file whose size differs from the set's size is rejected")
    void rejectsFileSizeMismatch() {
        List<FileEntry> files = List.of(entry("a.bin", 20), entry("b.bin", 20));

        try {
            new DuplicateSet(10, "digest", files);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    @DisplayName("DFS-003.AC-6 A null files list is rejected")
    void rejectsNullFilesList() {
        try {
            new DuplicateSet(10, "digest", null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
    }

    @Test
    @DisplayName("DFS-003.AC-6 A null element in the files list is rejected")
    void rejectsNullElement() {
        List<FileEntry> files = Arrays.asList(entry("a.bin", 10), null);

        try {
            new DuplicateSet(10, "digest", files);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
    }

    @Test
    @DisplayName("DFS-003.AC-6 A set constructed from a mutable list is unaffected when that list is changed afterwards")
    void isUnaffectedByLaterMutationOfTheConstructorArgument() {
        FileEntry a = entry("a.bin", 10);
        FileEntry b = entry("b.bin", 10);
        List<FileEntry> mutable = new ArrayList<>(List.of(a, b));

        DuplicateSet set = new DuplicateSet(10, "digest", mutable);
        mutable.add(entry("c.bin", 10));
        mutable.clear();

        assertEquals(List.of(a, b), set.files());
    }

    private static FileEntry entry(String path, long size) {
        return new FileEntry(Path.of(path), size, WHEN);
    }
}
