package us.pochaev.dfs.scanner.domain.factory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.pochaev.dfs.scanner.domain.model.ErrorCode;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.model.ScanSnapshot;
import us.pochaev.dfs.scanner.domain.model.ScanStatus;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ScannerFactoriesTest {

    private static final byte[] TEN = "0123456789".getBytes();

    @TempDir
    Path dir;

    /** Holds every submitted task until the test runs it. */
    private static final class HoldingExecutor implements Executor {
        final List<Runnable> held = new ArrayList<>();

        @Override
        public void execute(Runnable task) {
            held.add(task);
        }
    }

    private static <T> T success(Result<T> r) {
        if (r instanceof Result.Success<T> s) {
            return s.value();
        }
        return fail("expected Success but was " + r);
    }

    private static ErrorCode failureCode(Result<?> r) {
        if (r instanceof Result.Failure<?> f) {
            return f.code();
        }
        return fail("expected Failure but was " + r);
    }

    private static ScanId started(ScanUseCases uc, Path root) {
        return success(uc.start().start(List.of(root), ScanConfig.defaults()));
    }

    private static ScanStatus statusOf(ScanUseCases uc, ScanId id) {
        ScanSnapshot snapshot = success(uc.status().status(id));
        return snapshot.status();
    }

    private static Set<String> names(ScanResult result, int set) {
        return result.sets().get(set).files().stream()
                .map(f -> f.path().getFileName().toString()).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("DFS-019.AC-1 The factory returns all four use cases")
    void returnsAllFourUseCases() {
        ScanUseCases uc = ScannerFactories.scanUseCases(Runnable::run);

        assertNotNull(uc.start());
        assertNotNull(uc.cancel());
        assertNotNull(uc.status());
        assertNotNull(uc.result());
    }

    @Test
    @DisplayName("DFS-019.AC-2 The four ports share one scan state")
    void fourPortsShareState() throws IOException {
        Files.write(dir.resolve("a.txt"), TEN);
        Files.write(dir.resolve("b.txt"), TEN);
        ScanUseCases uc = ScannerFactories.scanUseCases(Runnable::run);

        ScanId id = started(uc, dir);

        assertEquals(ScanStatus.COMPLETED, statusOf(uc, id));
        ScanResult result = success(uc.result().result(id));
        assertEquals(1, result.sets().size());
        assertEquals(Set.of("a.txt", "b.txt"), names(result, 0));
        assertEquals(ScanStatus.COMPLETED, success(uc.cancel().cancel(id)).status());
    }

    @Test
    @DisplayName("DFS-019.AC-3 Separate builds do not share state")
    void separateBuildsAreIndependent() throws IOException {
        Files.write(dir.resolve("a.txt"), TEN);
        ScanUseCases first = ScannerFactories.scanUseCases(Runnable::run);
        ScanUseCases second = ScannerFactories.scanUseCases(Runnable::run);

        ScanId id = started(first, dir);

        assertEquals(ErrorCode.UNKNOWN_SCAN, failureCode(second.status().status(id)));
    }

    @Test
    @DisplayName("DFS-019.AC-4 The default adapters read the real disk")
    void defaultAdaptersReadRealDisk() throws IOException {
        Files.createDirectories(dir.resolve("dup"));
        Files.createDirectories(dir.resolve("empty"));
        Files.createDirectories(dir.resolve("unique"));
        Files.createDirectories(dir.resolve("linked"));
        Files.write(dir.resolve("dup/a.txt"), TEN);
        Files.write(dir.resolve("dup/b.txt"), TEN);
        Files.write(dir.resolve("empty/x.txt"), new byte[0]);
        Files.write(dir.resolve("empty/y.txt"), new byte[0]);
        Files.write(dir.resolve("unique/c.txt"), "abcdefghij".getBytes());
        Files.write(dir.resolve("linked/l1.bin"), new byte[20]);
        try {
            Files.createLink(dir.resolve("linked/l2.bin"), dir.resolve("linked/l1.bin"));
        } catch (UnsupportedOperationException | FileSystemException e) {
            assumeTrue(false, "platform refuses hard links: " + e);
        }
        ScanUseCases uc = ScannerFactories.scanUseCases(Runnable::run);

        ScanId id = started(uc, dir);

        ScanResult result = success(uc.result().result(id));
        assertEquals(1, result.sets().size());
        assertEquals(Set.of("a.txt", "b.txt"), names(result, 0));
    }

    @Test
    @DisplayName("DFS-019.AC-4 A missing location is rejected by the real resolver")
    void missingLocationRejected() {
        ScanUseCases uc = ScannerFactories.scanUseCases(Runnable::run);

        Result<ScanId> r = uc.start().start(List.of(dir.resolve("nope")), ScanConfig.defaults());

        assertEquals(ErrorCode.LOCATION_NOT_FOUND, failureCode(r));
    }

    @Test
    @DisplayName("DFS-019.AC-5 A null executor is a caller bug")
    void nullExecutorThrows() {
        // try/fail/catch rather than assertThrows: the stub's UnsupportedOperationException must
        // propagate uncaught so the red gate sees it (use-case test-guide, "What not to do").
        try {
            ScannerFactories.scanUseCases(null);
            fail("expected NullPointerException for a null executor");
        } catch (NullPointerException expected) {
            // a null executor is a caller bug
        }
    }

    @Test
    @DisplayName("DFS-019.AC-6 Scans run on the caller's executor")
    void scansRunOnCallersExecutor() throws IOException {
        Files.write(dir.resolve("a.txt"), TEN);
        Files.write(dir.resolve("b.txt"), TEN);
        HoldingExecutor executor = new HoldingExecutor();
        ScanUseCases uc = ScannerFactories.scanUseCases(executor);

        ScanId id = started(uc, dir);

        assertEquals(ScanStatus.QUEUED, statusOf(uc, id));
        assertEquals(1, executor.held.size());
        executor.held.get(0).run();
        assertEquals(ScanStatus.COMPLETED, statusOf(uc, id));
    }
}
