package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.model.ScanSnapshot;
import us.pochaev.dfs.scanner.domain.model.ScanStatus;
import us.pochaev.dfs.scanner.infra.adapter.InMemoryScanRepository;
import us.pochaev.dfs.scanner.infra.adapter.NioContentHasher;
import us.pochaev.dfs.scanner.infra.adapter.NioFileIdentity;
import us.pochaev.dfs.scanner.infra.adapter.NioFileTreeWalker;
import us.pochaev.dfs.scanner.infra.adapter.NioLocationResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DFS-018.AC-17: the one real-thread test. Real virtual-thread executor, in-memory repository and
 * real NIO adapters over a {@code @TempDir}. Waiting is on the tasks' own futures, bounded.
 */
class ScanServiceRealThreadTest {

    @Test
    @DisplayName("DFS-018.AC-17 Two concurrent scans on real threads and real files do not share state")
    void twoConcurrentScansDoNotShareState(@TempDir Path dir) throws Exception {
        Path root = dir.toRealPath();
        Path one = Files.createDirectory(root.resolve("one"));
        Path two = Files.createDirectory(root.resolve("two"));
        write(one.resolve("a.bin"), "content of folder one");
        write(one.resolve("b.bin"), "content of folder one");
        write(two.resolve("c.bin"), "different content of folder two");
        write(two.resolve("d.bin"), "different content of folder two");

        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new CopyOnWriteArrayList<>();
            ScanService service = new ScanService(new NioLocationResolver(),
                    new ScanPipeline(new NioLocationResolver(), new NioFileTreeWalker(), new NioContentHasher(),
                            new NioFileIdentity()),
                    new InMemoryScanRepository(),
                    task -> futures.add(threads.submit(task)));

            ScanId first = success(service.start(List.of(one), ScanConfig.defaults()));
            ScanId second = success(service.start(List.of(two), ScanConfig.defaults()));

            assertEquals(2, futures.size());
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            assertEquals(ScanStatus.COMPLETED, success(service.status(first)).status());
            assertEquals(ScanStatus.COMPLETED, success(service.status(second)).status());
            assertSetOf(success(service.result(first)), one.resolve("a.bin"), one.resolve("b.bin"));
            assertSetOf(success(service.result(second)), two.resolve("c.bin"), two.resolve("d.bin"));
        }
    }

    private static void assertSetOf(ScanResult result, Path x, Path y) {
        assertEquals(1, result.sets().size());
        assertEquals(List.of(x, y), result.sets().getFirst().files().stream().map(FileEntry::path).toList());
    }

    private static void write(Path file, String text) throws IOException {
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private static <T> T success(Result<T> result) {
        if (result instanceof Result.Success<T> success) {
            return success.value();
        }
        return org.junit.jupiter.api.Assertions.fail("expected Success but was " + result);
    }
}
