package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import us.pochaev.dfs.scanner.domain.model.FolderTotal;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/** {@link FolderTotal}'s construction invariants (DFS-030 decision 2). */
class FolderTotalTest {

    @Test
    @DisplayName("DFS-030.AC-6 A folder total keeps the values it is given")
    void keepsItsValues() {
        FolderTotal total = new FolderTotal(Path.of("root/x"), 2, 350);

        assertEquals(Path.of("root/x"), total.folder());
        assertEquals(2, total.files());
        assertEquals(350, total.bytes());
    }

    @Test
    @DisplayName("DFS-030.AC-6 A folder total rejects a null folder")
    void nullFolderIsRejected() {
        try {
            new FolderTotal(null, 1, 0);
            fail("expected NullPointerException for a null folder");
        } catch (NullPointerException expected) {
            // expected once implemented
        }
    }

    @Test
    @DisplayName("DFS-030.AC-6 A folder total rejects fewer than one file")
    void zeroFilesIsRejected() {
        try {
            new FolderTotal(Path.of("root/x"), 0, 0);
            fail("expected IllegalArgumentException for 0 files");
        } catch (IllegalArgumentException expected) {
            // expected once implemented
        }
    }

    @Test
    @DisplayName("DFS-030.AC-6 A folder total rejects negative bytes")
    void negativeBytesAreRejected() {
        try {
            new FolderTotal(Path.of("root/x"), 1, -1);
            fail("expected IllegalArgumentException for negative bytes");
        } catch (IllegalArgumentException expected) {
            // expected once implemented
        }
    }
}
