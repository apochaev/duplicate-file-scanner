package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.FolderTotal;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.scanner.domain.model.ScanListener;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.model.SkipReason;
import us.pochaev.dfs.scanner.domain.model.WalkEvent;
import us.pochaev.dfs.scanner.infra.port.ContentHasher;
import us.pochaev.dfs.scanner.infra.port.FileIdentity;
import us.pochaev.dfs.scanner.infra.port.FileTreeWalker;
import us.pochaev.dfs.scanner.infra.port.LocationResolver;
import us.pochaev.dfs.scanner.infra.port.WalkListener;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The roots and per-folder totals {@link ScanPipeline} puts in the {@link ScanResult} (DFS-030),
 * against hand-written fakes of the four ports, as {@code ScanPipelineTest} does.
 */
class ScanFolderTotalsTest {

    private static final ScanListener NO_OP_LISTENER = event -> { };
    private static final BooleanSupplier NEVER_STOP = () -> false;
    private static final Instant WHEN = Instant.parse("2024-01-01T10:00:00Z");

    private static ScanConfig config(long minSize, boolean defaultExcludes) {
        return new ScanConfig(minSize, 65536, 131072, defaultExcludes);
    }

    private static FileEntry entry(String path, long size) {
        return new FileEntry(Path.of(path), size, WHEN);
    }

    private static WalkEvent found(FileEntry entry) {
        return new WalkEvent.FileFound(entry);
    }

    private static ScanResult scan(Path root, ScanConfig config, WalkEvent... events) throws IOException {
        return scan(new Fakes().resolve(root, root).walk(root, events), List.of(root), config);
    }

    private static ScanResult scan(Fakes fakes, List<Path> locations, ScanConfig config) throws IOException {
        ScanPipeline pipeline = new ScanPipeline(fakes.resolver, fakes.walker, fakes.hasher, fakes.identity);
        return pipeline.scan(locations, config, NO_OP_LISTENER, NEVER_STOP);
    }

    // --- AC-1 ---

    @Test
    @DisplayName("DFS-030.AC-1 Nested and repeated locations are recorded once, as resolved and walked")
    void nestedAndRepeatedLocationsAreRecordedOnce() throws IOException {
        Path deep = Path.of("in/a/b");
        Path parent = Path.of("in/a");
        Fakes fakes = new Fakes()
                .resolve(deep, Path.of("real/a/b"))
                .resolve(parent, Path.of("real/a"));

        ScanResult result = scan(fakes, List.of(deep, parent, parent), config(1, true));

        assertEquals(List.of(Path.of("real/a")), result.roots());
    }

    @Test
    @DisplayName("DFS-030.AC-1 Several separate locations are recorded sorted")
    void separateLocationsAreRecordedSorted() throws IOException {
        Path z = Path.of("root/z");
        Path m = Path.of("root/m");
        Fakes fakes = new Fakes().resolve(z, z).resolve(m, m);

        ScanResult result = scan(fakes, List.of(z, m), config(1, true));

        assertEquals(List.of(m, z), result.roots());
    }

    // --- AC-2 ---

    @Test
    @DisplayName("DFS-030.AC-2 Each folder's direct files are totalled, and a folder with only subfolders has no entry")
    void directFilesAreTotalledPerFolder() throws IOException {
        Path root = Path.of("root");

        ScanResult result = scan(root, config(1, true),
                found(entry("root/p/a.jpg", 100)), found(entry("root/p/b.jpg", 250)),
                found(entry("root/p/q/c.jpg", 40)));

        assertEquals(List.of(
                new FolderTotal(Path.of("root/p"), 2, 350),
                new FolderTotal(Path.of("root/p/q"), 1, 40)), result.folders());
    }

    @Test
    @DisplayName("DFS-030.AC-2 Folders without duplicates are totalled too")
    void foldersWithoutDuplicatesAreTotalled() throws IOException {
        Path root = Path.of("root");

        ScanResult result = scan(root, config(1, true), found(entry("root/u/only.txt", 10)));

        assertEquals(List.of(new FolderTotal(Path.of("root/u"), 1, 10)), result.folders());
        assertEquals(List.of(), result.sets());
    }

    // --- AC-3 ---

    @Test
    @DisplayName("DFS-030.AC-3 Excluded, too small and empty files are not counted")
    void excludedTooSmallAndEmptyFilesAreNotCounted() throws IOException {
        Path root = Path.of("root");

        ScanResult result = scan(root, config(5, true),
                found(entry("root/f/.DS_Store", 6)), found(entry("root/f/._x.jpg", 6)),
                found(entry("root/f/tiny.txt", 3)), found(entry("root/f/empty.txt", 0)),
                found(entry("root/f/kept.txt", 8)));

        assertEquals(List.of(new FolderTotal(Path.of("root/f"), 1, 8)), result.folders());
    }

