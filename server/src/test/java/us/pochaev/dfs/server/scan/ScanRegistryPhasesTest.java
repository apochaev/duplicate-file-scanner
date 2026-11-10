package us.pochaev.dfs.server.scan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.scanner.domain.model.ScanEvent;
import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.domain.model.ScanListener;
import us.pochaev.dfs.scanner.domain.model.ScanPhase;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.model.ScanSnapshot;
import us.pochaev.dfs.scanner.domain.model.ScanStatus;
import us.pochaev.dfs.scanner.domain.usecases.command.CancelScan;
import us.pochaev.dfs.scanner.domain.usecases.command.StartScan;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanResult;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanStatus;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registry's phase totals and finish time (DFS-064), driven deterministically: fakes of the four core ports, a
 * fake clock, events fed straight to the listener the registry gave the core.
 */
class ScanRegistryPhasesTest {

    static Instant at(String hhmmss) {
        return Instant.parse("2026-11-09T" + hhmmss + "Z");
    }

    static final class FakeClock extends Clock {
        volatile Instant now;

        FakeClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** The four core ports in one fake: the core's status is whatever the test sets. */
    static final class FakeCore implements StartScan, CancelScan, GetScanStatus, GetScanResult {
        final ScanId id = new ScanId(UUID.randomUUID());
        volatile ScanListener listener;
        volatile ScanStatus status = ScanStatus.RUNNING;
        volatile ScanPhase phase;
        volatile String failure;

        @Override
        public Result<ScanId> start(List<Path> locations, ScanConfig config) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<ScanId> start(List<Path> locations, ScanConfig config, ScanListener l) {
            listener = l;
            return new Result.Success<>(id);
        }

        @Override
        public Result<ScanSnapshot> cancel(ScanId scanId) {
            return status(scanId);
        }

        @Override
        public Result<ScanSnapshot> status(ScanId scanId) {
            return new Result.Success<>(new ScanSnapshot(status, phase, 0, 0, 0, failure, null));
        }

        @Override
        public Result<ScanResult> result(ScanId scanId) {
            return new Result.Success<>(new ScanResult(List.of(), List.of(), List.of(), List.of(), 0));
        }

        void emit(ScanEvent event) {
            if (event instanceof ScanEvent.PhaseStarted p) {
                phase = p.phase();
            }
            listener.onEvent(event);
        }

        void walk(int dirs, int files, int bytesPerFile) {
            for (int i = 1; i <= dirs; i++) {
                emit(new ScanEvent.DirectoryWalked(i));
            }
            for (int i = 1; i <= files; i++) {
                emit(new ScanEvent.FileWalked(i, (long) i * bytesPerFile));
            }
        }
    }

    record Fixture(ScanRegistry registry, FakeCore core, FakeClock clock, String id) {
    }

    static Fixture started(String clockAt) {
        FakeCore core = new FakeCore();
        FakeClock clock = new FakeClock(at(clockAt));
        ScanRegistry registry = new ScanRegistry(core, core, core, core, clock);
        Result<ScanEntry> r = registry.start(List.of("/x"), List.of(Path.of("/x")), ScanConfig.defaults());
        assertTrue(r instanceof Result.Success<ScanEntry>, r.toString());
        return new Fixture(registry, core, clock, core.id.value().toString());
    }

    static ScanProgress progress(Fixture f) {
        return f.registry.progress(f.id).orElseThrow();
    }

    static ScanEntry entry(Fixture f) {
        return f.registry.find(f.id).orElseThrow();
    }

