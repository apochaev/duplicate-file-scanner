package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.infra.port.ContentHasher;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Stage 3 of the detection pipeline: {@link FullHashConfirmer} turns {@link CandidateGroup}s into
 * {@link DuplicateSet}s using a full-content digest. Tested against {@link FakeContentHasher}, a
 * fake that returns configured digests and records every {@code fullHash} call, per CLAUDE.md's
 * rule to test {@code application} logic against a fake rather than the real filesystem. Per
 * design.md's checklist item 12, the near-duplicate scenario (AC-2) is proven here with a fake
 * returning different configured digests for the two paths; real bytes belong only to the adapter
 * test ({@link us.pochaev.dfs.scanner.infra.adapter.NioContentHasherTest}).
 *
 * <p>Every test here constructs {@link FullHashConfirmer} and calls {@link
 * FullHashConfirmer#confirm}, which is currently stubbed to throw {@code
 * UnsupportedOperationException("not implemented: DFS-003")} unconditionally, before any argument
 * is inspected. That is why every test below fails red for that one reason today.
 */
class FullHashConfirmerTest {

    private static final Instant WHEN = Instant.parse("2024-01-01T10:00:00Z");

    // --- AC-1: identical files form one duplicate set ---

    @Test
    @DisplayName("DFS-003.AC-1 Identical files form a duplicate set")
    void identicalFilesFormADuplicateSet() {
        FileEntry a = entry("folder1/a.bin", 300 * 1024);
        FileEntry b = entry("folder2/b.bin", 300 * 1024);
        FakeContentHasher hasher = new FakeContentHasher()
                .withDigest(a.path(), "same-digest")
                .withDigest(b.path(), "same-digest");
        CandidateGroup group = new CandidateGroup(300 * 1024, List.of(a, b));

        FullHashConfirmer confirmer = new FullHashConfirmer(hasher);

        List<DuplicateSet> result = confirmer.confirm(List.of(group));

        assertEquals(1, result.size());
        DuplicateSet set = result.getFirst();
        assertEquals(300 * 1024, set.size());
        assertEquals("same-digest", set.digest());
        assertEquals(List.of(a, b), set.files());
    }

    @Test
    @DisplayName("DFS-003.AC-1 Small files go straight to the full hash")
    void smallFilesGoStraightToTheFullHash() {
        FileEntry a = entry("a.bin", 1024);
        FileEntry b = entry("b.bin", 1024);
        FakeContentHasher hasher = new FakeContentHasher()
                .withDigest(a.path(), "same-digest")
                .withDigest(b.path(), "same-digest");
        CandidateGroup group = new CandidateGroup(1024, List.of(a, b));

        FullHashConfirmer confirmer = new FullHashConfirmer(hasher);

        List<DuplicateSet> result = confirmer.confirm(List.of(group));

        assertEquals(1, result.size());
        assertEquals(List.of(a, b), result.getFirst().files());
    }

    @Test
    @DisplayName("DFS-003.AC-1, DFS-003.AC-3 Only the identical files form a set")
    void onlyTheIdenticalFilesFormASet() {
        FileEntry a = entry("a.bin", 300 * 1024);
        FileEntry b = entry("b.bin", 300 * 1024);
        FileEntry c = entry("c.bin", 300 * 1024);
        FakeContentHasher hasher = new FakeContentHasher()
                .withDigest(a.path(), "same-digest")
                .withDigest(b.path(), "same-digest")
                .withDigest(c.path(), "different-digest");
        CandidateGroup group = new CandidateGroup(300 * 1024, List.of(a, b, c));

        FullHashConfirmer confirmer = new FullHashConfirmer(hasher);

        List<DuplicateSet> result = confirmer.confirm(List.of(group));

        assertEquals(1, result.size());
        assertEquals(List.of(a, b), result.getFirst().files());
    }

    // --- AC-2, AC-3: files differing anywhere never share a set ---

