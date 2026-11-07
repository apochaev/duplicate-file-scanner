package us.pochaev.dfs.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * The folder the browser flows scan (DFS-053 decision 4), written in a temp folder so nothing platform-specific is
 * committed:
 *
 * <pre>
 * root/keep/shared-a.bin     3000 bytes of 'a'     root/extra/shared-a.bin   the same content
 * root/keep/shared-b.bin     2000 bytes of 'b'     root/extra/shared-b.bin   the same content
 * root/keep/only-keep.bin    1000 bytes of 'c'     root/extra/only-extra.bin 1500 bytes of 'd'
 * root/keep/shared-a-link.bin  a hard link to root/keep/shared-a.bin (when the platform allows it)
 * </pre>
 *
 * So the scan finds two duplicate sets of two physical copies each; the hard link is no third copy of the first.
 */
record BrowserFixture(Path root, Path keep, Path extra, boolean hardLinked) {

    static BrowserFixture create(Path temp) {
        try {
            Path root = temp.toRealPath().resolve("root");
            Path keep = Files.createDirectories(root.resolve("keep"));
            Path extra = Files.createDirectories(root.resolve("extra"));
            write(keep.resolve("shared-a.bin"), 'a', 3000);
            write(extra.resolve("shared-a.bin"), 'a', 3000);
            write(keep.resolve("shared-b.bin"), 'b', 2000);
            write(extra.resolve("shared-b.bin"), 'b', 2000);
            write(keep.resolve("only-keep.bin"), 'c', 1000);
            write(extra.resolve("only-extra.bin"), 'd', 1500);
            return new BrowserFixture(root, keep, extra, hardLink(keep.resolve("shared-a-link.bin"), keep.resolve("shared-a.bin")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Where a test expects a copy of the first shared file. */
    Path sharedAIn(Path folder) {
        return folder.resolve("shared-a.bin");
    }

    private static void write(Path file, char fill, int length) throws IOException {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) fill);
        Files.write(file, bytes);
    }

    /** False when the platform or its privileges do not allow a hard link; the tests then skip what needs one. */
    private static boolean hardLink(Path link, Path existing) {
        try {
            Files.createLink(link, existing);
            return true;
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            return false;
        }
    }
}
