package us.pochaev.dfs.scanner.infra.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    /**
     * DFS-002.AC-1, real-adapter half: {@link PartialHashFilterTest} proves the separation
     * behavior against a fake that is simply told to return different digests for different
     * files. That alone would also pass if {@code sampleHash} returned a constant for every
     * file, so this proves the real adapter itself produces a different digest when two
     * same-size files differ within the sampled head, using real bytes in a {@code @TempDir}
     * (never committed), per CLAUDE.md's rule that content-dependent adapter behavior needs
     * real bytes.
     */
    @Test
    @DisplayName("DFS-002.AC-1 sampleHash gives a different digest when files differ in the head")
    void sampleHashDiffersWhenHeadDiffers(@TempDir Path dir) throws IOException {
        int size = 1000;
        int sampleSize = 200;
        Path fileA = dir.resolve("a.bin");
        Path fileB = dir.resolve("b.bin");
        writePattern(fileA, size, -1);
        writePattern(fileB, size, 0); // differs at the first byte, inside the sampled head

        NioContentHasher hasher = new NioContentHasher();

        String digestA = hasher.sampleHash(fileA, sampleSize);
        String digestB = hasher.sampleHash(fileB, sampleSize);

        assertNotEquals(digestA, digestB);
    }

    /**
     * DFS-002.AC-1, real-adapter half: same as above, but the differing byte is inside the
     * sampled tail instead of the head.
     */
    @Test
    @DisplayName("DFS-002.AC-1 sampleHash gives a different digest when files differ in the tail")
    void sampleHashDiffersWhenTailDiffers(@TempDir Path dir) throws IOException {
        int size = 1000;
        int sampleSize = 200;
        Path fileA = dir.resolve("a.bin");
        Path fileB = dir.resolve("b.bin");
        writePattern(fileA, size, -1);
        writePattern(fileB, size, size - 1); // differs at the last byte, inside the sampled tail

        NioContentHasher hasher = new NioContentHasher();

        String digestA = hasher.sampleHash(fileA, sampleSize);
        String digestB = hasher.sampleHash(fileB, sampleSize);

        assertNotEquals(digestA, digestB);
    }

    /**
     * Writes {@code size} bytes of a repeating pattern, optionally flipping one byte at
     * {@code diffOffset} (pass -1 for no difference). Generated at test time; nothing is
     * committed.
     */
    private static void writePattern(Path path, int size, int diffOffset) throws IOException {
        try (OutputStream out = Files.newOutputStream(path)) {
            for (int i = 0; i < size; i++) {
                byte b = (byte) i;
                if (i == diffOffset) {
                    b = (byte) (b + 1);
                }
                out.write(b);
            }
        }
    }
}