    @Test
    @DisplayName("DFS-003.AC-2, DFS-003.AC-3 Files differing only in the middle are separated")
    void filesDifferingOnlyInTheMiddleAreSeparated() {
        FileEntry a = entry("a.bin", 300 * 1024);
        FileEntry b = entry("b.bin", 300 * 1024);
        FakeContentHasher hasher = new FakeContentHasher()
                .withDigest(a.path(), "digest-a")
                .withDigest(b.path(), "digest-b");
        CandidateGroup group = new CandidateGroup(300 * 1024, List.of(a, b));

        FullHashConfirmer confirmer = new FullHashConfirmer(hasher);

        List<DuplicateSet> result = confirmer.confirm(List.of(group));

        assertEquals(List.of(), result);
    }

    @Test
    @DisplayName("DFS-003.AC-2 A group splits into two sets")
    void groupSplitsIntoTwoSets() {
        FileEntry a = entry("a.bin", 300 * 1024);
        FileEntry b = entry("b.bin", 300 * 1024);
        FileEntry c = entry("c.bin", 300 * 1024);
        FileEntry d = entry("d.bin", 300 * 1024);
        FakeContentHasher hasher = new FakeContentHasher()
                .withDigest(a.path(), "digest-ac")
                .withDigest(c.path(), "digest-ac")
                .withDigest(b.path(), "digest-bd")
                .withDigest(d.path(), "digest-bd");
        CandidateGroup group = new CandidateGroup(300 * 1024, List.of(a, b, c, d));

        FullHashConfirmer confirmer = new FullHashConfirmer(hasher);

        List<DuplicateSet> result = confirmer.confirm(List.of(group));

        assertEquals(2, result.size());
        assertEquals(List.of(a, c), result.get(0).files());
        assertEquals(List.of(b, d), result.get(1).files());
    }

    // --- AC-4: members hashed in ascending path order ---

    @Test
    @DisplayName("DFS-003.AC-4 Files are hashed in path order")
    void filesAreHashedInPathOrder() {
        FileEntry z = entry("z.bin", 300 * 1024);
        FileEntry a = entry("a.bin", 300 * 1024);
        FakeContentHasher hasher = new FakeContentHasher()
                .withDigest(a.path(), "same-digest")
                .withDigest(z.path(), "same-digest");
        // Deliberately out of path order.
        CandidateGroup group = new CandidateGroup(300 * 1024, List.of(z, a));

        FullHashConfirmer confirmer = new FullHashConfirmer(hasher);

        confirmer.confirm(List.of(group));

        assertEquals(List.of(a.path(), z.path()), hasher.calls());
    }

    // --- AC-5: output order follows the input and the smallest path ---

    @Test
    @DisplayName("DFS-003.AC-5 Output order follows the input and the smallest path")
    void outputOrderFollowsTheInputAndSmallestPath() {
        FileEntry xb = entry("x/b", 300 * 1024);
        FileEntry xd = entry("x/d", 300 * 1024);
        FileEntry xa = entry("x/a", 300 * 1024);
        FileEntry xc = entry("x/c", 300 * 1024);
        FileEntry ya = entry("y/a", 300 * 1024);
        FileEntry yb = entry("y/b", 300 * 1024);
        FakeContentHasher hasher = new FakeContentHasher()
                .withDigest(xb.path(), "digest-bd")
                .withDigest(xd.path(), "digest-bd")
                .withDigest(xa.path(), "digest-ac")
                .withDigest(xc.path(), "digest-ac")
                .withDigest(ya.path(), "digest-y")
                .withDigest(yb.path(), "digest-y");
        CandidateGroup firstGroup = new CandidateGroup(300 * 1024, List.of(xb, xd, xa, xc));
        CandidateGroup secondGroup = new CandidateGroup(300 * 1024, List.of(ya, yb));

        FullHashConfirmer confirmer = new FullHashConfirmer(hasher);

        List<DuplicateSet> result = confirmer.confirm(List.of(firstGroup, secondGroup));

        assertEquals(3, result.size());
        assertEquals(List.of(xa, xc), result.get(0).files());
        assertEquals(List.of(xb, xd), result.get(1).files());
        assertEquals(List.of(ya, yb), result.get(2).files());
    }

    // --- AC-7: an unreadable file is dropped, not its whole group ---

