package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.FolderTotal;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/** {@link ScanPipeline.FolderCounter} on hand-built entries (DFS-030 decision 2, checklist items 3, 4, 6). */
class FolderCounterTest {

    private static FileEntry entry(String path, long size) {
        return new FileEntry(Path.of(path), size, Instant.EPOCH);
    }

    @Test
    @DisplayName("DFS-030.AC-2 Files are totalled per direct parent folder, sorted by folder path")
    void filesAreTotalledPerParentFolder() {
        ScanPipeline.FolderCounter counter = new ScanPipeline.FolderCounter(1);
        counter.add(entry("root/p/q/c.jpg", 40));
        counter.add(entry("root/p/a.jpg", 100));
        counter.add(entry("root/p/b.jpg", 250));

        assertEquals(List.of(
                new FolderTotal(Path.of("root/p"), 2, 350),
                new FolderTotal(Path.of("root/p/q"), 1, 40)), counter.totals());
    }

    @Test
    @DisplayName("DFS-030.AC-2 Nothing counted gives no totals")
    void nothingCountedGivesNoTotals() {
        ScanPipeline.FolderCounter counter = new ScanPipeline.FolderCounter(1);

        assertEquals(List.of(), counter.totals());
    }

    @Test
    @DisplayName("DFS-030.AC-2 The order files arrive in does not change the totals")
    void arrivalOrderDoesNotMatter() {
        List<FileEntry> files = List.of(entry("root/z/1", 5), entry("root/m/2", 6), entry("root/z/3", 7),
                entry("root/a/4", 8), entry("root/m/5", 9));
        ScanPipeline.FolderCounter forward = new ScanPipeline.FolderCounter(1);
        files.forEach(forward::add);
        ScanPipeline.FolderCounter backward = new ScanPipeline.FolderCounter(1);
        files.reversed().forEach(backward::add);

        assertEquals(forward.totals(), backward.totals());
        assertEquals(List.of(Path.of("root/a"), Path.of("root/m"), Path.of("root/z")),
                forward.totals().stream().map(FolderTotal::folder).toList());
    }

    @Test
    @DisplayName("DFS-030.AC-3 A file below the minimum size is not counted, one at the minimum is")
    void filesBelowMinSizeAreNotCounted() {
        ScanPipeline.FolderCounter counter = new ScanPipeline.FolderCounter(5);
        counter.add(entry("root/f/tiny.txt", 3));
        counter.add(entry("root/f/empty.txt", 0));
        counter.add(entry("root/f/edge.txt", 5));
        counter.add(entry("root/f/kept.txt", 8));
        counter.add(entry("root/g/tiny.txt", 4));

        assertEquals(List.of(new FolderTotal(Path.of("root/f"), 2, 13)), counter.totals());
    }

    @Test
    @DisplayName("DFS-030.AC-5 Two paths of equal size in one folder count as two files")
    void everyAddCounts() {
        ScanPipeline.FolderCounter counter = new ScanPipeline.FolderCounter(1);
        counter.add(entry("root/l/one.dat", 30));
        counter.add(entry("root/l/two.dat", 30));

        assertEquals(List.of(new FolderTotal(Path.of("root/l"), 2, 60)), counter.totals());
    }

    @Test
    @DisplayName("DFS-030.AC-6 The totals list cannot be changed and a returned list ignores later counting")
    void totalsAreImmutableSnapshots() {
        ScanPipeline.FolderCounter counter = new ScanPipeline.FolderCounter(1);
        counter.add(entry("root/a/x", 10));
        List<FolderTotal> first = counter.totals();

        try {
            first.add(new FolderTotal(Path.of("root/b"), 1, 1));
            fail("expected UnsupportedOperationException when adding to totals()");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
        counter.add(entry("root/a/y", 20));
        counter.add(entry("root/b/z", 5));

        assertEquals(List.of(new FolderTotal(Path.of("root/a"), 1, 10)), first);
        assertEquals(List.of(new FolderTotal(Path.of("root/a"), 2, 30), new FolderTotal(Path.of("root/b"), 1, 5)),
                counter.totals());
    }
}
