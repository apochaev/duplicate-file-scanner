package us.pochaev.dfs.scanner.infra.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.domain.model.ScanSnapshot;
import us.pochaev.dfs.scanner.domain.model.ScanStatus;
import us.pochaev.dfs.scanner.infra.port.ScanRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** DFS-018.AC-18: the in-memory repository's updates are atomic under real threads. */
class InMemoryScanRepositoryTest {

    private static final int THREADS = 8;
    private static final int UPDATES_PER_THREAD = 1000;

    @Test
    @DisplayName("DFS-018.AC-18 Concurrent updates to one scan are never lost")
    void concurrentUpdatesAreNeverLost() throws Exception {
        InMemoryScanRepository repository = new InMemoryScanRepository();
        ScanId id = ScanId.random();
        repository.create(new ScanRecord(id,
                new ScanSnapshot(ScanStatus.RUNNING, null, 0, 0, 0, null, null), null));
        CountDownLatch go = new CountDownLatch(1);

        try (ExecutorService threads = Executors.newFixedThreadPool(THREADS)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                futures.add(threads.submit(() -> {
                    go.await();
                    for (int i = 0; i < UPDATES_PER_THREAD; i++) {
                        repository.update(id, record -> new ScanRecord(record.id(),
                                new ScanSnapshot(record.snapshot().status(), null,
                                        record.snapshot().filesWalked() + 1, 0, 0, null, null),
                                null));
                    }
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }

        assertEquals(THREADS * UPDATES_PER_THREAD, repository.find(id).orElseThrow().snapshot().filesWalked());
    }
}
