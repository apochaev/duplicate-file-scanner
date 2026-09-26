package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.PhysicalCopy;
import us.pochaev.dfs.scanner.infra.port.FileIdentity;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Stage 4 of the detection pipeline: {@link HardLinkDetector} regroups each confirmed {@link
 * DuplicateSet}'s files into {@link PhysicalCopy physical copies}, so hard links to one file are
 * recognised as one copy rather than duplicates of each other (CLAUDE.md: "hard links are not
 * duplicates"). Tested against {@link FakeFileIdentity}, a hand-written fake that is told which
 * path pairs are the same physical file and which paths are unreadable, and records every call in
 * order, per CLAUDE.md's rule to test {@code application} logic against a fake rather than the
 * real filesystem.
 *
 * <p>Every test here constructs {@link HardLinkDetector} and calls {@link
 * HardLinkDetector#detect}, which are currently stubbed to throw {@code
 * UnsupportedOperationException("not implemented: DFS-004")} unconditionally, before any argument
 * is inspected. That is why every test below fails red for that one reason today.
 */
class HardLinkDetectorTest {

    private static final Instant WHEN = Instant.parse("2024-01-01T10:00:00Z");

    // --- AC-2: a set that is all links of one physical file produces no DuplicateSet ---

    @Test
    @DisplayName("DFS-004.AC-2 Two hard links to one file are not duplicates")
    void twoHardLinksToOneFileAreNotDuplicates() {
        FileEntry a = entry("folder1/a.bin", 10 * 1024);
        FileEntry b = entry("folder2/b.bin", 10 * 1024);
        FakeFileIdentity identity = new FakeFileIdentity().sameFile(a.path(), b.path());
        DuplicateSet input = new DuplicateSet(10 * 1024, "digest", List.of(a, b));

        HardLinkDetector detector = new HardLinkDetector(identity);

        List<DuplicateSet> result = detector.detect(List.of(input));

        assertEquals(List.of(), result);
    }

    // --- AC-1, AC-3: a hard-linked pair counts as one copy, alongside an independent copy ---

    @Test
    @DisplayName("DFS-004.AC-1, DFS-004.AC-3 A hard-linked pair counts as one copy")
    void hardLinkedPairCountsAsOneCopy() {
        FileEntry a = entry("a.bin", 10 * 1024);
        FileEntry b = entry("b.bin", 10 * 1024);
        FileEntry c = entry("c.bin", 10 * 1024);
        FakeFileIdentity identity = new FakeFileIdentity().sameFile(a.path(), b.path());
        DuplicateSet input = new DuplicateSet(10 * 1024, "digest", List.of(a, b, c));

        HardLinkDetector detector = new HardLinkDetector(identity);

        List<DuplicateSet> result = detector.detect(List.of(input));

        assertEquals(1, result.size());
        DuplicateSet set = result.getFirst();
        assertEquals(List.of(a, b, c), set.files());
        assertEquals(List.of(new PhysicalCopy(List.of(a, b)), new PhysicalCopy(List.of(c))), set.copies());
    }

    // --- AC-3: independent identical files remain separate copies, one link each ---

    @Test
    @DisplayName("DFS-004.AC-3 Independent identical files remain separate copies")
    void independentIdenticalFilesRemainSeparateCopies() {
        FileEntry a = entry("a.bin", 10 * 1024);
        FileEntry b = entry("b.bin", 10 * 1024);
        FileEntry c = entry("c.bin", 10 * 1024);
        FakeFileIdentity identity = new FakeFileIdentity(); // no pair configured as the same file
        DuplicateSet input = new DuplicateSet(10 * 1024, "digest", List.of(a, b, c));

        HardLinkDetector detector = new HardLinkDetector(identity);

        List<DuplicateSet> result = detector.detect(List.of(input));

        assertEquals(1, result.size());
        DuplicateSet set = result.getFirst();
        assertEquals(
                List.of(new PhysicalCopy(List.of(a)), new PhysicalCopy(List.of(b)), new PhysicalCopy(List.of(c))),
                set.copies());
    }

    // --- AC-4: identity is checked only inside a set, ascending, against each copy's first link ---

    @Test
    @DisplayName("DFS-004.AC-4 Identity is only checked inside a set, against each copy's first link")
    void identityIsOnlyCheckedInsideASetAgainstEachCopysFirstLink() {
        FileEntry xa = entry("x/a", 10 * 1024);
        FileEntry xb = entry("x/b", 10 * 1024);
        FileEntry xc = entry("x/c", 10 * 1024);
        FileEntry yd = entry("y/d", 10 * 1024);
        FileEntry ye = entry("y/e", 10 * 1024);
        FakeFileIdentity identity = new FakeFileIdentity().sameFile(xa.path(), xb.path());
        DuplicateSet firstSet = new DuplicateSet(10 * 1024, "digest-x", List.of(xa, xb, xc));
        DuplicateSet secondSet = new DuplicateSet(10 * 1024, "digest-y", List.of(yd, ye));

        HardLinkDetector detector = new HardLinkDetector(identity);

        detector.detect(List.of(firstSet, secondSet));

        assertEquals(
                List.of(
                        new Call(xb.path(), xa.path()),
                        new Call(xc.path(), xa.path()),
                        new Call(ye.path(), yd.path())),
                identity.calls());
    }

    // --- AC-5: output order follows the input, the first path, and path order within a copy ---

    @Test
    @DisplayName("DFS-004.AC-5 Output order follows the input, the first path and the path order")
    void outputOrderFollowsTheInputTheFirstPathAndThePathOrder() {
        FileEntry xa = entry("x/a", 1000);
        FileEntry xb = entry("x/b", 1000);
        FileEntry xc = entry("x/c", 1000);
        FileEntry xd = entry("x/d", 1000);
        FileEntry s2a = entry("s2/a", 2000);
        FileEntry s2b = entry("s2/b", 2000);
        FileEntry s3a = entry("s3/a", 3000);
        FileEntry s3b = entry("s3/b", 3000);
        FakeFileIdentity identity = new FakeFileIdentity()
                .sameFile(xa.path(), xc.path()) // x/a and x/c are one physical file
                .sameFile(s2a.path(), s2b.path()); // S2 is all links of one file
        // S1's files arrive out of path order.
        DuplicateSet s1 = new DuplicateSet(1000, "digest-1", List.of(xc, xa, xd, xb));
        DuplicateSet s2 = new DuplicateSet(2000, "digest-2", List.of(s2a, s2b));
        DuplicateSet s3 = new DuplicateSet(3000, "digest-3", List.of(s3a, s3b));

        HardLinkDetector detector = new HardLinkDetector(identity);

        List<DuplicateSet> result = detector.detect(List.of(s1, s2, s3));

        DuplicateSet expectedS1 = new DuplicateSet(1000, "digest-1", List.of(xa, xb, xc, xd), List.of(
                new PhysicalCopy(List.of(xa, xc)),
                new PhysicalCopy(List.of(xb)),
                new PhysicalCopy(List.of(xd))));
        DuplicateSet expectedS3 = new DuplicateSet(3000, "digest-3", List.of(s3a, s3b), List.of(
                new PhysicalCopy(List.of(s3a)),
                new PhysicalCopy(List.of(s3b))));
        assertEquals(List.of(expectedS1, expectedS3), result);
    }

    // --- AC-8: a file whose identity check throws is dropped, not its whole set ---

    @Test
    @DisplayName("DFS-004.AC-8 A file whose identity cannot be checked is dropped, not its set")
    void fileWhoseIdentityCannotBeCheckedIsDroppedNotItsSet() {
        FileEntry a = entry("a.bin", 10 * 1024);
        FileEntry b = entry("b.bin", 10 * 1024);
        FileEntry c = entry("c.bin", 10 * 1024);
        // b is not the first path in ascending order, so it is checked as a candidate and never
        // becomes a copy's first link (design.md's "Open design notes").
        FakeFileIdentity identity = new FakeFileIdentity().unreadable(b.path());
        DuplicateSet input = new DuplicateSet(10 * 1024, "digest", List.of(a, b, c));

        HardLinkDetector detector = new HardLinkDetector(identity);

        List<DuplicateSet> result = detector.detect(List.of(input));

        assertEquals(1, result.size());
        DuplicateSet set = result.getFirst();
        assertEquals(List.of(a, c), set.files());
        assertEquals(List.of(new PhysicalCopy(List.of(a)), new PhysicalCopy(List.of(c))), set.copies());
    }

    @Test
    @DisplayName("DFS-004.AC-8 Dropping an unreadable file leaves fewer than two copies")
    void droppingAnUnreadableFileLeavesFewerThanTwoCopies() {
        FileEntry a = entry("a.bin", 10 * 1024);
        FileEntry b = entry("b.bin", 10 * 1024);
        // b is not the first path, so it is checked (and fails) as a candidate.
        FakeFileIdentity identity = new FakeFileIdentity().unreadable(b.path());
        DuplicateSet input = new DuplicateSet(10 * 1024, "digest", List.of(a, b));

        HardLinkDetector detector = new HardLinkDetector(identity);

        List<DuplicateSet> result = detector.detect(List.of(input));

        assertEquals(List.of(), result);
    }

    private static FileEntry entry(String path, long size) {
        return new FileEntry(Path.of(path), size, WHEN);
    }

    /** One recorded call to {@link FileIdentity#isSameFile}, in argument order. */
    private record Call(Path a, Path b) {
    }

    /**
     * A fake {@link FileIdentity}: two paths are the same file only if configured as such with
     * {@link #sameFile} (symmetric), or if a path is marked {@link #unreadable}, in which case
     * any call naming it throws a simulated {@link IOException}. Records every call, in argument
     * order, for {@link #calls()} to assert on (AC-4).
     */
    private static final class FakeFileIdentity implements FileIdentity {

        private final Set<UnorderedPair> sameFilePairs = new HashSet<>();
        private final Set<Path> unreadable = new HashSet<>();
        private final List<Call> calls = new ArrayList<>();

        FakeFileIdentity sameFile(Path a, Path b) {
            sameFilePairs.add(new UnorderedPair(a, b));
            return this;
        }

        FakeFileIdentity unreadable(Path path) {
            unreadable.add(path);
            return this;
        }

        List<Call> calls() {
            return List.copyOf(calls);
        }

        @Override
        public boolean isSameFile(Path a, Path b) throws IOException {
            calls.add(new Call(a, b));
            if (unreadable.contains(a) || unreadable.contains(b)) {
                throw new IOException("simulated unreadable file: " + a + " or " + b);
            }
            if (a.equals(b)) {
                return true;
            }
            return sameFilePairs.contains(new UnorderedPair(a, b));
        }

        private record UnorderedPair(Path one, Path other) {
            private UnorderedPair {
                Objects.requireNonNull(one, "one");
                Objects.requireNonNull(other, "other");
            }

            @Override
            public boolean equals(Object obj) {
                if (!(obj instanceof UnorderedPair(Path otherOne, Path otherOther))) {
                    return false;
                }
                return (one.equals(otherOne) && other.equals(otherOther))
                        || (one.equals(otherOther) && other.equals(otherOne));
            }

            @Override
            public int hashCode() {
                return one.hashCode() ^ other.hashCode();
            }
        }
    }
}
