package us.pochaev.dfs.scanner.infra.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DFS-002.AC-1: {@link NioContentHasher#sampleHash}, the real head/tail sample adapter, tested
 * against real bytes in a {@code @TempDir}.
 *
 * <p>DFS-003.AC-8: {@link NioContentHasher#fullHash}, the real full-content SHA-256 adapter,
 * tested the same way: a known input against its known digest, and a discrimination test proving
 * the adapter tells files apart by a single byte anywhere in the content, not only that identical
 * content produces matching digests (CLAUDE.md, found on DFS-002's {@code sampleHash}: a
 * constant-returning implementation would otherwise pass a suite proving only the matching case).
 */
class NioContentHasherTest {

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
     * DFS-003.AC-8: a known three-byte input gives its known SHA-256 digest.
     */
    @Test
    @DisplayName("DFS-003.AC-8 The real adapter computes the SHA-256 of the whole file")
    void fullHashComputesTheKnownDigestOfAKnownInput(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("abc.bin");
        Files.write(file, "abc".getBytes(StandardCharsets.US_ASCII));
        NioContentHasher hasher = new NioContentHasher();

        String digest = hasher.fullHash(file);

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", digest);
    }

    /**
     * DFS-003.AC-8: equal content gives equal digests, and content differing only in the middle
     * byte of a 300 KB file (past the 128 KB sample threshold) gives a different digest, using
     * real bytes in a {@code @TempDir} (never committed), per CLAUDE.md's rule that
     * content-dependent adapter behavior needs real bytes, and per the discrimination rule: a
     * suite proving only the matching case cannot catch a constant-returning implementation.
     */
    @Test
    @DisplayName("DFS-003.AC-8 The real adapter tells files apart by a single byte anywhere")
    void fullHashTellsFilesApartByASingleByteAnywhere(@TempDir Path dir) throws IOException {
        int size = 300 * 1024; // over the 128 KB sample threshold, so the whole file matters
        Path fileA = dir.resolve("a.bin");
        Path fileB = dir.resolve("b.bin");
        Path fileC = dir.resolve("c.bin");
        writePattern(fileA, size, -1);
        writePattern(fileB, size, -1);
        writePattern(fileC, size, size / 2); // differs only in the middle byte

        NioContentHasher hasher = new NioContentHasher();

        String digestA = hasher.fullHash(fileA);
        String digestB = hasher.fullHash(fileB);
        String digestC = hasher.fullHash(fileC);

        assertEquals(digestA, digestB);
        assertNotEquals(digestA, digestC);
    }

    /**
     * DFS-003.AC-9: DFS-002's stand-in test asserted that {@code fullHash} throws; it is gone,
     * and the observable contract that replaces it is that {@code fullHash} on a real file
     * returns a digest (a 64-character lowercase hexadecimal SHA-256) rather than throwing. The
     * stub's exception is deliberately not caught, so this test is red against the stub and
     * green once the implement phase lands (the scenario's "green gate passes with fullHash
     * returning a digest").
     */
    @Test
    @DisplayName("DFS-003.AC-9 fullHash returns a digest instead of throwing")
    void fullHashReturnsADigestInsteadOfThrowing(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("any.bin");
        writePattern(file, 1000, -1);
        NioContentHasher hasher = new NioContentHasher();

        String digest = hasher.fullHash(file);

        assertTrue(digest != null && digest.matches("[0-9a-f]{64}"),
                "expected a 64-character lowercase hex SHA-256 digest, got: " + digest);
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
