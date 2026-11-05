package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.pochaev.dfs.scanner.domain.factory.PlanExecutionUseCases;
import us.pochaev.dfs.scanner.domain.factory.ScannerFactories;
import us.pochaev.dfs.scanner.domain.model.DeletionPlan;
import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.ExecutionConfig;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.JobStatus;
import us.pochaev.dfs.scanner.domain.model.KeepAndQuarantine;
import us.pochaev.dfs.scanner.domain.model.OperationOutcome;
import us.pochaev.dfs.scanner.domain.model.PlanExecutionId;
import us.pochaev.dfs.scanner.domain.model.PlanExecutionReport;
import us.pochaev.dfs.scanner.domain.model.PlanRequest;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The one real-thread test of plan execution (DFS-051): virtual threads, NIO adapters, files in a temp folder. */
class PlanExecutionRealThreadTest {

    @TempDir
    Path t;

    private static FileEntry entry(Path p) throws IOException {
        BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return new FileEntry(p, a.size(), a.lastModifiedTime().toInstant());
    }

    @SuppressWarnings("unchecked")
    private static <T> T ok(Result<T> result) {
        return ((Result.Success<T>) assertInstanceOf(Result.Success.class, result, String.valueOf(result))).value();
    }

    @Test
    @DisplayName("DFS-051.AC-21 A plan over two identical files executes end to end on a real executor")
    void endToEndOnRealFiles() throws Exception {
        byte[] content = "the same twenty-seven bytes".getBytes(StandardCharsets.UTF_8);
        Path a = t.resolve("lib/A/photo.jpg");
        Path b = t.resolve("lib/B/photo.jpg");
        Files.createDirectories(a.getParent());
        Files.createDirectories(b.getParent());
        Files.write(a, content);
        Files.write(b, content);
        ScanResult scan = new ScanResult(List.of(t.resolve("lib")), List.of(),
                List.of(new DuplicateSet(content.length, "d-photo", List.of(entry(a), entry(b)))), List.of(), 0);
        DeletionPlan plan = ok(ScannerFactories.previewDeletionPlan().preview(scan, new PlanRequest(
                List.of(new KeepAndQuarantine(t.resolve("lib/A"), t.resolve("lib/B"))), List.of(), List.of())));
        Path manifests = t.resolve("m");
        List<Future<?>> futures = new CopyOnWriteArrayList<>();
        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            PlanExecutionUseCases useCases = ScannerFactories.planExecutionUseCases(
                    task -> futures.add(threads.submit(task)), new ExecutionConfig(manifests, List.of(t)));

            PlanExecutionId id = ok(useCases.start().start(scan, plan));

            assertEquals(1, futures.size());
            futures.get(0).get(10, TimeUnit.SECONDS);
            assertEquals(JobStatus.COMPLETED, ok(useCases.status().status(id)).status());
            PlanExecutionReport report = ok(useCases.result().result(id));
            assertInstanceOf(OperationOutcome.Moved.class, report.outcomes().get(0));
            Path inQuarantine = t.resolve(".dfs-quarantine").resolve(id.value().toString())
                    .resolve(t.relativize(b));
            assertTrue(Files.exists(inQuarantine));
            assertTrue(java.util.Arrays.equals(content, Files.readAllBytes(inQuarantine)));
            assertFalse(Files.exists(b));
            assertTrue(java.util.Arrays.equals(content, Files.readAllBytes(a)));
            String manifest = Files.readString(manifests.resolve(id.value() + ".jsonl"), StandardCharsets.UTF_8);
            assertTrue(manifest.contains("\"moved\""), manifest);
            assertTrue(manifest.contains("\"end\""), manifest);
        }
    }
}
