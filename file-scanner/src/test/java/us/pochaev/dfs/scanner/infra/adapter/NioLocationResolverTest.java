package us.pochaev.dfs.scanner.infra.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link NioLocationResolver} on real temporary files (DFS-005.AC-10): the one layer whose job is
 * to talk to the real filesystem (CLAUDE.md's fixture rules), so a link is created at test time and
 * the test is skipped through {@code Assumptions} when the platform does not allow it
 * ({@link Fixtures#createSymlinkOrSkip}, {@link Fixtures#createJunction} on Windows).
 * <p>
 * Every test here calls {@link NioLocationResolver#realPath}, which is currently stubbed to throw
 * {@code UnsupportedOperationException("not implemented: DFS-005")} unconditionally. That is why
 * every test below fails red for that one reason today: the tests expecting a checked exception
 * use a try/catch rather than {@code assertThrows}, so the stub's exception propagates uncaught
 * instead of being reported as this test's own {@code AssertionFailedError} (test-guide.md's L-013
 * warning).
 */
class NioLocationResolverTest {

    private final NioLocationResolver resolver = new NioLocationResolver();

    @Test
    @DisplayName("DFS-005.AC-10 A location given through a link resolves to its target")
    void locationGivenThroughLinkResolvesToTarget(@TempDir Path dir) throws IOException {
        Path target = Files.createDirectory(dir.resolve("target"));
        Path alias = dir.resolve("alias");
        if (Fixtures.isWindows()) {
            Fixtures.createJunction(alias, target);
        } else {
            Fixtures.createSymlinkOrSkip(alias, target);
        }

        Path result = resolver.realPath(alias);

        assertEquals(target.toRealPath(), result);
    }

    @Test
    @DisplayName("DFS-005.AC-10 A missing path throws NoSuchFileException")
    void missingPathThrowsNoSuchFileException(@TempDir Path dir) {
        Path missing = dir.resolve("missing");

        try {
            resolver.realPath(missing);
            fail("expected NoSuchFileException for a missing location");
        } catch (NoSuchFileException expected) {
            // expected once implemented
        } catch (IOException unexpected) {
            fail("expected NoSuchFileException but got " + unexpected);
        }
    }

    @Test
    @DisplayName("DFS-005.AC-10 A regular file throws NotDirectoryException")
    void regularFileThrowsNotDirectoryException(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("f.txt"), "content");

        try {
            resolver.realPath(file);
            fail("expected NotDirectoryException for a regular file");
        } catch (NotDirectoryException expected) {
            // expected once implemented
        } catch (IOException unexpected) {
            fail("expected NotDirectoryException but got " + unexpected);
        }
    }
}
