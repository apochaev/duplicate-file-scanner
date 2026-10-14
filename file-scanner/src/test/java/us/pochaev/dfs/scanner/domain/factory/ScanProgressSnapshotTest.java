package us.pochaev.dfs.scanner.domain.factory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.scanner.domain.model.ScanEvent;
import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.domain.model.ScanPhase;
import us.pochaev.dfs.scanner.domain.model.ScanSnapshot;
import us.pochaev.dfs.scanner.domain.model.ScanStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The new progress events reach the listener but leave {@link ScanSnapshot} as it was (DFS-024 decision 6). */
class ScanProgressSnapshotTest {

    @TempDir
    Path temp;

    @Test
    @DisplayName("DFS-024.AC-5 The snapshot ignores the new events")
    void snapshotIgnoresTheNewEvents() throws IOException {
        Path sub = Files.createDirectories(temp.resolve("sub"));
        Files.writeString(sub.resolve("x.txt"), "hello duplicate");
        Files.writeString(sub.resolve("y.txt"), "hello duplicate");
        // runs the scan on the calling thread, so it has completed when start returns
        ScanUseCases ports = ScannerFactories.scanUseCases(Runnable::run);
        List<ScanEvent> events = new ArrayList<>();

        ScanId id = ((Result.Success<ScanId>) ports.start().start(List.of(temp), ScanConfig.defaults(), events::add)).value();
        ScanSnapshot snapshot = ((Result.Success<ScanSnapshot>) ports.status().status(id)).value();

        assertTrue(events.stream().anyMatch(e -> e instanceof ScanEvent.DirectoryWalked), "the listener gets the new events");
        assertTrue(events.stream().anyMatch(e -> e instanceof ScanEvent.HashProgress), "the listener gets the new events");
        assertEquals(new ScanSnapshot(ScanStatus.COMPLETED, ScanPhase.HARD_LINKS, 2, 1, 1, null, null), snapshot);
    }
}