    /** The scenario scan up to its running HARD_LINKS phase: 3 folders, 5 files of 100 bytes, 2 candidates of 100. */
    static Fixture throughHardLinks() {
        Fixture f = started("10:00:00");
        f.core.emit(new ScanEvent.PhaseStarted(ScanPhase.WALK));
        f.core.walk(3, 5, 100);
        f.core.emit(new ScanEvent.PhaseStarted(ScanPhase.PARTIAL_HASH));
        f.core.emit(new ScanEvent.HashProgress(ScanPhase.PARTIAL_HASH, 5, 5, 500, 500));
        f.core.emit(new ScanEvent.PhaseStarted(ScanPhase.FULL_HASH));
        f.core.emit(new ScanEvent.HashProgress(ScanPhase.FULL_HASH, 2, 2, 200, 200));
        f.core.emit(new ScanEvent.PhaseStarted(ScanPhase.HARD_LINKS));
        f.core.emit(new ScanEvent.GroupDone(ScanPhase.HARD_LINKS, 1, 1));
        return f;
    }

    @Test
    @DisplayName("DFS-064.AC-1 Every byte hashed: FULL_HASH shows 3 of 3 files and 300 of 300 bytes until HARD_LINKS starts")
    void fullHashEndsAtItsTotalsBeforeHardLinks() {
        Fixture f = started("10:00:00");
        f.core.emit(new ScanEvent.PhaseStarted(ScanPhase.FULL_HASH));
        f.core.emit(new ScanEvent.HashProgress(ScanPhase.FULL_HASH, 0, 3, 0, 300));
        f.core.emit(new ScanEvent.HashProgress(ScanPhase.FULL_HASH, 1, 3, 100, 300));
        f.core.emit(new ScanEvent.HashProgress(ScanPhase.FULL_HASH, 2, 3, 200, 300));
        ScanProgress mid = progress(f);
        assertTrue(mid.hashedBytes() <= mid.hashBytesTotal(), mid.toString());

        f.core.emit(new ScanEvent.HashProgress(ScanPhase.FULL_HASH, 3, 3, 300, 300));

        ScanProgress p = progress(f);
        assertEquals("FULL_HASH", p.phase());
        assertEquals(3, p.hashedFiles());
        assertEquals(3, p.hashFilesTotal());
        assertEquals(300, p.hashedBytes());
        assertEquals(300, p.hashBytesTotal());
        assertEquals(new PhaseTotals("FULL_HASH", false, 0, 3, 300, 0), p.phases().getLast());

        f.core.emit(new ScanEvent.PhaseStarted(ScanPhase.HARD_LINKS));

        assertEquals("HARD_LINKS", progress(f).phase());
    }

    @Test
    @DisplayName("DFS-064.AC-2, DFS-064.AC-3 Finished phases keep their totals while the flat fields keep their meaning")
    void finishedPhasesKeepTheirTotals() {
        Fixture f = throughHardLinks();

        ScanProgress p = progress(f);

        assertEquals(List.of(
                new PhaseTotals("WALK", true, 3, 5, 500, 0),
                new PhaseTotals("PARTIAL_HASH", true, 0, 5, 500, 0),
                new PhaseTotals("FULL_HASH", true, 0, 2, 200, 0),
                new PhaseTotals("HARD_LINKS", false, 0, 0, 0, 1)), p.phases());
        // the flat fields: walk counts survive, the hash counters were reset by PhaseStarted(HARD_LINKS)
        assertEquals("HARD_LINKS", p.phase());
        assertEquals(3, p.directories());
        assertEquals(5, p.files());
        assertEquals(500, p.bytes());
        assertEquals(0, p.hashedFiles());
        assertEquals(0, p.hashFilesTotal());
        assertEquals(0, p.hashedBytes());
        assertEquals(0, p.hashBytesTotal());
    }

