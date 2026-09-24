package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Stage 2 of the detection pipeline: {@link PartialHashFilter} splits {@link CandidateGroup}s by
 * a head-and-tail sample digest. Tested against {@link RecordingContentHasher}, a fake that
 * returns configured digests and records every call, per CLAUDE.md's rule to test {@code
 * application} logic against a fake rather than the real filesystem (the near-duplicate,
 * over-threshold scenarios AC-2 and AC-9 need real bytes instead and live in
 * {@link PartialHashFilterRealFileTest}).
 *
 * <p>Every test here constructs {@link PartialHashFilter} (or calls {@link
 * PartialHashFilter#filter}), which is currently stubbed to throw {@code
 * UnsupportedOperationException("not implemented: DFS-002")} unconditionally, before any
 * argument is inspected. That is why every test below fails red for that one reason today.
 */
class PartialHashFilterTest {

    private static final Instant WHEN = Instant.parse("2024-01-01T10:00:00Z");

    // --- AC-1: a sampled head/tail difference separates files ---

    @Test
    @DisplayName("DFS-002.AC-1 Files that differ in sampled bytes are separated")
    void filesThatDifferInSampledBytesAreSeparated() {
        FileEntry a = entry("a.bin", 200 * 1024);
        FileEntry b = entry("b.bin", 200 * 1024);
        RecordingContentHasher hasher = new RecordingContentHasher()
                .withDigest(a.path(), "digest-a")
                .withDigest(b.path(), "digest-b");
        CandidateGroup group = new CandidateGroup(200 * 1024, List.of(a, b));

        PartialHashFilter filter = new PartialHashFilter(hasher);

        List<CandidateGroup> result = filter.filter(List.of(group));

        assertEquals(List.of(), result);
    }

    // --- AC-3: identical files survive ---

    @Test
    @DisplayName("DFS-002.AC-3 Identical files survive the stage")
    void identicalFilesSurviveTheStage() {
        FileEntry a = entry("a.bin", 300 * 1024);
        FileEntry b = entry("b.bin", 300 * 1024);
        RecordingContentHasher hasher = new RecordingContentHasher()
                .withDigest(a.path(), "same-digest")
                .withDigest(b.path(), "same-digest");
        CandidateGroup group = new CandidateGroup(300 * 1024, List.of(a, b));

        PartialHashFilter filter = new PartialHashFilter(hasher);

        List<CandidateGroup> result = filter.filter(List.of(group));

        assertEquals(1, result.size());
        assertEquals(List.of(a, b), result.getFirst().files());
    }

    // --- AC-4: small files bypass the sample entirely ---

    @Test
    @DisplayName("DFS-002.AC-4 Small files bypass the sample entirely")
    void smallFilesBypassTheSampleEntirely() {
        FileEntry a = entry("a.bin", 100 * 1024);
        FileEntry b = entry("b.bin", 100 * 1024);
        RecordingContentHasher hasher = new RecordingContentHasher();
        CandidateGroup group = new CandidateGroup(100 * 1024, List.of(a, b));

        PartialHashFilter filter = new PartialHashFilter(hasher);

        List<CandidateGroup> result = filter.filter(List.of(group));

        assertTrue(hasher.calls().isEmpty(), "the hasher must not be asked for a sample");
        assertEquals(1, result.size());
        assertSame(group, result.getFirst());
    }

    // --- AC-5: groups of one are dropped after the stage ---

    @Test
    @DisplayName("DFS-002.AC-5 A group left with fewer than two survivors is dropped")
    void groupLeftWithFewerThanTwoSurvivorsIsDropped() {
        FileEntry a = entry("a.bin", 300 * 1024);
        FileEntry b = entry("b.bin", 300 * 1024);
        FileEntry c = entry("c.bin", 300 * 1024);
        RecordingContentHasher hasher = new RecordingContentHasher()
                .withDigest(a.path(), "same-digest")
                .withDigest(b.path(), "same-digest")
                .withDigest(c.path(), "different-digest");
        CandidateGroup group = new CandidateGroup(300 * 1024, List.of(a, b, c));

        PartialHashFilter filter = new PartialHashFilter(hasher);

        List<CandidateGroup> result = filter.filter(List.of(group));

        assertEquals(1, result.size());
        assertEquals(List.of(a, b), result.getFirst().files());
    }

    // --- AC-6: members are sampled in ascending path order ---

    @Test
    @DisplayName("DFS-002.AC-6 Members are sampled in ascending path order")
    void membersAreSampledInAscendingPathOrder() {
        FileEntry z = entry("z.bin", 200 * 1024);
        FileEntry a = entry("a.bin", 200 * 1024);
        RecordingContentHasher hasher = new RecordingContentHasher()
                .withDigest(a.path(), "same-digest")
                .withDigest(z.path(), "same-digest");
        // Deliberately out of path order.
        CandidateGroup group = new CandidateGroup(200 * 1024, List.of(z, a));

        PartialHashFilter filter = new PartialHashFilter(hasher);

        filter.filter(List.of(group));

        List<Path> sampledOrder = hasher.calls().stream().map(RecordingContentHasher.Call::file).toList();
        assertEquals(List.of(a.path(), z.path()), sampledOrder);
    }

    // --- AC-7: a file that becomes unreadable is dropped, not its whole group ---

    @Test
    @DisplayName("DFS-002.AC-7 An unreadable file is dropped, not its whole group")
    void unreadableFileIsDroppedNotItsWholeGroup() {
        FileEntry a = entry("a.bin", 300 * 1024);
        FileEntry b = entry("b.bin", 300 * 1024);
        FileEntry c = entry("c.bin", 300 * 1024);
        RecordingContentHasher hasher = new RecordingContentHasher()
                .withDigest(a.path(), "same-digest")
                .withDigest(b.path(), "same-digest")
                .withDigest(c.path(), "same-digest")
                .unreadable(c.path());
        CandidateGroup group = new CandidateGroup(300 * 1024, List.of(a, b, c));

        PartialHashFilter filter = new PartialHashFilter(hasher);

        List<CandidateGroup> result = filter.filter(List.of(group));

        assertEquals(1, result.size());
        assertEquals(List.of(a, b), result.getFirst().files());
        assertTrue(result.stream().noneMatch(g -> g.files().contains(c)));
    }

    @Test
    @DisplayName("DFS-002.AC-7 An unreadable file leaving fewer than two members drops the group")
    void unreadableFileLeavingFewerThanTwoMembersDropsGroup() {
        FileEntry a = entry("a.bin", 300 * 1024);
        FileEntry b = entry("b.bin", 300 * 1024);
        RecordingContentHasher hasher = new RecordingContentHasher()
                .withDigest(a.path(), "same-digest")
                .withDigest(b.path(), "same-digest")
                .unreadable(b.path());
        CandidateGroup group = new CandidateGroup(300 * 1024, List.of(a, b));

        PartialHashFilter filter = new PartialHashFilter(hasher);

        List<CandidateGroup> result = filter.filter(List.of(group));

        assertEquals(List.of(), result);
    }

    // --- AC-9: the configured sample size reaches the hasher ---

    @Test
    @DisplayName("DFS-002.AC-9 The configured sample size is passed to the hasher instead of the default")
    void configuredSampleSizeIsPassedToHasher() {
        FileEntry a = entry("a.bin", 10 * 1024);
        FileEntry b = entry("b.bin", 10 * 1024);
        RecordingContentHasher hasher = new RecordingContentHasher()
                .withDigest(a.path(), "same-digest")
                .withDigest(b.path(), "same-digest");
        CandidateGroup group = new CandidateGroup(10 * 1024, List.of(a, b));

        PartialHashFilter filter = new PartialHashFilter(hasher, 4 * 1024, 8 * 1024);

        filter.filter(List.of(group));

        // Both members are above the 8 KB custom threshold, so both must actually be sampled: a
        // threshold that silently fell back to the 128 KB default would also bypass the sample
        // for this 10 KB group and leave the hasher with zero calls, which this count catches.
        assertEquals(2, hasher.calls().size(),
                "both files are above the custom 8 KB threshold, so both should be sampled");
        assertTrue(hasher.calls().stream().allMatch(call -> call.sampleSize() == 4 * 1024),
                "every call should use the configured 4 KB sample size, not the 64 KB default");
    }

    private static FileEntry entry(String path, long size) {
        return new FileEntry(Path.of(path), size, WHEN);
    }

    /**
     * A recording {@link ContentHasher} fake: returns the digest configured for a path (or throws
     * a simulated {@link IOException} for a path marked {@link #unreadable}), and records every
     * {@link #sampleHash} call so a test can assert order and parameters. {@link #fullHash} is not
     * used by this stage, so it throws if a test accidentally reaches it.
     */
    private static final class RecordingContentHasher implements ContentHasher {

        record Call(Path file, int sampleSize) {
        }

        private final Map<Path, String> digests = new HashMap<>();
        private final Set<Path> unreadable = new HashSet<>();
        private final List<Call> calls = new ArrayList<>();

        RecordingContentHasher withDigest(Path file, String digest) {
            digests.put(file, digest);
            return this;
        }

        RecordingContentHasher unreadable(Path file) {
            unreadable.add(file);
            return this;
        }

        List<Call> calls() {
            return List.copyOf(calls);
        }

        @Override
        public String sampleHash(Path file, int sampleSize) throws IOException {
            calls.add(new Call(file, sampleSize));
            if (unreadable.contains(file)) {
                throw new IOException("simulated unreadable file: " + file);
            }
            String digest = digests.get(file);
            if (digest == null) {
                fail("no digest configured for " + file);
            }
            return digest;
        }

        @Override
        public String fullHash(Path file) {
            throw new UnsupportedOperationException("fullHash is not used by the partial-hash stage");
        }
    }
}
