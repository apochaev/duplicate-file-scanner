package us.pochaev.dfs.scanner.application;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import us.pochaev.dfs.scanner.domain.model.FileEntry;

/**
 * Reproduces the heap-per-entry figure DFS-001's Definition of done requires (measure 1 million
 * synthetic entries, record the byte-per-entry figure in the ticket before closing it).
 *
 * <p>{@code @Disabled}: this is a manual benchmark, not part of the ordinary test suite —
 * {@code Runtime.totalMemory()}/{@code freeMemory()} readings are noisy under a shared test JVM
 * and this allocates a large amount of heap on purpose. Run it directly (for example from an
 * IDE, or {@code ./gradlew :file-scanner:test --tests SizeIndexHeapBenchmark
 * -Djunit.jupiter.conditions.deactivate="org.junit.*DisabledCondition"}) to reproduce the figure.
 *
 * <p>Original measurement (implementer, 2026-09-22, throwaway class, not committed): ~213
 * bytes/entry. Independent corroboration (pilot control branch, separate implementation,
 * 2026-09-22): ~210 bytes/entry. This committed version exists so the figure can be reproduced
 * by anyone, going forward, instead of relying on either throwaway measurement.
 */
class SizeIndexHeapBenchmark {

    private static final int ENTRY_COUNT = 1_000_000;

    @Test
    @Disabled("manual benchmark: run directly to reproduce the DFS-001 heap-per-entry figure")
    void heapPerEntryForOneMillionEntries() {
        System.gc();
        long before = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        SizeIndex index = new SizeIndex();
        Instant now = Instant.now();
        for (int i = 0; i < ENTRY_COUNT; i++) {
            // Half the sizes are shared by two entries (forming a group), half are unique
            // (dropped as singletons) — the same worst-case shape as the ticket's own estimate:
            // every entry is retained until groups() runs, regardless of whether it survives.
            long size = i / 2;
            Path path = Path.of("bench", "file-" + i + ".dat");
            index.add(new FileEntry(path, size, now));
        }

        System.gc();
        long after = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        long retained = after - before;
        double bytesPerEntry = (double) retained / ENTRY_COUNT;
        System.out.printf(
                "SizeIndex: %d entries retained ~%.1f bytes/entry (%.1f MB total)%n",
                ENTRY_COUNT, bytesPerEntry, retained / (1024.0 * 1024.0));

        // Sanity bound only (JVM heap accounting is noisy) — the DFS-001 ticket's estimate was
        // 250-400 bytes/entry; both independent measurements landed under it, around 210 bytes.
        assertTrue(bytesPerEntry > 0, "expected positive retained heap");

        // groups() still runs correctly at this scale.
        assertTrue(index.groups().size() > 0, "expected at least one candidate group");
    }
}
