package us.pochaev.dfs.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import us.pochaev.dfs.scanner.domain.model.ScanEvent;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static us.pochaev.dfs.scanner.domain.model.ScanPhase.FULL_HASH;
import static us.pochaev.dfs.scanner.domain.model.ScanPhase.HARD_LINKS;
import static us.pochaev.dfs.scanner.domain.model.ScanPhase.PARTIAL_HASH;
import static us.pochaev.dfs.scanner.domain.model.ScanPhase.WALK;

/** {@link ProgressDisplay} driven with a fake clock and explicit redraws (DFS-024 decision 8). */
class ProgressDisplayTest {

    private static final long SECOND = 1_000_000_000L;
    private static final long GIB = 1024L * 1024 * 1024;
    private static final long MIB = 1024L * 1024;

    private final AtomicLong clock = new AtomicLong();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    static Stream<Arguments> phaseLines() {
        return Stream.of(
                Arguments.of("walk", (BiConsumer<ProgressDisplayTest, ProgressDisplay>) (t, d) -> {
                    t.send(d, 0, new ScanEvent.PhaseStarted(WALK), new ScanEvent.DirectoryWalked(3),
                            new ScanEvent.FileWalked(5, 5_000_000));
                    t.at(2);
                }, "Walking:    3 folders, 5 files, 4.8 MB found  0:02"),
                Arguments.of("sampling", (BiConsumer<ProgressDisplayTest, ProgressDisplay>) (t, d) -> {
                    t.send(d, 0, new ScanEvent.PhaseStarted(PARTIAL_HASH),
                            new ScanEvent.HashProgress(PARTIAL_HASH, 2, 4, 2000, 4000));
                    t.at(3);
                }, "Sampling:   2 / 4 files  0:03"),
                Arguments.of("hashing with ETA", (BiConsumer<ProgressDisplayTest, ProgressDisplay>) (t, d) -> {
                    t.send(d, 10, new ScanEvent.PhaseStarted(FULL_HASH),
                            new ScanEvent.HashProgress(FULL_HASH, 1, 4, GIB, 4 * GIB));
                    t.at(20);
                }, "Hashing:    1 / 4 files, 1.0 GB / 4.0 GB (25%)  ETA 0:30  0:20"),
                Arguments.of("hashing under 3 s", (BiConsumer<ProgressDisplayTest, ProgressDisplay>) (t, d) -> {
                    t.send(d, 10, new ScanEvent.PhaseStarted(FULL_HASH),
                            new ScanEvent.HashProgress(FULL_HASH, 0, 4, GIB, 4 * GIB));
                    t.at(12);
                }, "Hashing:    0 / 4 files, 1.0 GB / 4.0 GB (25%)  ETA --:--  0:12"),
                Arguments.of("hashing under 1%", (BiConsumer<ProgressDisplayTest, ProgressDisplay>) (t, d) -> {
                    t.send(d, 10, new ScanEvent.PhaseStarted(FULL_HASH),
                            new ScanEvent.HashProgress(FULL_HASH, 0, 400, 4 * MIB, 1000 * GIB));
                    t.at(20);
                }, "Hashing:    0 / 400 files, 4.0 MB / 1000.0 GB (0%)  ETA --:--  0:20"),
                Arguments.of("hard links past an hour", (BiConsumer<ProgressDisplayTest, ProgressDisplay>) (t, d) -> {
                    t.send(d, 0, new ScanEvent.PhaseStarted(HARD_LINKS), new ScanEvent.GroupDone(HARD_LINKS, 7, 9));
                    t.at(3725);
                }, "Hard links: 7 / 9 sets  1:02:05"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("phaseLines")
    @DisplayName("DFS-024.AC-6, DFS-024.AC-7 Phase lines are rendered from events and the clock")
    void phaseLines(String name, BiConsumer<ProgressDisplayTest, ProgressDisplay> events, String line) {
        ProgressDisplay display = display(ProgressDisplay.Mode.OFF);

        events.accept(this, display);

        assertEquals(line, display.line());
    }

    @Test
    @DisplayName("DFS-024.AC-8 Live mode redraws in place, also without new events, and keeps finished lines")
    void liveModeRedrawsInPlace() {
        ProgressDisplay display = display(ProgressDisplay.Mode.LIVE);
        send(display, 0, new ScanEvent.PhaseStarted(WALK), new ScanEvent.FileWalked(1, 10));

        at(1);
        display.redraw();
        at(2);
        display.redraw();
        send(display, 2, new ScanEvent.PhaseStarted(PARTIAL_HASH));
        display.redraw();

        String walk1 = "Walking:    0 folders, 1 files, 10 B found  0:01";
        String walk2 = "Walking:    0 folders, 1 files, 10 B found  0:02";
        String sampling = "Sampling:   0 / 0 files  0:02";
        assertEquals("\r" + walk1 + "\r" + walk2 + "\r" + walk2 + "\n"
                + "\r" + sampling, output());
    }

    @Test
    @DisplayName("DFS-024.AC-8 A redraw of a shorter line is padded to the previous line's length")
    void liveModePadsAShorterLine() {
        ProgressDisplay display = display(ProgressDisplay.Mode.LIVE);
        send(display, 10, new ScanEvent.PhaseStarted(FULL_HASH),
                new ScanEvent.HashProgress(FULL_HASH, 1, 4, GIB, 4 * GIB));
        at(12);
        display.redraw();
        at(20);
        display.redraw();

        String before = "Hashing:    1 / 4 files, 1.0 GB / 4.0 GB (25%)  ETA --:--  0:12";
        String after = "Hashing:    1 / 4 files, 1.0 GB / 4.0 GB (25%)  ETA 0:30  0:20";
        assertEquals("\r" + before + "\r" + after + " ", output());
    }

    @Test
    @DisplayName("DFS-024.AC-9 Log mode writes plain lines, throttled")
    void logModeWritesThrottledLines() {
        ProgressDisplay display = display(ProgressDisplay.Mode.LOG);
        send(display, 0, new ScanEvent.PhaseStarted(WALK));
        for (int i = 1; i <= 120; i++) {
            clock.set(i * SECOND / 10);
            display.onEvent(new ScanEvent.FileWalked(i, i));
        }
        send(display, 12, new ScanEvent.PhaseStarted(PARTIAL_HASH));

        String out = output();
        assertFalse(out.contains("\r"), out);
        assertEquals(List.of(
                "Walking:    0 folders, 50 files, 50 B found  0:05",
                "Walking:    0 folders, 100 files, 100 B found  0:10",
                "Walking:    0 folders, 120 files, 120 B found  0:12"), out.lines().toList());
    }

    @Test
    @DisplayName("DFS-024.AC-10 Off mode writes nothing")
    void offModeWritesNothing() {
        ProgressDisplay display = display(ProgressDisplay.Mode.OFF);
        send(display, 0, new ScanEvent.PhaseStarted(WALK), new ScanEvent.FileWalked(1, 10));
        at(6);
        display.redraw();
        send(display, 6, new ScanEvent.FileWalked(2, 20), new ScanEvent.PhaseStarted(PARTIAL_HASH));
        display.completed(0);

        assertEquals("", output());
    }

    @Test
    @DisplayName("DFS-028.AC-3 A final event and the finish in the same second print the line once")
    void finishInSameSecondIsNotRepeated() {
        ProgressDisplay display = display(ProgressDisplay.Mode.LOG);
        send(display, 0, new ScanEvent.PhaseStarted(HARD_LINKS));
        send(display, 1255, new ScanEvent.GroupDone(HARD_LINKS, 1, 1));

        display.completed(1);

        assertEquals(List.of(
                "Hard links: 1 / 1 sets  20:55",
                "Done in 20:55: 0 files found, 0 compared, 1 duplicate sets"), output().lines().toList());
    }

    @Test
    @DisplayName("DFS-028.AC-3 The finish a second after the final event does not repeat the line")
    void finishNextSecondIsNotRepeated() {
        ProgressDisplay display = display(ProgressDisplay.Mode.LOG);
        send(display, 0, new ScanEvent.PhaseStarted(HARD_LINKS));
        send(display, 1255, new ScanEvent.GroupDone(HARD_LINKS, 1, 1));
        at(1256);

        display.completed(1);

        assertEquals(List.of(
                "Hard links: 1 / 1 sets  20:55",
                "Done in 20:56: 0 files found, 0 compared, 1 duplicate sets"), output().lines().toList());
    }

    @Test
    @DisplayName("DFS-028.AC-3 A finished line with new counts is still printed")
    void finishWithNewCountsIsPrinted() {
        ProgressDisplay display = display(ProgressDisplay.Mode.LOG);
        send(display, 0, new ScanEvent.PhaseStarted(FULL_HASH));
        send(display, 5, new ScanEvent.HashProgress(FULL_HASH, 10, 20, 10 * MIB, 20 * MIB));
        send(display, 7, new ScanEvent.HashProgress(FULL_HASH, 20, 20, 20 * MIB, 20 * MIB));
        send(display, 7, new ScanEvent.PhaseStarted(HARD_LINKS));

        List<String> lines = output().lines().toList();
        assertEquals(2, lines.size(), output());
        assertTrue(lines.get(0).startsWith("Hashing:    10 / 20 files") && lines.get(0).endsWith("  0:05"), lines.get(0));
        assertTrue(lines.get(1).startsWith("Hashing:    20 / 20 files") && lines.get(1).endsWith("  0:07"), lines.get(1));
    }

    @Test
    @DisplayName("DFS-025.AC-1 Before 99% of bytes, the ETA adds a per-file cost for the files left")
    void etaAddsPerFileCost() {
        ProgressDisplay display = sampledThenHashing(100);

        send(display, 20, new ScanEvent.HashProgress(FULL_HASH, 10, 110, 500_000_000, 1_000_000_000));

        // bytes: 500,000,000 left at 50,000,000 per second = 10 s; files: 100 left x 0.1 s = 10 s
        assertTrue(display.line().contains("ETA 0:20"), display.line());
    }

    @Test
    @DisplayName("DFS-025.AC-1 From 99% of bytes on, the ETA uses the recent files rate")
    void etaFollowsRecentFilesRate() {
        ProgressDisplay display = sampledThenHashing(100);

        send(display, 60, new ScanEvent.HashProgress(FULL_HASH, 50, 110, 990_000_000, 1_000_000_000));
        send(display, 70, new ScanEvent.HashProgress(FULL_HASH, 70, 110, 995_000_000, 1_000_000_000));

        // base: the report at 60 s, the newest at least 5 s old; 20 files in 10 s = 2 per second; 40 left / 2 = 20 s
        assertTrue(display.line().contains("ETA 0:20"), display.line());
    }

    @Test
    @DisplayName("DFS-025.AC-1 The existing guards still show no estimate too early")
    void etaGuardsStay() {
        ProgressDisplay early = sampledThenHashing(100);
        send(early, 12, new ScanEvent.HashProgress(FULL_HASH, 2, 110, 100_000_000, 1_000_000_000));
        assertTrue(early.line().contains("ETA --:--"), early.line());

        ProgressDisplay small = sampledThenHashing(100);
        send(small, 20, new ScanEvent.HashProgress(FULL_HASH, 3, 110, 5_000_000, 1_000_000_000));
        assertTrue(small.line().contains("ETA --:--"), small.line());
    }

    @Test
    @DisplayName("DFS-025.AC-2 With no files sampled, the per-file cost is 0")
    void etaWithoutSamplingIsByteBased() {
        ProgressDisplay display = sampledThenHashing(0);

        send(display, 20, new ScanEvent.HashProgress(FULL_HASH, 10, 110, 500_000_000, 1_000_000_000));

        assertTrue(display.line().contains("ETA 0:10"), display.line());
    }

    @Test
    @DisplayName("DFS-025.AC-2 From 99% of bytes on, with no file finished since the recent base, the ETA falls back")
    void etaFallsBackWhenNoFileFinished() {
        ProgressDisplay display = sampledThenHashing(100);

        send(display, 60, new ScanEvent.HashProgress(FULL_HASH, 50, 110, 990_000_000, 1_000_000_000));
        send(display, 70, new ScanEvent.HashProgress(FULL_HASH, 50, 110, 995_000_000, 1_000_000_000));

        // bytes: 5,000,000 left at 995,000,000 per 60 s, about 0.3 s; files: 60 left x 0.1 s = 6 s
        assertTrue(display.line().contains("ETA 0:06"), display.line());
    }

    @Test
    @DisplayName("DFS-025.AC-3 The percentage stays a byte percentage")
    void percentageStaysBytes() {
        ProgressDisplay display = sampledThenHashing(100);

        send(display, 70, new ScanEvent.HashProgress(FULL_HASH, 50, 110, 995_000_000, 1_000_000_000));

        assertTrue(display.line().contains("(99%)"), display.line());
    }

    /** Sampling from 0 s to 10 s over {@code sampled} files, then hashing from 10 s (DFS-025's Background). */
    private ProgressDisplay sampledThenHashing(long sampled) {
        ProgressDisplay display = display(ProgressDisplay.Mode.OFF);
        send(display, 0, new ScanEvent.PhaseStarted(PARTIAL_HASH));
        if (sampled > 0) {
            send(display, 0, new ScanEvent.HashProgress(PARTIAL_HASH, sampled, sampled, sampled * 1000, sampled * 1000));
        }
        send(display, 10, new ScanEvent.PhaseStarted(FULL_HASH));
        return display;
    }

    private ProgressDisplay display(ProgressDisplay.Mode mode) {
        return new ProgressDisplay(mode, new PrintStream(bytes, true, StandardCharsets.UTF_8), clock::get);
    }

    private void send(ProgressDisplay display, long seconds, ScanEvent... events) {
        at(seconds);
        for (ScanEvent event : events) {
            display.onEvent(event);
        }
    }

    private void at(long seconds) {
        clock.set(seconds * SECOND);
    }

    private String output() {
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
