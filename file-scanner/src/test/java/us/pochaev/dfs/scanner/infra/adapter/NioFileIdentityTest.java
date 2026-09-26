package us.pochaev.dfs.scanner.infra.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DFS-004.AC-9: {@link NioFileIdentity#isSameFile}, on {@link Files#isSameFile}, tested against
 * real files in a {@code @TempDir}, per CLAUDE.md's rule that content- or filesystem-dependent
 * adapter behavior needs real bytes rather than a fake. The hard-link case creates its link at
 * test time with {@link Files#createLink} and skips via {@code Assumptions} when the platform or
 * privileges do not allow it ({@link Fixtures#createHardLinkOrSkip}).
 */
class NioFileIdentityTest {

    @Test
    @DisplayName("DFS-004.AC-9 Two hard links to one file are recognised as the same file")
    void twoHardLinksToOneFileAreRecognisedAsTheSameFile(@TempDir Path dir) throws IOException {
        Path a = dir.resolve("a.bin");
        writeBytes(a, 10 * 1024);
        Path b = dir.resolve("b.bin");
        Fixtures.createHardLinkOrSkip(b, a);

        NioFileIdentity identity = new NioFileIdentity();

        assertTrue(identity.isSameFile(a, b));
    }

    @Test
    @DisplayName("DFS-004.AC-9 Independent identical files are told apart")
    void independentIdenticalFilesAreToldApart(@TempDir Path dir) throws IOException {
        Path a = dir.resolve("a.bin");
        Path c = dir.resolve("c.bin");
        writeBytes(a, 10 * 1024);
        writeBytes(c, 10 * 1024);

        NioFileIdentity identity = new NioFileIdentity();

        assertFalse(identity.isSameFile(a, c));
    }

    /** Writes {@code size} bytes of a repeating pattern. Generated at test time; never committed. */
    private static void writeBytes(Path path, int size) throws IOException {
        try (OutputStream out = Files.newOutputStream(path)) {
            for (int i = 0; i < size; i++) {
                out.write((byte) i);
            }
        }
    }
}