    @Test
    @DisplayName("DFS-003.AC-7 An unreadable file is dropped, not its whole group")
    void unreadableFileIsDroppedNotItsWholeGroup() {
        FileEntry a = entry("a.bin", 300 * 1024);
        FileEntry b = entry("b.bin", 300 * 1024);
        FileEntry c = entry("c.bin", 300 * 1024);
        FakeContentHasher hasher = new FakeContentHasher()
                .withDigest(a.path(), "same-digest")
                .withDigest(b.path(), "same-digest")
                .withDigest(c.path(), "same-digest")
                .unreadable(c.path());
        CandidateGroup group = new CandidateGroup(300 * 1024, List.of(a, b, c));

        FullHashConfirmer confirmer = new FullHashConfirmer(hasher);

        List<DuplicateSet> result = confirmer.confirm(List.of(group));

        assertEquals(1, result.size());
        assertEquals(List.of(a, b), result.getFirst().files());
    }

    @Test
    @DisplayName("DFS-003.AC-7 An unreadable file leaves its group with fewer than two members")
    void unreadableFileLeavesGroupWithFewerThanTwoMembers() {
        FileEntry a = entry("a.bin", 300 * 1024);
        FileEntry b = entry("b.bin", 300 * 1024);
        FakeContentHasher hasher = new FakeContentHasher()
                .withDigest(a.path(), "same-digest")
                .withDigest(b.path(), "same-digest")
                .unreadable(b.path());
        CandidateGroup group = new CandidateGroup(300 * 1024, List.of(a, b));

        FullHashConfirmer confirmer = new FullHashConfirmer(hasher);

        List<DuplicateSet> result = confirmer.confirm(List.of(group));

        assertEquals(List.of(), result);
    }

    // --- AC-10: the stage does not check a file's size against the scan's ---

    @Test
    @DisplayName("DFS-003.AC-10 Copies that all changed identically since the scan still form a set with the scan-time size")
    void copiesChangedIdenticallyStillFormASetWithScanTimeSize() {
        FileEntry a = entry("a.bin", 300 * 1024);
        FileEntry b = entry("b.bin", 300 * 1024);
        // Both files have since had the same content appended; the hasher returns equal digests
        // for their current content, which the group's FileEntry values (still 300 KB) predate.
        FakeContentHasher hasher = new FakeContentHasher()
                .withDigest(a.path(), "same-digest-after-append")
                .withDigest(b.path(), "same-digest-after-append");
        CandidateGroup group = new CandidateGroup(300 * 1024, List.of(a, b));

        FullHashConfirmer confirmer = new FullHashConfirmer(hasher);

        List<DuplicateSet> result = confirmer.confirm(List.of(group));

        assertEquals(1, result.size());
        assertEquals(300 * 1024, result.getFirst().size());
        assertEquals(List.of(a, b), result.getFirst().files());
    }

    private static FileEntry entry(String path, long size) {
        return new FileEntry(Path.of(path), size, WHEN);
    }

    /**
     * A fake {@link ContentHasher}: returns the digest configured for a path (or throws a
     * simulated {@link IOException} for a path marked {@link #unreadable}), and records every
     * {@link #fullHash} call's path so a test can assert order. {@link #sampleHash} is not used
     * by this stage, so it throws if a test accidentally reaches it.
     */
    private static final class FakeContentHasher implements ContentHasher {

        private final Map<Path, String> digests = new HashMap<>();
        private final Set<Path> unreadable = new HashSet<>();
        private final List<Path> calls = new ArrayList<>();

        FakeContentHasher withDigest(Path file, String digest) {
            digests.put(file, digest);
            return this;
        }

        FakeContentHasher unreadable(Path file) {
            unreadable.add(file);
            return this;
        }

        List<Path> calls() {
            return List.copyOf(calls);
        }

        @Override
        public String sampleHash(Path file, int sampleSize) {
            throw new UnsupportedOperationException("sampleHash is not used by the full-hash stage");
        }

        @Override
        public String fullHash(Path file) throws IOException {
            calls.add(file);
            if (unreadable.contains(file)) {
                throw new IOException("simulated unreadable file: " + file);
            }
            String digest = digests.get(file);
            if (digest == null) {
                throw new AssertionError("no digest configured for " + file);
            }
            return digest;
        }
    }
}
