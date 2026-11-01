package us.pochaev.dfs.scanner.infra.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.pochaev.dfs.scanner.infra.port.ContentComparator;
import us.pochaev.dfs.scanner.infra.port.ContentComparator.Comparison;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The NIO content comparator (DFS-034) on real files, with a small chunk to span several chunks. */
class NioContentComparatorTest {

    @TempDir
    Path t;

    private Path file(String name, byte[] content) throws IOException {
        Path p = t.resolve(name);
        Files.write(p, content);
        return p;
    }

    private static byte[] bytes(int n) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = (byte) (i * 13 + 1);
        }
        return b;
    }

    @Test
    @DisplayName("DFS-034.AC-1 Identical files over several chunks are identical, progress rises to the size")
    void identicalOverSeveralChunks() throws IOException {
        Path a = file("a", bytes(100));
        Path b = file("b", bytes(100));
        List<Long> progress = new ArrayList<>();

        Comparison c = new NioContentComparator(16).compare(a, b, progress::add, () -> false);

        assertEquals(new Comparison.Identical(), c);
        assertTrue(progress.size() > 1);
        long prev = 0;
        for (long p : progress) {
            assertTrue(p > prev);
            prev = p;
        }
        assertEquals(100L, progress.get(progress.size() - 1));
    }

    @Test
    @DisplayName("DFS-034.AC-1 Two empty files are identical")
    void emptyFilesAreIdentical() throws IOException {
        Comparison c = new NioContentComparator(16).compare(file("a", new byte[0]), file("b", new byte[0]),
                n -> { }, () -> false);

        assertEquals(new Comparison.Identical(), c);
    }

    @Test
    @DisplayName("DFS-034.AC-2 The first differing byte is located in a later chunk, and other inputs differ")
    void firstDifferingByteIsLocated() throws IOException {
        byte[] other = bytes(100);
        other[70] ^= 0x7f;
        other[90] ^= 0x7f;
        NioContentComparator cmp = new NioContentComparator(16);

        Comparison differs = cmp.compare(file("a", bytes(100)), file("b", other), n -> { }, () -> false);
        Comparison same = cmp.compare(file("c", bytes(100)), file("d", bytes(100)), n -> { }, () -> false);

        assertEquals(new Comparison.Differs(70), differs);
        assertEquals(new Comparison.Identical(), same);
    }

    @Test
    @DisplayName("DFS-034.AC-2 A file that ends first differs at the offset where it ends")
    void shorterFileDiffersAtItsEnd() throws IOException {
        Comparison c = new NioContentComparator(16).compare(file("a", bytes(40)), file("b", bytes(37)),
                n -> { }, () -> false);

        assertEquals(new Comparison.Differs(37), c);
    }

    @Test
    @DisplayName("DFS-034.AC-7 A cancel is honoured before the next chunk")
    void cancelIsHonouredBetweenChunks() throws IOException {
        Path a = file("a", bytes(100));
        Path b = file("b", bytes(100));
        AtomicBoolean cancel = new AtomicBoolean();
        List<Long> progress = new ArrayList<>();

        Comparison c = new NioContentComparator(16).compare(a, b, n -> {
            progress.add(n);
            cancel.set(true);
        }, cancel::get);

        assertEquals(new Comparison.Cancelled(), c);
        assertEquals(List.of(16L), progress);
    }

    @Test
    @DisplayName("DFS-034.AC-4 A symlink is not opened")
    void symlinkIsNotOpened() throws IOException {
        Path real = file("real", bytes(10));
        Path link = t.resolve("link");
        try {
            Files.createSymbolicLink(link, real);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            assumeTrue(false, "symlinks not allowed here: " + e);
        }
        Path other = file("other", bytes(10));
        ContentComparator cmp = new NioContentComparator(16);

        try {
            Comparison c = cmp.compare(link, other, n -> { }, () -> false);
            fail("expected an IOException, got " + c);
        } catch (IOException expected) {
            // neither path is followed
        }
    }
}
