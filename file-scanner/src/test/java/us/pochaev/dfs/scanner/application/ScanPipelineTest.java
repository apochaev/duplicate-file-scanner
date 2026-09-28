package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.PhysicalCopy;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.scanner.domain.model.ScanEvent;
import us.pochaev.dfs.scanner.domain.model.ScanListener;
import us.pochaev.dfs.scanner.domain.model.ScanPhase;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.model.SkipReason;
import us.pochaev.dfs.scanner.domain.model.WalkEvent;
import us.pochaev.dfs.scanner.infra.port.ContentHasher;
import us.pochaev.dfs.scanner.infra.port.FileIdentity;
import us.pochaev.dfs.scanner.infra.port.FileTreeWalker;
import us.pochaev.dfs.scanner.infra.port.LocationResolver;
import us.pochaev.dfs.scanner.infra.port.WalkListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link ScanPipeline}, DFS-005's composing service: runs one scan synchronously, in the order of
 * CLAUDE.md design decision 3 (walk into {@link SizeIndex}, then partial hash, full hash, hard
 * links). Tested against hand-written fakes of all four ports ({@link LocationResolver},
 * {@link FileTreeWalker}, {@link ContentHasher}, {@link FileIdentity}), per CLAUDE.md's rule to
 * test {@code application} logic against fakes rather than the real filesystem (the real-adapter
 * scenario, AC-17, lives in {@code infra.adapter.ScanPipelineEndToEndTest}).
 * <p>
 * Every test here calls {@link ScanPipeline#scan}, and almost every one first builds a
 * {@link ScanConfig}, whose compact constructor and {@link ScanConfig#defaults()} are stubbed to
 * throw {@code UnsupportedOperationException("not implemented: DFS-005")} unconditionally
 * (design.md's "Red gate" note). That is why every test below fails red for that one reason today:
 * a test expecting a different exception type (IllegalArgumentException, NoSuchFileException,
 * CancellationException) uses a try/catch rather than {@code assertThrows}, so the stub's
 * exception propagates uncaught instead of being reported as this test's own
 * {@code AssertionFailedError} (test-guide.md's L-013 warning).
 */
class ScanPipelineTest {

    private static final Instant WHEN = Instant.parse("2024-01-01T10:00:00Z");
    private static final ScanListener NO_OP_LISTENER = event -> {
    };
    private static final BooleanSupplier NEVER_STOP = () -> false;

    // --- AC-1: identical files in two folders form one duplicate set ---

    @Test
    @DisplayName("DFS-005.AC-1 Identical files in two folders form one duplicate set")
    void identicalFilesInTwoFoldersFormOneDuplicateSet() throws IOException {
        Path root = Path.of("C:/a");
        FileEntry photoX = entry("C:/a/x/photo.jpg", 10);
        FileEntry photoY = entry("C:/a/y/photo.jpg", 10);
        FileEntry notes = entry("C:/a/x/notes.txt", 30);
        FakeLocationResolver resolver = new FakeLocationResolver().resolvesTo(root, root);
        FakeFileTreeWalker walker = new FakeFileTreeWalker()
                .withEvents(root, found(photoX), found(photoY), found(notes));
        FakeContentHasher hasher = new FakeContentHasher()
                .digest(photoX.path(), "photo-digest")
                .digest(photoY.path(), "photo-digest")
                .digest(notes.path(), "notes-digest");
        FakeFileIdentity identity = new FakeFileIdentity();
        ScanPipeline pipeline = new ScanPipeline(resolver, walker, hasher, identity);

        ScanResult result = pipeline.scan(List.of(root), ScanConfig.defaults(), NO_OP_LISTENER, NEVER_STOP);

        assertEquals(1, result.sets().size());
        DuplicateSet set = result.sets().getFirst();
        assertEquals(List.of(photoX, photoY), set.files());
        assertEquals(
                List.of(new PhysicalCopy(List.of(photoX)), new PhysicalCopy(List.of(photoY))),
                set.copies());
        assertEquals(List.of(), result.skipped());
    }

    // --- AC-2: a walker skip is reported in the result ---

    @Test
    @DisplayName("DFS-005.AC-2 A path the walker skips is reported in the result")
    void walkerSkipIsReportedInResult() throws IOException {
        Path root = Path.of("root");
        WalkEvent.Skipped skip = new WalkEvent.Skipped(root.resolve("link"), SkipReason.SYMLINK, "symbolic link");
        FakeLocationResolver resolver = new FakeLocationResolver().resolvesTo(root, root);
        FakeFileTreeWalker walker = new FakeFileTreeWalker().withEvents(root, skip);
        ScanPipeline pipeline = new ScanPipeline(resolver, walker, new FakeContentHasher(), new FakeFileIdentity());

        ScanResult result = pipeline.scan(List.of(root), ScanConfig.defaults(), NO_OP_LISTENER, NEVER_STOP);

        assertEquals(List.of(skip), result.skipped());
    }

    // --- AC-3: a file a stage cannot read is reported in the result ---

    @Test
    @DisplayName("DFS-005.AC-3 A file whose full hash fails is reported as UNREADABLE with a detail naming the full-hash stage")
    void fileWhoseFullHashFailsIsReported() throws IOException {
        Path root = Path.of("root");
        FileEntry a = entry("root/a.bin", 10);
        FileEntry b = entry("root/b.bin", 10);
        FileEntry c = entry("root/c.bin", 10);
        FakeLocationResolver resolver = new FakeLocationResolver().resolvesTo(root, root);
        FakeFileTreeWalker walker = new FakeFileTreeWalker().withEvents(root, found(a), found(b), found(c));
        FakeContentHasher hasher = new FakeContentHasher()
                .digest(a.path(), "same-digest")
                .digest(b.path(), "same-digest")
                .fullHashFails(c.path(), "Access is denied");
        ScanPipeline pipeline = new ScanPipeline(resolver, walker, hasher, new FakeFileIdentity());

        ScanResult result = pipeline.scan(List.of(root), ScanConfig.defaults(), NO_OP_LISTENER, NEVER_STOP);

        assertEquals(1, result.sets().size());
        assertEquals(List.of(a, b), result.sets().getFirst().files());
        assertEquals(1, result.skipped().size());
        WalkEvent.Skipped skip = result.skipped().getFirst();
        assertEquals(c.path(), skip.path());
        assertEquals(SkipReason.UNREADABLE, skip.reason());
        assertTrue(skip.detail().toLowerCase().contains("full hash"), "detail should name the full-hash stage: " + skip.detail());
    }

    // --- AC-4: progress is reported per phase and per group ---

    @Test
    @DisplayName("DFS-005.AC-4 Progress is reported per phase and per group")
    void progressIsReportedPerPhaseAndPerGroup() throws IOException {
        Path root = Path.of("root");
        FileEntry a1 = entry("root/a1.bin", 10);
        FileEntry a2 = entry("root/a2.bin", 10);
        FileEntry b1 = entry("root/b1.bin", 20);
        FileEntry b2 = entry("root/b2.bin", 20);
        FileEntry c = entry("root/c.bin", 30);
        FakeLocationResolver resolver = new FakeLocationResolver().resolvesTo(root, root);
        FakeFileTreeWalker walker = new FakeFileTreeWalker()
                .withEvents(root, found(a1), found(a2), found(b1), found(b2), found(c));
        FakeContentHasher hasher = new FakeContentHasher()
                .digest(a1.path(), "a-digest").digest(a2.path(), "a-digest")
                .digest(b1.path(), "b-digest").digest(b2.path(), "b-digest");
        RecordingListener listener = new RecordingListener();
        ScanPipeline pipeline = new ScanPipeline(resolver, walker, hasher, new FakeFileIdentity());

        pipeline.scan(List.of(root), ScanConfig.defaults(), listener, NEVER_STOP);

        List<ScanEvent> expected = List.of(
                new ScanEvent.PhaseStarted(ScanPhase.WALK),
                new ScanEvent.FileWalked(1), new ScanEvent.FileWalked(2), new ScanEvent.FileWalked(3),
                new ScanEvent.FileWalked(4), new ScanEvent.FileWalked(5),
                new ScanEvent.PhaseStarted(ScanPhase.PARTIAL_HASH),
                new ScanEvent.GroupDone(ScanPhase.PARTIAL_HASH, 1, 2),
                new ScanEvent.GroupDone(ScanPhase.PARTIAL_HASH, 2, 2),
                new ScanEvent.PhaseStarted(ScanPhase.FULL_HASH),
                new ScanEvent.GroupDone(ScanPhase.FULL_HASH, 1, 2),
                new ScanEvent.GroupDone(ScanPhase.FULL_HASH, 2, 2),
                new ScanEvent.PhaseStarted(ScanPhase.HARD_LINKS),
                new ScanEvent.GroupDone(ScanPhase.HARD_LINKS, 1, 2),
                new ScanEvent.GroupDone(ScanPhase.HARD_LINKS, 2, 2));
        assertEquals(expected, listener.events());
    }

    // --- AC-5: skips reach the listener as they happen ---

    @Test
    @DisplayName("DFS-005.AC-5 A skip reaches the listener between the phase it happened in and the next phase")
    void skipReachesListenerBetweenPhases() throws IOException {
        Path root = Path.of("root");
        FileEntry a = entry("root/a.bin", 10);
        FileEntry b = entry("root/b.bin", 10);
        FileEntry c = entry("root/c.bin", 10);
        FakeLocationResolver resolver = new FakeLocationResolver().resolvesTo(root, root);
        FakeFileTreeWalker walker = new FakeFileTreeWalker().withEvents(root, found(a), found(b), found(c));
        FakeContentHasher hasher = new FakeContentHasher()
                .digest(a.path(), "same-digest").digest(b.path(), "same-digest")
                .fullHashFails(c.path(), "Access is denied");
        RecordingListener listener = new RecordingListener();
        ScanPipeline pipeline = new ScanPipeline(resolver, walker, hasher, new FakeFileIdentity());

        pipeline.scan(List.of(root), ScanConfig.defaults(), listener, NEVER_STOP);

        List<ScanEvent> events = listener.events();
        int fullHashStarted = events.indexOf(new ScanEvent.PhaseStarted(ScanPhase.FULL_HASH));
        int hardLinksStarted = events.indexOf(new ScanEvent.PhaseStarted(ScanPhase.HARD_LINKS));
        int skipIndex = -1;
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i) instanceof ScanEvent.Skipped skipped && skipped.skipped().path().equals(c.path())) {
                skipIndex = i;
            }
        }
        assertTrue(fullHashStarted >= 0 && hardLinksStarted >= 0 && skipIndex >= 0,
                "expected PhaseStarted(FULL_HASH), a Skipped for c, and PhaseStarted(HARD_LINKS)");
        assertTrue(fullHashStarted < skipIndex && skipIndex < hardLinksStarted,
                "the skip for c should be reported after FULL_HASH starts and before HARD_LINKS starts");
    }

    // --- AC-6: the core prints nothing ---

    @Test
    @DisplayName("DFS-005.AC-6 Running a scan writes nothing to System.out or System.err")
    void scanWritesNothingToSystemOutOrErr() throws IOException {
        Path root = Path.of("root");
        FileEntry a = entry("root/a.bin", 10);
        FileEntry b = entry("root/b.bin", 10);
        WalkEvent.Skipped skip = new WalkEvent.Skipped(root.resolve("link"), SkipReason.SYMLINK, "symbolic link");
        FakeLocationResolver resolver = new FakeLocationResolver().resolvesTo(root, root);
        FakeFileTreeWalker walker = new FakeFileTreeWalker().withEvents(root, found(a), found(b), skip);
        FakeContentHasher hasher = new FakeContentHasher().digest(a.path(), "same").digest(b.path(), "same");
        ScanPipeline pipeline = new ScanPipeline(resolver, walker, hasher, new FakeFileIdentity());

        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();
        ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(capturedOut));
            System.setErr(new PrintStream(capturedErr));

            pipeline.scan(List.of(root), ScanConfig.defaults(), NO_OP_LISTENER, NEVER_STOP);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        assertEquals(0, capturedOut.size(), "nothing should be written to System.out");
        assertEquals(0, capturedErr.size(), "nothing should be written to System.err");
    }

    // --- AC-7: empty files are excluded by default ---

    @Test
    @DisplayName("DFS-005.AC-7 Empty files are excluded by default")
    void emptyFilesAreExcludedByDefault() throws IOException {
        Path root = Path.of("root");
        FileEntry e1 = entry("root/e1.dat", 0);
        FileEntry e2 = entry("root/e2.dat", 0);
        FileEntry a1 = entry("root/a1.bin", 10);
        FileEntry a2 = entry("root/a2.bin", 10);
        FakeLocationResolver resolver = new FakeLocationResolver().resolvesTo(root, root);
        FakeFileTreeWalker walker = new FakeFileTreeWalker().withEvents(root, found(e1), found(e2), found(a1), found(a2));
        FakeContentHasher hasher = new FakeContentHasher().digest(a1.path(), "same").digest(a2.path(), "same");
        ScanPipeline pipeline = new ScanPipeline(resolver, walker, hasher, new FakeFileIdentity());

        ScanResult result = pipeline.scan(List.of(root), ScanConfig.defaults(), NO_OP_LISTENER, NEVER_STOP);

        assertEquals(1, result.sets().size());
        assertEquals(List.of(a1, a2), result.sets().getFirst().files());
    }

    // --- AC-7: a larger minimum size excludes small duplicates ---

    @Test
    @DisplayName("DFS-005.AC-7 A larger minimum size excludes small duplicates")
    void largerMinimumSizeExcludesSmallDuplicates() throws IOException {
        Path root = Path.of("root");
        FileEntry a1 = entry("root/a1.bin", 10);
        FileEntry a2 = entry("root/a2.bin", 10);
        FileEntry b1 = entry("root/b1.bin", 5000);
        FileEntry b2 = entry("root/b2.bin", 5000);
        FakeLocationResolver resolver = new FakeLocationResolver().resolvesTo(root, root);
        FakeFileTreeWalker walker = new FakeFileTreeWalker().withEvents(root, found(a1), found(a2), found(b1), found(b2));
        FakeContentHasher hasher = new FakeContentHasher()
                .digest(a1.path(), "a-same").digest(a2.path(), "a-same")
                .digest(b1.path(), "b-same").digest(b2.path(), "b-same");
        ScanPipeline pipeline = new ScanPipeline(resolver, walker, hasher, new FakeFileIdentity());
        ScanConfig config = new ScanConfig(4096, ScanConfig.defaults().sampleSize(), ScanConfig.defaults().partialHashThreshold());

        ScanResult result = pipeline.scan(List.of(root), config, NO_OP_LISTENER, NEVER_STOP);

        assertEquals(1, result.sets().size());
        assertEquals(List.of(b1, b2), result.sets().getFirst().files());
    }

    // --- AC-9: equal and nested start locations are walked once ---

    @Test
    @DisplayName("DFS-005.AC-9 Equal and nested start locations are walked once")
    void equalAndNestedLocationsAreWalkedOnce() throws IOException {
        Path location1 = Path.of("C:/photos/2024");
        Path location2 = Path.of("c:/Photos");
        Path location3 = Path.of("D:/backup");
        Path resolvedPhotos = Path.of("C:/photos");
        Path resolvedBackup = Path.of("D:/backup");
        FakeLocationResolver resolver = new FakeLocationResolver()
                .resolvesTo(location1, location1)
                .resolvesTo(location2, resolvedPhotos)
                .resolvesTo(location3, resolvedBackup);
        FakeFileTreeWalker walker = new FakeFileTreeWalker();
        ScanPipeline pipeline = new ScanPipeline(resolver, walker, new FakeContentHasher(), new FakeFileIdentity());

        pipeline.scan(List.of(location1, location2, location3), ScanConfig.defaults(), NO_OP_LISTENER, NEVER_STOP);

        assertEquals(List.of(resolvedPhotos, resolvedBackup), walker.walkedRoots());
    }

    // --- AC-9: locations are walked in path order whatever the order given ---

    @Test
    @DisplayName("DFS-005.AC-9 Locations are walked in path order whatever the order given")
    void locationsAreWalkedInPathOrder() throws IOException {
        Path locationD = Path.of("D:/b");
        Path locationC = Path.of("C:/a");
        FakeLocationResolver resolver = new FakeLocationResolver()
                .resolvesTo(locationD, locationD)
                .resolvesTo(locationC, locationC);
        FakeFileTreeWalker walker = new FakeFileTreeWalker();
        ScanPipeline pipeline = new ScanPipeline(resolver, walker, new FakeContentHasher(), new FakeFileIdentity());

        pipeline.scan(List.of(locationD, locationC), ScanConfig.defaults(), NO_OP_LISTENER, NEVER_STOP);

        assertEquals(List.of(locationC, locationD), walker.walkedRoots());
    }

    // --- AC-11: an empty location list is rejected ---

    @Test
    @DisplayName("DFS-005.AC-11 An empty location list is rejected")
    void emptyLocationListIsRejected() {
        ScanPipeline pipeline = new ScanPipeline(
                new FakeLocationResolver(), new FakeFileTreeWalker(), new FakeContentHasher(), new FakeFileIdentity());

        try {
            pipeline.scan(List.of(), ScanConfig.defaults(), NO_OP_LISTENER, NEVER_STOP);
            fail("expected IllegalArgumentException for an empty location list");
        } catch (IllegalArgumentException expected) {
            // expected once implemented
        } catch (IOException unexpected) {
            fail("expected IllegalArgumentException but got " + unexpected);
        }
    }

    // --- AC-12: a mistyped location fails the scan before anything runs ---

    @Test
    @DisplayName("DFS-005.AC-12 A mistyped location fails the scan before anything runs")
    void mistypedLocationFailsScanBeforeAnythingRuns() {
        Path good = Path.of("C:/a");
        Path bad = Path.of("C:/phtos");
        FakeLocationResolver resolver = new FakeLocationResolver()
                .resolvesTo(good, good)
                .failsWith(bad, new NoSuchFileException(bad.toString()));
        FakeFileTreeWalker walker = new FakeFileTreeWalker();
        RecordingListener listener = new RecordingListener();
        ScanPipeline pipeline = new ScanPipeline(resolver, walker, new FakeContentHasher(), new FakeFileIdentity());

        try {
            pipeline.scan(List.of(good, bad), ScanConfig.defaults(), listener, NEVER_STOP);
            fail("expected NoSuchFileException naming " + bad);
        } catch (NoSuchFileException expected) {
            assertTrue(expected.getMessage() != null && expected.getMessage().contains(bad.toString()),
                    "exception should name " + bad + " but was: " + expected.getMessage());
        } catch (IOException unexpected) {
            fail("expected NoSuchFileException but got " + unexpected);
        }
        assertEquals(List.of(), walker.walkedRoots(), "the walker should never be called");
        assertEquals(List.of(), listener.events(), "the listener should receive no events");
    }

    // --- AC-13: a location that fails mid-scan is skipped and the rest still run ---

    @Test
    @DisplayName("DFS-005.AC-13 A location that fails mid-scan is skipped and the rest still run")
    void locationThatFailsMidScanIsSkippedAndRestStillRun() throws IOException {
        Path locationA = Path.of("C:/a");
        Path locationUsb = Path.of("D:/usb");
        FileEntry x1 = entry("C:/a/x1.bin", 10);
        FileEntry x2 = entry("C:/a/x2.bin", 10);
        FakeLocationResolver resolver = new FakeLocationResolver()
                .resolvesTo(locationA, locationA)
                .resolvesTo(locationUsb, locationUsb);
        FakeFileTreeWalker walker = new FakeFileTreeWalker()
                .withEvents(locationA, found(x1), found(x2))
                .withFailure(locationUsb, new IOException("The device is not ready"));
        FakeContentHasher hasher = new FakeContentHasher().digest(x1.path(), "same").digest(x2.path(), "same");
        ScanPipeline pipeline = new ScanPipeline(resolver, walker, hasher, new FakeFileIdentity());

        ScanResult result = pipeline.scan(List.of(locationA, locationUsb), ScanConfig.defaults(), NO_OP_LISTENER, NEVER_STOP);

        assertEquals(1, result.sets().size());
        assertEquals(List.of(x1, x2), result.sets().getFirst().files());
        assertEquals(1, result.skipped().size());
        assertEquals(locationUsb, result.skipped().getFirst().path());
        assertEquals(SkipReason.UNREADABLE, result.skipped().getFirst().reason());
    }

    // --- AC-14: stopping during the walk ---

    @Test
    @DisplayName("DFS-005.AC-14 Stopping during the walk throws CancellationException and never walks the next location")
    void stoppingDuringWalkThrowsCancellationAndSkipsNextLocation() throws IOException {
        Path locationA = Path.of("C:/a");
        Path locationB = Path.of("D:/b");
        FileEntry f1 = entry("C:/a/f1.bin", 10);
        FileEntry f2 = entry("C:/a/f2.bin", 10);
        FileEntry f3 = entry("C:/a/f3.bin", 10);
        FakeLocationResolver resolver = new FakeLocationResolver()
                .resolvesTo(locationA, locationA)
                .resolvesTo(locationB, locationB);
        FakeFileTreeWalker walker = new FakeFileTreeWalker()
                .withEvents(locationA, found(f1), found(f2), found(f3))
                .withEvents(locationB, found(entry("D:/b/g.bin", 10)));
        ScanPipeline pipeline = new ScanPipeline(resolver, walker, new FakeContentHasher(), new FakeFileIdentity());
        BooleanSupplier stopAfterTwoChecks = stopAfterChecks(2);

        try {
            pipeline.scan(List.of(locationA, locationB), ScanConfig.defaults(), NO_OP_LISTENER, stopAfterTwoChecks);
            fail("expected CancellationException");
        } catch (CancellationException expected) {
            // expected once implemented
        }
        assertEquals(List.of(false, false, false), walker.listenerReturns().subList(0, Math.min(3, walker.listenerReturns().size())),
                "the walk listener should return false starting at the 3rd event");
        assertEquals(List.of(locationA), walker.walkedRoots(), "D:/b should never be walked");
    }

    // --- AC-15: stopping between groups of the full hash ---

    @Test
    @DisplayName("DFS-005.AC-15 Stopping between groups of the full hash throws CancellationException and skips the hard-link stage")
    void stoppingBetweenFullHashGroupsThrowsCancellationAndSkipsHardLinks() throws IOException {
        Path root = Path.of("root");
        FileEntry a1 = entry("root/a1.bin", 10);
        FileEntry a2 = entry("root/a2.bin", 10);
        FileEntry b1 = entry("root/b1.bin", 20);
        FileEntry b2 = entry("root/b2.bin", 20);
        FakeLocationResolver resolver = new FakeLocationResolver().resolvesTo(root, root);
        FakeFileTreeWalker walker = new FakeFileTreeWalker().withEvents(root, found(a1), found(a2), found(b1), found(b2));
        FakeContentHasher hasher = new FakeContentHasher()
                .digest(a1.path(), "a-same").digest(a2.path(), "a-same")
                .digest(b1.path(), "b-same").digest(b2.path(), "b-same");
        FakeFileIdentity identity = new FakeFileIdentity();
        ScanPipeline pipeline = new ScanPipeline(resolver, walker, hasher, identity);
        // design.md's scan() order checks the stop signal at every walk event (4: a1, a2, b1, b2),
        // then before each of PARTIAL_HASH's 2 groups, then before each of FULL_HASH's 2 groups.
        // Both candidate groups are below the sample threshold, so PARTIAL_HASH passes each through
        // unchanged without hashing. That makes the 8th check the one immediately before FULL_HASH's
        // 2nd group (b1/b2's group is processed as FULL_HASH's 1st group, a1/a2's as its 2nd,
        // following SizeIndex.groups()'s largest-size-first order): the checks before that succeed
        // (checks 1-7 read false), the 8th reads true, so FULL_HASH confirms exactly one group and
        // HARD_LINKS never starts.
        BooleanSupplier stopBeforeSecondFullHashGroup = stopAfterChecks(7);

        try {
            pipeline.scan(List.of(root), ScanConfig.defaults(), NO_OP_LISTENER, stopBeforeSecondFullHashGroup);
            fail("expected CancellationException");
        } catch (CancellationException expected) {
            // expected once implemented
        }
        List<Path> fullHashCalls = hasher.fullHashCalls();
        assertEquals(Set.of(b1.path(), b2.path()), Set.copyOf(fullHashCalls),
                "the full-hash stage should have received exactly one group (b1, b2)");
        assertTrue(identity.calls().isEmpty(), "the hard-link stage should never be reached");
    }

    // --- AC-16: result order follows the pipeline and the order of events ---

    @Test
    @DisplayName("DFS-005.AC-16 Result order follows the pipeline order and the order skips and drops happened")
    void resultOrderFollowsPipelineAndEventOrder() throws IOException {
        Path root = Path.of("root");
        FileEntry a1 = entry("root/a1.bin", 10);
        FileEntry a2 = entry("root/a2.bin", 10);
        FileEntry a3 = entry("root/a3.bin", 10);
        FileEntry b1 = entry("root/b1.bin", 20);
        FileEntry b2 = entry("root/b2.bin", 20);
        FileEntry b3 = entry("root/b3.bin", 20);
        WalkEvent.Skipped linkSkip = new WalkEvent.Skipped(root.resolve("l"), SkipReason.SYMLINK, "symbolic link");
        FakeLocationResolver resolver = new FakeLocationResolver().resolvesTo(root, root);
        FakeFileTreeWalker walker = new FakeFileTreeWalker()
                .withEvents(root, found(a1), found(a2), found(a3), found(b1), found(b2), found(b3), linkSkip);
        FakeContentHasher hasher = new FakeContentHasher()
                .digest(a1.path(), "a-same").digest(a2.path(), "a-same").digest(a3.path(), "a-same")
                .digest(b1.path(), "b-same").digest(b2.path(), "b-same")
                .fullHashFails(b3.path(), "Access is denied");
        RecordingListener listener = new RecordingListener();
        ScanPipeline pipeline = new ScanPipeline(resolver, walker, hasher, new FakeFileIdentity());

        ScanResult result = pipeline.scan(List.of(root), ScanConfig.defaults(), listener, NEVER_STOP);

        assertEquals(2, result.sets().size());
        assertEquals(List.of(b1, b2), result.sets().get(0).files());
        assertEquals(List.of(a1, a2, a3), result.sets().get(1).files());

        assertEquals(2, result.skipped().size());
        assertEquals(root.resolve("l"), result.skipped().get(0).path());
        assertEquals(b3.path(), result.skipped().get(1).path());

        List<Path> skippedEventOrder = listener.events().stream()
                .filter(ScanEvent.Skipped.class::isInstance)
                .map(e -> ((ScanEvent.Skipped) e).skipped().path())
                .toList();
        assertEquals(List.of(root.resolve("l"), b3.path()), skippedEventOrder);
    }

    // --- AC-18: two concurrent scans on one instance do not share state ---

    @Test
    @DisplayName("DFS-005.AC-18 Two concurrent scans on one pipeline instance return only their own results")
    void concurrentScansDoNotShareState() throws Exception {
        Path locationA = Path.of("C:/a");
        Path locationB = Path.of("D:/b");
        FileEntry a1 = entry("C:/a/a1.bin", 10);
        FileEntry a2 = entry("C:/a/a2.bin", 10);
        FileEntry b1 = entry("D:/b/b1.bin", 10);
        FileEntry b2 = entry("D:/b/b2.bin", 10);
        WalkEvent.Skipped skipA = new WalkEvent.Skipped(locationA.resolve("la"), SkipReason.SYMLINK, "symbolic link");
        WalkEvent.Skipped skipB = new WalkEvent.Skipped(locationB.resolve("lb"), SkipReason.SYMLINK, "symbolic link");
        FakeLocationResolver resolver = new FakeLocationResolver()
                .resolvesTo(locationA, locationA)
                .resolvesTo(locationB, locationB);
        BlockingFakeFileTreeWalker walker = new BlockingFakeFileTreeWalker()
                .withEvents(locationA, found(a1), found(a2), skipA)
                .withEvents(locationB, found(b1), found(b2), skipB);
        FakeContentHasher hasher = new FakeContentHasher()
                .digest(a1.path(), "a-same").digest(a2.path(), "a-same")
                .digest(b1.path(), "b-same").digest(b2.path(), "b-same");
        ScanPipeline pipeline = new ScanPipeline(resolver, walker, hasher, new FakeFileIdentity());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ScanResult> futureA = executor.submit(
                    () -> pipeline.scan(List.of(locationA), ScanConfig.defaults(), NO_OP_LISTENER, NEVER_STOP));
            Future<ScanResult> futureB = executor.submit(
                    () -> pipeline.scan(List.of(locationB), ScanConfig.defaults(), NO_OP_LISTENER, NEVER_STOP));

            ScanResult resultA = futureA.get();
            ScanResult resultB = futureB.get();

            assertEquals(1, resultA.sets().size());
            assertEquals(List.of(a1, a2), resultA.sets().getFirst().files());
            assertEquals(List.of(skipA), resultA.skipped());

            assertEquals(1, resultB.sets().size());
            assertEquals(List.of(b1, b2), resultB.sets().getFirst().files());
            assertEquals(List.of(skipB), resultB.skipped());
        } finally {
            executor.shutdown();
        }
    }

    private static WalkEvent found(FileEntry entry) {
        return new WalkEvent.FileFound(entry);
    }

    private static FileEntry entry(String path, long size) {
        return new FileEntry(Path.of(path), size, WHEN);
    }

    private static BooleanSupplier stopAfterChecks(int checksBeforeTrue) {
        AtomicInteger count = new AtomicInteger();
        return () -> count.incrementAndGet() > checksBeforeTrue;
    }

    // --- fakes ---

    private static final class FakeLocationResolver implements LocationResolver {
        private final Map<Path, Path> resolved = new HashMap<>();
        private final Map<Path, IOException> failures = new HashMap<>();

        FakeLocationResolver resolvesTo(Path location, Path real) {
            resolved.put(location, real);
            return this;
        }

        FakeLocationResolver failsWith(Path location, IOException failure) {
            failures.put(location, failure);
            return this;
        }

        @Override
        public Path realPath(Path location) throws IOException {
            IOException failure = failures.get(location);
            if (failure != null) {
                throw failure;
            }
            Path real = resolved.get(location);
            if (real == null) {
                fail("no resolution configured for " + location);
            }
            return real;
        }
    }

    private static class FakeFileTreeWalker implements FileTreeWalker {
        final Map<Path, List<WalkEvent>> eventsByRoot = new LinkedHashMap<>();
        final Map<Path, IOException> failuresByRoot = new HashMap<>();
        final List<Path> walkedRoots = new ArrayList<>();
        final List<Boolean> listenerReturns = new ArrayList<>();

        FakeFileTreeWalker withEvents(Path root, WalkEvent... events) {
            eventsByRoot.put(root, List.of(events));
            return this;
        }

        FakeFileTreeWalker withFailure(Path root, IOException failure) {
            failuresByRoot.put(root, failure);
            return this;
        }

        List<Path> walkedRoots() {
            synchronized (walkedRoots) {
                return List.copyOf(walkedRoots);
            }
        }

        List<Boolean> listenerReturns() {
            synchronized (listenerReturns) {
                return List.copyOf(listenerReturns);
            }
        }

        @Override
        public void walk(Path root, WalkListener listener) throws IOException {
            synchronized (walkedRoots) {
                walkedRoots.add(root);
            }
            beforeEvents(root);
            IOException failure = failuresByRoot.get(root);
            if (failure != null) {
                throw failure;
            }
            for (WalkEvent event : eventsByRoot.getOrDefault(root, List.of())) {
                boolean keepGoing = listener.onEvent(event);
                synchronized (listenerReturns) {
                    listenerReturns.add(keepGoing);
                }
                if (!keepGoing) {
                    return;
                }
            }
        }

        void beforeEvents(Path root) {
            // hook for subclasses
        }
    }

    /** Blocks each walk until both concurrent scans have started theirs (DFS-005.AC-18). */
    private static final class BlockingFakeFileTreeWalker extends FakeFileTreeWalker {
        private final CyclicBarrier barrier = new CyclicBarrier(2);

        @Override
        BlockingFakeFileTreeWalker withEvents(Path root, WalkEvent... events) {
            eventsByRoot.put(root, List.of(events));
            return this;
        }

        @Override
        void beforeEvents(Path root) {
            try {
                barrier.await();
            } catch (Exception e) {
                throw new AssertionError("barrier wait failed", e);
            }
        }
    }

    private static final class FakeContentHasher implements ContentHasher {
        private final Map<Path, String> digests = new HashMap<>();
        private final Map<Path, String> sampleFailures = new HashMap<>();
        private final Map<Path, String> fullFailures = new HashMap<>();
        private final List<Path> fullHashCalls = new ArrayList<>();

        FakeContentHasher digest(Path path, String digest) {
            digests.put(path, digest);
            return this;
        }

        FakeContentHasher fullHashFails(Path path, String message) {
            fullFailures.put(path, message);
            return this;
        }

        List<Path> fullHashCalls() {
            synchronized (fullHashCalls) {
                return List.copyOf(fullHashCalls);
            }
        }

        @Override
        public String sampleHash(Path file, int sampleSize) throws IOException {
            String failure = sampleFailures.get(file);
            if (failure != null) {
                throw new IOException(failure);
            }
            return digestFor(file);
        }

        @Override
        public String fullHash(Path file) throws IOException {
            synchronized (fullHashCalls) {
                fullHashCalls.add(file);
            }
            String failure = fullFailures.get(file);
            if (failure != null) {
                throw new IOException(failure);
            }
            return digestFor(file);
        }

        private String digestFor(Path file) {
            String digest = digests.get(file);
            if (digest == null) {
                fail("no digest configured for " + file);
            }
            return digest;
        }
    }

    private static final class FakeFileIdentity implements FileIdentity {
        private final Set<Path> pairA = new HashSet<>();
        private final List<Path[]> calls = new ArrayList<>();

        List<Path[]> calls() {
            synchronized (calls) {
                return List.copyOf(calls);
            }
        }

        @Override
        public boolean isSameFile(Path a, Path b) throws IOException {
            synchronized (calls) {
                calls.add(new Path[]{a, b});
            }
            return a.equals(b);
        }
    }

    private static final class RecordingListener implements ScanListener {
        private final List<ScanEvent> events = new ArrayList<>();

        @Override
        public synchronized void onEvent(ScanEvent event) {
            events.add(event);
        }

        synchronized List<ScanEvent> events() {
            return List.copyOf(events);
        }
    }
}
