package us.pochaev.dfs.scanner.application;

import java.nio.file.Path;
import java.time.Instant;
import us.pochaev.dfs.scanner.domain.model.FileEntry;

/**
 * Reproduces the heap-per-folder figure DFS-030's Definition of done requires (decision 10): 100,000
 * folders with a few counted files each, then the retained bytes per folder, recorded in the ticket
 * before it closes. 100,000 folders is about ten times the 2026-10-31 drive.
 *
 * <p>A plain method, not a test, like {@link SizeIndexHeapBenchmark}: the story gate's disabled-test
 * check rejects any disabled test, and heap readings are too noisy for the ordinary suite. Run it by
 * hand (from an IDE, a temporary {@code main}, or {@code jshell} on the compiled classes).
 */
class FolderCounterHeapBenchmark {

    private static final int FOLDER_COUNT = 100_000;
    private static final int FILES_PER_FOLDER = 3;

    void heapPerFolderForOneHundredThousandFolders() {
        System.gc();
        long before = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        ScanPipeline.FolderCounter counter = new ScanPipeline.FolderCounter(1);
        Instant now = Instant.now();
        for (int folder = 0; folder < FOLDER_COUNT; folder++) {
            Path dir = Path.of("bench", "group-" + folder / 100, "folder-" + folder);
            for (int file = 0; file < FILES_PER_FOLDER; file++) {
                counter.add(new FileEntry(dir.resolve("file-" + file + ".dat"), 1000 + file, now));
            }
        }

        System.gc();
        long after = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        long retained = after - before;
        int totals = counter.totals().size();
        System.out.printf("FolderCounter: %d folders retained ~%.1f bytes/folder (%.1f MB total)%n",
                totals, (double) retained / FOLDER_COUNT, retained / (1024.0 * 1024.0));
        if (totals != FOLDER_COUNT) {
            throw new AssertionError("expected " + FOLDER_COUNT + " totals, got " + totals);
        }
    }
}
