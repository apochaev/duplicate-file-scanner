package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import us.pochaev.dfs.scanner.domain.model.ScanResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/** {@link ScanResult}'s excluded count (DFS-027). */
class ScanResultTest {

    @Test
    @DisplayName("DFS-027.AC-3 A scan result carries the excluded count")
    void carriesExcludedCount() {
        ScanResult result = new ScanResult(List.of(), List.of(), 29565);

        assertEquals(29565, result.excluded());
    }

    @Test
    @DisplayName("DFS-027.AC-3 A negative excluded count is rejected")
    void negativeExcludedCountIsRejected() {
        try {
            new ScanResult(List.of(), List.of(), -1);
            fail("expected IllegalArgumentException for a negative excluded count");
        } catch (IllegalArgumentException expected) {
            // expected once implemented
        }
    }
}