    @Test
    @DisplayName("DFS-030.AC-3 With default excludes off, excluded names are counted")
    void withDefaultExcludesOffExcludedNamesAreCounted() throws IOException {
        Path root = Path.of("root");

        ScanResult result = scan(root, config(1, false),
                found(entry("root/f/.DS_Store", 6)), found(entry("root/f/kept.txt", 8)));

        assertEquals(List.of(new FolderTotal(Path.of("root/f"), 2, 14)), result.folders());
    }

    @Test
    @DisplayName("DFS-030.AC-3 A path the walk skips is not counted")
    void aSkippedPathIsNotCounted() throws IOException {
        Path root = Path.of("root");
        WalkEvent.Skipped locked = new WalkEvent.Skipped(Path.of("root/g/locked.txt"), SkipReason.UNREADABLE, "denied");

        ScanResult result = scan(root, config(1, true), found(entry("root/g/ok.txt", 5)), locked);

        assertEquals(List.of(new FolderTotal(Path.of("root/g"), 1, 5)), result.folders());
        assertEquals(List.of(locked), result.skipped());
    }

    // --- AC-4 ---

    @Test
    @DisplayName("DFS-030.AC-4 A file that fails hashing stays counted and is also skipped")
    void aFileThatFailsHashingStaysCounted() throws IOException {
        Path root = Path.of("root");
        FileEntry a = entry("root/h/a.bin", 20);
        FileEntry b = entry("root/h/b.bin", 20);
        Fakes fakes = new Fakes().resolve(root, root).walk(root, found(a), found(b));
        fakes.hasher.digests.put(a.path(), "same");
        fakes.hasher.digests.put(b.path(), "same");
        fakes.hasher.fullFailures.put(b.path(), "Access is denied");

        ScanResult result = scan(fakes, List.of(root), config(1, true));

        assertEquals(List.of(new FolderTotal(Path.of("root/h"), 2, 40)), result.folders());
        assertTrue(result.skipped().stream().anyMatch(s -> s.path().equals(b.path())), result.skipped().toString());
    }

    // --- AC-5 ---

    @Test
    @DisplayName("DFS-030.AC-5 Hard links count once per path")
    void hardLinksCountOncePerPath() throws IOException {
        Path root = Path.of("root");
        FileEntry one = entry("root/l/one.dat", 30);
        FileEntry two = entry("root/l/two.dat", 30);
        Fakes fakes = new Fakes().resolve(root, root).walk(root, found(one), found(two));
        fakes.hasher.digests.put(one.path(), "same");
        fakes.hasher.digests.put(two.path(), "same");
        fakes.identity.same = true;

        ScanResult result = scan(fakes, List.of(root), config(1, true));

        assertEquals(List.of(new FolderTotal(Path.of("root/l"), 2, 60)), result.folders());
    }

    // --- fakes ---

    private static final class Fakes {
        final FakeResolver resolver = new FakeResolver();
        final FakeWalker walker = new FakeWalker();
        final FakeHasher hasher = new FakeHasher();
        final FakeIdentity identity = new FakeIdentity();

        Fakes resolve(Path location, Path real) {
            resolver.resolved.put(location, real);
            return this;
        }

        Fakes walk(Path root, WalkEvent... events) {
            walker.events.put(root, List.of(events));
            return this;
        }
    }

    private static final class FakeResolver implements LocationResolver {
        final Map<Path, Path> resolved = new HashMap<>();

        @Override
        public Path realPath(Path location) {
            return resolved.get(location);
        }
    }

    private static final class FakeWalker implements FileTreeWalker {
        final Map<Path, List<WalkEvent>> events = new HashMap<>();

        @Override
        public void walk(Path root, boolean skipSystemFolders, WalkListener listener) {
            for (WalkEvent event : events.getOrDefault(root, List.of())) {
                if (!listener.onEvent(event)) {
                    return;
                }
            }
        }
    }

    private static final class FakeHasher implements ContentHasher {
        final Map<Path, String> digests = new HashMap<>();
        final Map<Path, String> fullFailures = new HashMap<>();

        @Override
        public String sampleHash(Path file, int sampleSize) throws IOException {
            return digest(file);
        }

        @Override
        public String fullHash(Path file) throws IOException {
            String failure = fullFailures.get(file);
            if (failure != null) {
                throw new IOException(failure);
            }
            return digest(file);
        }

        @Override
        public String fullHash(Path file, java.util.function.LongConsumer bytesRead) throws IOException {
            return fullHash(file);
        }

        private String digest(Path file) {
            String digest = digests.get(file);
            if (digest == null) {
                throw new AssertionError("no digest configured for " + file);
            }
            return digest;
        }
    }

    private static final class FakeIdentity implements FileIdentity {
        boolean same;

        @Override
        public Object key(Path path) {
            return same ? "one-inode" : path;
        }
    }
}
