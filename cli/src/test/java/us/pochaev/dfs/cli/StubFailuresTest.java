package us.pochaev.dfs.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StubFailuresTest {

    @Test
    @DisplayName("DFS-062.AC-1 The stub text in stderr is rethrown as UnsupportedOperationException")
    void stubTextInStderr() {
        var e = assertThrows(UnsupportedOperationException.class,
                () -> StubFailures.rethrow("", "error: scan failed: not implemented: DFS-999\n"));
        assertEquals("not implemented: DFS-999", e.getMessage());
    }

    @Test
    @DisplayName("DFS-062.AC-1 The stub text in stdout only is rethrown as UnsupportedOperationException")
    void stubTextInStdout() {
        var e = assertThrows(UnsupportedOperationException.class,
                () -> StubFailures.rethrow("line one\nreport: not implemented: DFS-998 extra\nline three\n", ""));
        assertEquals("not implemented: DFS-998 extra", e.getMessage());
    }

    @Test
    @DisplayName("DFS-062.AC-2 No stub text anywhere returns normally")
    void noStubText() {
        assertDoesNotThrow(() -> StubFailures.rethrow("{\"type\":\"summary\"}\n", "Report written\n"));
    }
}