    @Test
    @DisplayName("DFS-064.AC-3 The flat hash counters are the current phase's: they follow HashProgress and are reset at PhaseStarted")
    void flatFieldsAreUnchanged() {
        Fixture f = started("10:00:00");
        f.core.emit(new ScanEvent.PhaseStarted(ScanPhase.WALK));
        f.core.walk(2, 4, 50);
        f.core.emit(new ScanEvent.PhaseStarted(ScanPhase.PARTIAL_HASH));
        f.core.emit(new ScanEvent.HashProgress(ScanPhase.PARTIAL_HASH, 1, 4, 50, 200));

        ScanProgress p = progress(f);

        assertEquals(2, p.directories());
        assertEquals(4, p.files());
        assertEquals(200, p.bytes());
        assertEquals(1, p.hashedFiles());
        assertEquals(4, p.hashFilesTotal());
        assertEquals(50, p.hashedBytes());
        assertEquals(200, p.hashBytesTotal());
        assertEquals(0, p.groupsDone());
        assertEquals(0, p.groupsTotal());
    }

    @Test
    @DisplayName("DFS-064.AC-2 GroupDone in a hash phase leaves that phase's files and bytes alone and its groups 0, as the core sends it")
    void groupDoneInHashPhasesKeepsFilesAndBytes() {
        Fixture f = started("10:00:00");
        f.core.emit(new ScanEvent.PhaseStarted(ScanPhase.WALK));
        f.core.walk(2, 5, 100);
        f.core.emit(new ScanEvent.PhaseStarted(ScanPhase.PARTIAL_HASH));
        f.core.emit(new ScanEvent.HashProgress(ScanPhase.PARTIAL_HASH, 0, 5, 0, 500));
        f.core.emit(new ScanEvent.HashProgress(ScanPhase.PARTIAL_HASH, 3, 5, 300, 500));
        f.core.emit(new ScanEvent.GroupDone(ScanPhase.PARTIAL_HASH, 1, 2));
        f.core.emit(new ScanEvent.HashProgress(ScanPhase.PARTIAL_HASH, 5, 5, 500, 500));
        f.core.emit(new ScanEvent.GroupDone(ScanPhase.PARTIAL_HASH, 2, 2));
        f.core.emit(new ScanEvent.PhaseStarted(ScanPhase.FULL_HASH));
        f.core.emit(new ScanEvent.HashProgress(ScanPhase.FULL_HASH, 0, 2, 0, 200));
        f.core.emit(new ScanEvent.HashProgress(ScanPhase.FULL_HASH, 2, 2, 200, 200));
        f.core.emit(new ScanEvent.GroupDone(ScanPhase.FULL_HASH, 1, 1));

        List<PhaseTotals> phases = progress(f).phases();

        assertEquals(new PhaseTotals("PARTIAL_HASH", true, 0, 5, 500, 0), phases.get(1), phases.toString());
        assertEquals(new PhaseTotals("FULL_HASH", false, 0, 2, 200, 0), phases.get(2), phases.toString());

        f.core.emit(new ScanEvent.PhaseStarted(ScanPhase.HARD_LINKS));
        f.core.emit(new ScanEvent.GroupDone(ScanPhase.HARD_LINKS, 1, 1));

        phases = progress(f).phases();
        assertEquals(new PhaseTotals("FULL_HASH", true, 0, 2, 200, 0), phases.get(2), phases.toString());
        assertEquals(new PhaseTotals("HARD_LINKS", false, 0, 0, 0, 1), phases.get(3), phases.toString());
    }

    @Test
    @DisplayName("DFS-064.AC-2 Only started phases are listed")
    void onlyStartedPhasesAreListed() {
        Fixture f = started("10:00:00");
        f.core.emit(new ScanEvent.PhaseStarted(ScanPhase.WALK));
        f.core.walk(3, 5, 100);
        f.core.emit(new ScanEvent.PhaseStarted(ScanPhase.PARTIAL_HASH));

        List<PhaseTotals> phases = progress(f).phases();

        assertEquals(2, phases.size(), phases.toString());
        assertEquals("WALK", phases.get(0).phase());
        assertTrue(phases.get(0).finished());
        assertEquals("PARTIAL_HASH", phases.get(1).phase());
        assertEquals(false, phases.get(1).finished());
    }

