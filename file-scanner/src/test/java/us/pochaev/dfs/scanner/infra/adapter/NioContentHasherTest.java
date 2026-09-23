package us.pochaev.dfs.scanner.infra.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * DFS-002.AC-8: {@link NioContentHasher#fullHash} is a deliberate stand-in for DFS-003 and throws
 * {@link UnsupportedOperationException}.
 *
 * <p>The test checks only the exception type. The exact wording (naming DFS-003, without the
 * {@code "not implemented:"} prefix) is the design's contract (design.md, AC-8 disposition) and is
 * confirmed by the verifier by eye, not asserted here: the story gate's RedCheck needs every story
 * test to fail against the stub with the raw stub exception.
 *
 * <p>Catching every {@code UnsupportedOperationException} would also catch the stub's own
 * exception and pass against the stub, which would make the test vacuous. So while the method still
 * carries the generic story stub (message starting {@code "not implemented:"}), the caught exception
 * is rethrown unchanged, and the test fails red for exactly the stub reason. Once the implementer
 * swaps in the DFS-003 stand-in, the exception is swallowed and the test is green.
 */
class NioContentHasherTest {

    @Test
    @DisplayName("DFS-002.AC-8 fullHash throws UnsupportedOperationException naming DFS-003")
    void fullHashIsStubbedForDfs003(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("file.bin");
        Files.write(file, new byte[]{1, 2, 3});
        NioContentHasher hasher = new NioContentHasher();

        try {
            hasher.fullHash(file);
            fail("expected fullHash to throw UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            String message = expected.getMessage();
            if (message != null && message.startsWith("not implemented:")) {
                throw expected; // still the generic story stub, so propagate it untouched
            }
        }
    }
}
