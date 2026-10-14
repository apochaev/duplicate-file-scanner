package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.PhysicalCopy;
import us.pochaev.dfs.scanner.domain.model.SkipReason;
import us.pochaev.dfs.scanner.domain.model.WalkEvent;
import us.pochaev.dfs.scanner.infra.port.ContentHasher;
import us.pochaev.dfs.scanner.infra.port.FileIdentity;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The new drop-sink constructors of {@link PartialHashFilter}, {@link FullHashConfirmer} and
 * {@link HardLinkDetector} (DFS-005.AC-3): each reports a member it drops on an
 * {@link IOException} to the supplied {@code onDrop} sink, as
 * {@code Skipped(path, UNREADABLE, "<stage> failed: <message>")}. Their existing 1-to-3-argument
 * constructors and {@code filter}/{@code confirm}/{@code detect} methods are unchanged and untested
 * here (DFS-002 to DFS-004 already cover them); only the new constructor and the sink's content are
 * new behavior for this ticket.
 * <p>
 * Every test here constructs a stage with the new constructor, which is currently stubbed to throw
 * {@code UnsupportedOperationException("not implemented: DFS-005")} unconditionally. That is why
 * every test below fails red for that one reason today.
 */
class StageDropReportingTest {

    private static final Instant WHEN = Instant.parse("2024-01-01T10:00:00Z");

    @Test
    @DisplayName("DFS-005.AC-3 PartialHashFilter reports a dropped member with a detail naming the partial-hash stage")
    void partialHashFilterReportsDroppedMember() {
        FileEntry a = entry("a.bin", 300 * 1024);
        FileEntry b = entry("b.bin", 300 * 1024);
        FileEntry c = entry("c.bin", 300 * 1024);
        RecordingHasher hasher = new RecordingHasher()
                .withDigest(a.path(), "same-digest")
                .withDigest(b.path(), "same-digest")
                .sampleFails(c.path(), new IOException("Access is denied"));
        List<WalkEvent.Skipped> drops = new ArrayList<>();
        CandidateGroup group = new CandidateGroup(300 * 1024, List.of(a, b, c));

        PartialHashFilter filter = new PartialHashFilter(hasher, PartialHashFilter.DEFAULT_SAMPLE_SIZE,
                PartialHashFilter.DEFAULT_SKIP_THRESHOLD, drops::add);

        List<CandidateGroup> result = filter.filter(List.of(group));

        assertEquals(1, result.size());
        assertEquals(List.of(a, b), result.getFirst().files());
        assertEquals(1, drops.size());
        WalkEvent.Skipped drop = drops.getFirst();
        assertEquals(c.path(), drop.path());
        assertEquals(SkipReason.UNREADABLE, drop.reason());
        assertTrue(drop.detail().startsWith("partial hash failed: "), "detail should name the partial-hash stage: " + drop.detail());
        assertTrue(drop.detail().contains("Access is denied"));
    }

    @Test
    @DisplayName("DFS-005.AC-3 FullHashConfirmer reports a dropped member with a detail naming the full-hash stage")
    void fullHashConfirmerReportsDroppedMember() {
        FileEntry a = entry("a.bin", 10 * 1024);
        FileEntry b = entry("b.bin", 10 * 1024);
        FileEntry c = entry("c.bin", 10 * 1024);
        RecordingHasher hasher = new RecordingHasher()
                .withDigest(a.path(), "same-digest")
                .withDigest(b.path(), "same-digest")
                .fullFails(c.path(), new IOException("Access is denied"));
        List<WalkEvent.Skipped> drops = new ArrayList<>();
        CandidateGroup group = new CandidateGroup(10 * 1024, List.of(a, b, c));

        FullHashConfirmer confirmer = new FullHashConfirmer(hasher, drops::add);

        var result = confirmer.confirm(List.of(group));

        assertEquals(1, result.size());
        assertEquals(List.of(a, b), result.getFirst().files());
        assertEquals(1, drops.size());
        WalkEvent.Skipped drop = drops.getFirst();
        assertEquals(c.path(), drop.path());
        assertEquals(SkipReason.UNREADABLE, drop.reason());
        assertTrue(drop.detail().startsWith("full hash failed: "), "detail should name the full-hash stage: " + drop.detail());
        assertTrue(drop.detail().contains("Access is denied"));
    }