    @Test
    @DisplayName("DFS-064.AC-2 A phase that has sent no hash progress yet has totals 0")
    void aPhaseWithoutHashProgressHasZeroTotals() {
        Fixture f = started("10:00:00");
        f.core.emit(new ScanEvent.PhaseStarted(ScanPhase.WALK));
        f.core.emit(new ScanEvent.PhaseStarted(ScanPhase.PARTIAL_HASH));

        PhaseTotals partial = progress(f).phases().getLast();

        assertEquals(new PhaseTotals("PARTIAL_HASH", false, 0, 0, 0, 0), partial);
    }

    @Test
    @DisplayName("DFS-064.AC-2, DFS-064.AC-4 A completed scan finishes every entry, keeps its totals and lists them on its entry")
    void completedScanFinishesEveryEntry() {
        Fixture f = throughHardLinks();
        f.core.status = ScanStatus.COMPLETED;
        f.clock.now = at("10:41:00");
        f.registry.sweep();

        List<PhaseTotals> phases = entry(f).progress().phases();

        assertEquals(4, phases.size(), phases.toString());
        assertTrue(phases.stream().allMatch(PhaseTotals::finished), phases.toString());
        assertEquals(new PhaseTotals("WALK", true, 3, 5, 500, 0), phases.get(0));
        assertEquals(new PhaseTotals("FULL_HASH", true, 0, 2, 200, 0), phases.get(2));
        assertEquals(1, phases.get(3).groups());
    }

    @Test
    @DisplayName("DFS-064.AC-4 finishedAt is stamped when the scan ends, not when someone first reads it, and never changes")
    void finishedAtIsFixedWhenTheScanEnds() {
        Fixture f = started("10:00:00");
        f.core.emit(new ScanEvent.PhaseStarted(ScanPhase.WALK));
        f.clock.now = at("10:41:00");
        f.core.status = ScanStatus.COMPLETED;
        f.registry.sweep();
        f.clock.now = at("11:30:00");

        ScanEntry first = entry(f);
        ScanEntry second = entry(f);

        assertEquals(at("10:00:00"), first.startedAt());
        assertEquals(at("10:41:00"), first.finishedAt());
        assertEquals(first.finishedAt(), second.finishedAt());
        assertEquals(at("10:41:00"), f.registry.list().getFirst().finishedAt());
    }

    @Test
    @DisplayName("DFS-064.AC-4 A running scan has no finishedAt, and neither does a cancelling one")
    void aRunningScanHasNoFinishedAt() {
        Fixture f = started("10:00:00");
        f.core.emit(new ScanEvent.PhaseStarted(ScanPhase.FULL_HASH));
        f.clock.now = at("10:10:00");
        f.registry.sweep();

        assertNull(entry(f).finishedAt());

        f.core.status = ScanStatus.CANCELLING;
        f.registry.sweep();

        assertNull(entry(f).finishedAt());
    }

    @ParameterizedTest(name = "DFS-064.AC-4 A scan that ends {0} has status {1} and finishedAt 10:05:00")
    @CsvSource(delimiter = '|', value = {
            "when the user cancels it  | CANCELLED",
            "with a read error in core | FAILED",
    })
    void aScanThatEndsOtherwiseHasAFinishedAt(String how, ScanStatus status) {
        Fixture f = started("10:00:00");
        f.core.emit(new ScanEvent.PhaseStarted(ScanPhase.WALK));
        f.clock.now = at("10:05:00");
        f.core.status = status;
        if (status == ScanStatus.FAILED) {
            f.core.failure = "java.io.UncheckedIOException: read error";
        }
        f.registry.sweep();
        f.clock.now = at("10:20:00");

        ScanEntry e = entry(f);

        assertEquals(status.name(), e.progress().status());
        assertEquals(at("10:05:00"), e.finishedAt());
        // an unfinished phase of a scan that did not complete stays unfinished
        assertEquals(false, e.progress().phases().getLast().finished());
        assertNotNull(e.progress().phases());
    }
}
