package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import us.pochaev.dfs.scanner.domain.model.FileEntry;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * DFS-001.AC-7: {@link CandidateGroup} enforces its construction invariants.
 *
 * <p>Every test here uses try/catch rather than {@code assertThrows}: the stub throws
 * {@code UnsupportedOperationException("not implemented: DFS-001")} unconditionally, before any
 * precondition check, and {@code assertThrows} against a narrower expected type would report its
 * own {@code AssertionFailedError} instead of letting the stub's exception propagate. A
 * try/fail/catch block lets the stub's exception surface uncaught, which is what the red gate
 * requires.
 */
class CandidateGroupTest {

    private static final Instant WHEN = Instant.parse("2024-01-01T00:00:00Z");

    @Test
    @DisplayName("DFS-001.AC-7 A single file is rejected")
    void rejectsSingleFile() {
        List<FileEntry> files = List.of(entry("a.txt", 10));

        try {
            new CandidateGroup(10, files);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    @DisplayName("DFS-001.AC-7 Two files of different sizes are rejected")
    void rejectsFilesOfDifferentSizes() {
        List<FileEntry> files = List.of(entry("a.txt", 10), entry("b.txt", 20));

        try {
            new CandidateGroup(10, files);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    @DisplayName("DFS-001.AC-7 A file whose size differs from the group's size is rejected")
    void rejectsFileSizeMismatchWithGroupSize() {
        List<FileEntry> files = List.of(entry("a.txt", 20), entry("b.txt", 20));

        try {
            new CandidateGroup(10, files);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    @DisplayName("DFS-001.AC-7 A null files list is rejected")
    void rejectsNullFilesList() {
        try {
            new CandidateGroup(10, null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
    }

    @Test
    @DisplayName("DFS-001.AC-7 A null element in the files list is rejected")
    void rejectsNullElement() {
        List<FileEntry> files = Arrays.asList(entry("a.txt", 10), null);

        try {
            new CandidateGroup(10, files);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
    }

    private static FileEntry entry(String path, long size) {
        return new FileEntry(Path.of(path), size, WHEN);
    }
}
