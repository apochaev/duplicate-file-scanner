package us.pochaev.dfs.scanner.infra.adapter;

import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The committed fixture tree (src/test/resources/fixtures) holds only what git can store
 * portably: regular files and directories. Everything platform-specific (links, junctions,
 * hidden or read-only files, unreadable folders) is created at test time, and the test is
 * skipped when the platform or privileges do not allow it.
 */
final class Fixtures {

    static final String LONG_NAME = "x".repeat(80) + ".txt";

    /** Every file in the committed fixture tree, relative to its root, with '/' separators. */
    static final Set<String> FILES = Set.of(
            "basic/a.txt",
            "basic/b.txt",
            "basic/unique.txt",
            "empty/zero-a.dat",
            "empty/zero-b.dat",
            "folders/folder1/notes.txt",
            "folders/folder1/photo.jpg",
            "folders/folder2/notes.txt",
            "folders/folder2/only-here.txt",
            "folders/folder2/photo-copy.jpg",
            "names/-leading-dash.txt",
            "names/.dotfile",
            "names/UPPERCASE.TXT",
            "names/café.txt",
            "names/multi.part.name.tar.gz",
            "names/no-extension",
            "names/odd chars [1] (2) & #3 %4 'q'.txt",
            "names/with space.txt",
            "names/" + LONG_NAME,
            "names/файл.txt",
            "names/日本語.txt",
            "nested/level1/level2/level3/level4/level5/deep.txt",
            "nested/level1/sibling.txt",
            "same-size/first.bin",
            "same-size/second.bin");

    private Fixtures() {
    }

    static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    /** Copies the committed fixture tree into {@code target} (which must already exist). */
    static Path copyInto(Path target) throws IOException {
        Path source;
        try {
            source = Path.of(Fixtures.class.getResource("/fixtures").toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination);
                }
            }
        }
        return target;
    }

    static void createSymlinkOrSkip(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | FileSystemException e) {
            Assumptions.abort("symbolic links are not available here: " + e.getMessage());
        }
    }

    static void createHardLinkOrSkip(Path link, Path existing) throws IOException {
        try {
            Files.createLink(link, existing);
        } catch (UnsupportedOperationException | FileSystemException e) {
            Assumptions.abort("hard links are not available here: " + e.getMessage());
        }
    }

    /** Windows only: there is no Java API for junctions. */
    static void createJunction(Path link, Path target) throws IOException {
        run("cmd", "/c", "mklink", "/J", link.toString(), target.toString());
    }

    /**
     * Makes {@code dir} impossible to list, skipping the test if that cannot be achieved (for
     * example when running with elevated privileges). Returns an action that undoes it; call it
     * before the temp directory is cleaned up.
     */
    static Runnable denyListing(Path dir) throws IOException {
        Runnable restore;
        if (isWindows()) {
            run("icacls", dir.toString(), "/deny", "*S-1-1-0:(RD)");
            restore = () -> runQuietly("icacls", dir.toString(), "/remove:d", "*S-1-1-0");
        } else {
            Files.setPosixFilePermissions(dir, Set.of());
            restore = () -> {
                try {
                    Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
                } catch (IOException ignored) {
                    // best effort; the temp directory cleanup reports any leftover
                }
            };
        }
        try (DirectoryStream<Path> ignored = Files.newDirectoryStream(dir)) {
            restore.run();
            Assumptions.abort("directory listing could not be blocked (elevated privileges?)");
        } catch (IOException blockedAsIntended) {
            // expected
        }
        return restore;
    }

    private static void run(String... command) throws IOException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            if (process.waitFor() != 0) {
                throw new IOException(String.join(" ", command) + " failed: " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted running " + String.join(" ", command), e);
        }
    }

    private static void runQuietly(String... command) {
        try {
            run(command);
        } catch (IOException ignored) {
            // best effort
        }
    }
}
