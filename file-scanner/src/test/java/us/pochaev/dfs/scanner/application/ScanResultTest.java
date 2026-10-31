package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import us.pochaev.dfs.scanner.domain.model.FolderTotal;
import us.pochaev.dfs.scanner.domain.model.ScanResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/** {@link ScanResult}'s excluded count (DFS-027) and its roots and folder totals (DFS-030). */
class ScanResultTest {

    private static final Path ROOT = Path.of("root");

    @Test
    @DisplayName("DFS-027.AC-3 A scan result carries the excluded count")
    void carriesExcludedCount() {
        ScanResult result = new ScanResult(List.of(), List.of(), List.of(), List.of(), 29565);

        assertEquals(29565, result.excluded());
    }

    @Test
    @DisplayName("DFS-027.AC-3 A negative excluded count is rejected")
    void negativeExcludedCountIsRejected() {
        try {
            new ScanResult(List.of(), List.of(), List.of(), List.of(), -1);
            fail("expected IllegalArgumentException for a negative excluded count");
        } catch (IllegalArgumentException expected) {
            // expected once implemented
        }
    }

    @Test
    @DisplayName("DFS-030.AC-6 A scan result rejects null roots")
    void nullRootsAreRejected() {
        try {
            new ScanResult(null, List.of(), List.of(), List.of(), 0);
            fail("expected NullPointerException for null roots");
        } catch (NullPointerException expected) {
            // expected once implemented
        }
    }

    @Test
    @DisplayName("DFS-030.AC-6 A scan result rejects null folders")
    void nullFoldersAreRejected() {
        try {
            new ScanResult(List.of(), null, List.of(), List.of(), 0);
            fail("expected NullPointerException for null folders");
        } catch (NullPointerException expected) {
            // expected once implemented
        }
    }

    @Test
    @DisplayName("DFS-030.AC-6 A scan result rejects a null root")
    void nullRootElementIsRejected() {
        try {
            new ScanResult(Arrays.asList(ROOT, null), List.of(), List.of(), List.of(), 0);
            fail("expected NullPointerException for a null root");
        } catch (NullPointerException expected) {
            // expected once implemented
        }
    }

    @Test
    @DisplayName("DFS-030.AC-6 A scan result rejects a null folder total")
    void nullFolderTotalIsRejected() {
        try {
            new ScanResult(List.of(), Arrays.asList(total(), null), List.of(), List.of(), 0);
            fail("expected NullPointerException for a null folder total");
        } catch (NullPointerException expected) {
            // expected once implemented
        }
    }

    @Test
    @DisplayName("DFS-030.AC-6 The roots and folders lists cannot be changed")
    void rootsAndFoldersAreImmutable() {
        ScanResult result = new ScanResult(List.of(ROOT), List.of(total()), List.of(), List.of(), 0);

        try {
            result.roots().add(Path.of("other"));
            fail("expected UnsupportedOperationException when adding to roots()");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
        try {
            result.folders().add(total());
            fail("expected UnsupportedOperationException when adding to folders()");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    @Test
    @DisplayName("DFS-030.AC-6 Changing the lists passed in leaves the scan result unchanged")
    void changingTheInputListsLeavesTheResultUnchanged() {
        List<Path> roots = new ArrayList<>(List.of(ROOT));
        List<FolderTotal> folders = new ArrayList<>(List.of(total()));

        ScanResult result = new ScanResult(roots, folders, List.of(), List.of(), 0);
        roots.add(Path.of("other"));
        folders.clear();

        assertEquals(List.of(ROOT), result.roots());
        assertEquals(List.of(total()), result.folders());
    }

    /** Built per call: a static field calling the stubbed record would break class initialization (DFS-027.L-1). */
    private static FolderTotal total() {
        return new FolderTotal(ROOT.resolve("x"), 1, 0);
    }
}