    @Test
    @DisplayName("DFS-005.AC-3 FullHashConfirmer falls back to the exception's class name when its message is null")
    void fullHashConfirmerFallsBackToClassNameWhenMessageIsNull() {
        FileEntry a = entry("a.bin", 10 * 1024);
        FileEntry b = entry("b.bin", 10 * 1024);
        RecordingHasher hasher = new RecordingHasher()
                .withDigest(a.path(), "same-digest")
                .fullFails(b.path(), new IOException());
        List<WalkEvent.Skipped> drops = new ArrayList<>();
        CandidateGroup group = new CandidateGroup(10 * 1024, List.of(a, b));

        FullHashConfirmer confirmer = new FullHashConfirmer(hasher, drops::add);

        confirmer.confirm(List.of(group));

        assertEquals(1, drops.size());
        assertEquals("full hash failed: IOException", drops.getFirst().detail());
    }

    @Test
    @DisplayName("DFS-005.AC-3 HardLinkDetector reports a dropped file with a detail naming the hard-link stage")
    void hardLinkDetectorReportsDroppedFile() {
        FileEntry a = entry("a.bin", 10 * 1024);
        FileEntry b = entry("b.bin", 10 * 1024);
        FileEntry c = entry("c.bin", 10 * 1024);
        RecordingIdentity identity = new RecordingIdentity().unreadable(b.path(), new IOException("Access is denied"));
        List<WalkEvent.Skipped> drops = new ArrayList<>();
        var input = new us.pochaev.dfs.scanner.domain.model.DuplicateSet(10 * 1024, "digest", List.of(a, b, c));

        HardLinkDetector detector = new HardLinkDetector(identity, drops::add);

        var result = detector.detect(List.of(input));

        assertEquals(1, result.size());
        assertEquals(List.of(a, c), result.getFirst().files());
        assertEquals(List.of(new PhysicalCopy(List.of(a)), new PhysicalCopy(List.of(c))), result.getFirst().copies());
        assertEquals(1, drops.size());
        WalkEvent.Skipped drop = drops.getFirst();
        assertEquals(b.path(), drop.path());
        assertEquals(SkipReason.UNREADABLE, drop.reason());
        assertTrue(drop.detail().startsWith("hard-link check failed: "), "detail should name the hard-link stage: " + drop.detail());
        assertTrue(drop.detail().contains("Access is denied"));
    }

    private static FileEntry entry(String path, long size) {
        return new FileEntry(Path.of(path), size, WHEN);
    }

    private static final class RecordingHasher implements ContentHasher {
        private final java.util.Map<Path, String> digests = new java.util.HashMap<>();
        private final java.util.Map<Path, IOException> sampleFailures = new java.util.HashMap<>();
        private final java.util.Map<Path, IOException> fullFailures = new java.util.HashMap<>();

        RecordingHasher withDigest(Path file, String digest) {
            digests.put(file, digest);
            return this;
        }

        RecordingHasher sampleFails(Path file, IOException failure) {
            sampleFailures.put(file, failure);
            return this;
        }

        RecordingHasher fullFails(Path file, IOException failure) {
            fullFailures.put(file, failure);
            return this;
        }

        @Override
        public String sampleHash(Path file, int sampleSize) throws IOException {
            IOException failure = sampleFailures.get(file);
            if (failure != null) {
                throw failure;
            }
            return digests.get(file);
        }

        @Override
        public String fullHash(Path file) throws IOException {
            IOException failure = fullFailures.get(file);
            if (failure != null) {
                throw failure;
            }
            return digests.get(file);
        }
    }

    private static final class RecordingIdentity implements FileIdentity {
        private final java.util.Map<Path, IOException> unreadable = new java.util.HashMap<>();

        RecordingIdentity unreadable(Path path, IOException failure) {
            unreadable.put(path, failure);
            return this;
        }

        @Override
        public Object key(Path path) throws IOException {
            IOException failure = unreadable.get(path);
            if (failure != null) {
                throw failure;
            }
            return path;
        }
    }
}
