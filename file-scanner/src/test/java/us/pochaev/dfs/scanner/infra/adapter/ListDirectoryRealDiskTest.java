package us.pochaev.dfs.scanner.infra.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.pochaev.dfs.scanner.application.ListDirectoryService;
import us.pochaev.dfs.scanner.domain.factory.ScannerFactories;
import us.pochaev.dfs.scanner.domain.model.DirectoryEntry;
import us.pochaev.dfs.scanner.domain.model.DirectoryEntry.FileItem;
import us.pochaev.dfs.scanner.domain.model.DirectoryEntry.FolderItem;
import us.pochaev.dfs.scanner.domain.model.DirectoryEntry.SkippedItem;
import us.pochaev.dfs.scanner.domain.model.DirectoryListing;
import us.pochaev.dfs.scanner.domain.model.ErrorCode;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.SkipReason;
import us.pochaev.dfs.scanner.domain.usecases.query.ListDirectory;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** ListDirectory over the real disk (and the package-private DirectoryAccess seam), DFS-032. */
class ListDirectoryRealDiskTest {

    @TempDir
    Path tmp;

    private final ListDirectory service = new ListDirectoryService(new NioDirectoryReader());

    private static DirectoryListing ok(Result<DirectoryListing> result) {
        return ((Result.Success<DirectoryListing>) assertInstanceOf(Result.Success.class, result)).value();
    }

    private static Result.Failure<DirectoryListing> failure(Result<DirectoryListing> result) {
        return (Result.Failure<DirectoryListing>) assertInstanceOf(Result.Failure.class, result);
    }

    private static List<String> names(DirectoryListing listing) {
        return listing.entries().stream().map(DirectoryEntry::name).toList();
    }

    private Path file(String name, int size) throws IOException {
        return Files.write(tmp.resolve(name), new byte[size]);
    }

    @Test
    @DisplayName("DFS-032.AC-1, DFS-032.AC-2, DFS-032.AC-3 Files and subfolders of one level are listed, not their contents")
    void listsOneLevel() throws IOException {
        Path a = file("a.txt", 3);
        file("b.jpg", 5);
        Path sub = Files.createDirectory(tmp.resolve("sub"));
        Files.write(sub.resolve("c.txt"), new byte[1]);

        DirectoryListing listing = ok(service.list(tmp));

        assertEquals(tmp, listing.folder());
        assertEquals(List.of("a.txt", "b.jpg", "sub"), names(listing));
        FileItem fa = assertInstanceOf(FileItem.class, listing.entries().get(0));
        assertEquals(tmp.resolve("a.txt"), fa.path());
        assertEquals(3, fa.size());
        assertEquals(Files.getLastModifiedTime(a).toInstant(), fa.lastModified());
        assertEquals(5, assertInstanceOf(FileItem.class, listing.entries().get(1)).size());
        FolderItem fs = assertInstanceOf(FolderItem.class, listing.entries().get(2));
        assertEquals(tmp.resolve("sub"), fs.path());
        assertEquals(Files.getLastModifiedTime(sub).toInstant(), fs.lastModified());
        assertFalse(names(listing).contains("c.txt"));
    }

    @Test
    @DisplayName("DFS-032.AC-2 Files the scan leaves out are still listed")
    void listsFilesTheScanLeavesOut() throws IOException {
        file("empty.txt", 0);
        file(".DS_Store", 2);
        file("._x.jpg", 2);

        DirectoryListing listing = ok(service.list(tmp));

        assertEquals(3, listing.entries().size());
        for (DirectoryEntry entry : listing.entries()) {
            assertInstanceOf(FileItem.class, entry);
        }
        FileItem empty = (FileItem) listing.entries().stream()
                .filter(e -> e.name().equals("empty.txt")).findFirst().orElseThrow();
        assertEquals(0, empty.size());
        assertTrue(names(listing).containsAll(List.of(".DS_Store", "._x.jpg")));
    }

    @Test
    @DisplayName("DFS-032.AC-1 An empty folder gives an empty listing")
    void emptyFolder() {
        DirectoryListing listing = ok(service.list(tmp));
        assertEquals(List.of(), listing.entries());
    }

    @Test
    @DisplayName("DFS-032.AC-4 A symlink child is reported, not followed")
    void symlinkChild() throws IOException {
        Path other = Files.createDirectory(tmp.resolve("other"));
        Files.write(other.resolve("secret.txt"), new byte[1]);
        Path scanned = Files.createDirectory(tmp.resolve("T"));
        Files.write(scanned.resolve("a.txt"), new byte[1]);
        Fixtures.createSymlinkOrSkip(scanned.resolve("link"), other);

        DirectoryListing listing = ok(service.list(scanned));

        assertEquals(List.of("a.txt", "link"), names(listing));
        SkippedItem link = assertInstanceOf(SkippedItem.class, listing.entries().get(1));
        assertEquals(SkipReason.SYMLINK, link.reason());
        assertEquals(scanned.resolve("link"), link.path());
        assertFalse(names(listing).contains("secret.txt"));
    }

    @Test
    @DisplayName("DFS-032.AC-4 A junction child is reported, not followed")
    void junctionChild() throws IOException {
        assumeTrue(Fixtures.isWindows(), "junctions exist on Windows only");
        Path other = Files.createDirectory(tmp.resolve("other"));
        Files.write(other.resolve("secret.txt"), new byte[1]);
        Path scanned = Files.createDirectory(tmp.resolve("T"));
        Fixtures.createJunction(scanned.resolve("j"), other);

        DirectoryListing listing = ok(service.list(scanned));

        assertEquals(List.of("j"), names(listing));
        SkippedItem j = assertInstanceOf(SkippedItem.class, listing.entries().get(0));
        assertEquals(SkipReason.SPECIAL_FILE, j.reason());
        assertFalse(names(listing).contains("secret.txt"));
    }

    /** A DirectoryAccess that is real NIO except for the failures a test injects. */
    private static class FakeAccess implements DirectoryAccess {
        private final String badChild;
        private final IOException openFailure;

        FakeAccess(String badChild, IOException openFailure) {
            this.badChild = badChild;
            this.openFailure = openFailure;
        }

        @Override
        public DirectoryStream<Path> open(Path folder) throws IOException {
            if (openFailure != null) {
                throw openFailure;
            }
            return Files.newDirectoryStream(folder);
        }

        @Override
        public BasicFileAttributes attributes(Path path) throws IOException {
            if (path.getFileName().toString().equals(badChild)) {
                throw new AccessDeniedException(path.toString(), null, "denied");
            }
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        }
    }

    @Test
    @DisplayName("DFS-032.AC-5 A child that cannot be read does not fail the listing")
    void unreadableChild() throws IOException {
        file("a.txt", 4);
        file("bad.txt", 1);

        ListDirectory listing = new ListDirectoryService(new NioDirectoryReader(new FakeAccess("bad.txt", null)));
        DirectoryListing result = ok(listing.list(tmp));

        assertEquals(List.of("a.txt", "bad.txt"), names(result));
        assertEquals(4, assertInstanceOf(FileItem.class, result.entries().get(0)).size());
        SkippedItem bad = assertInstanceOf(SkippedItem.class, result.entries().get(1));
        assertEquals(SkipReason.UNREADABLE, bad.reason());
        assertFalse(bad.detail().isBlank());
    }

    @Test
    @DisplayName("DFS-032.AC-6 Entries are ordered by name, kinds mixed")
    void orderedByName() throws IOException {
        file("d.txt", 1);
        file("b.txt", 1);
        Files.createDirectory(tmp.resolve("c"));
        Files.createDirectory(tmp.resolve("a"));

        assertEquals(List.of("a", "b.txt", "c", "d.txt"), names(ok(service.list(tmp))));
    }

    @Test
    @DisplayName("DFS-032.AC-7 A missing folder or a file is refused")
    void missingAndFile() throws IOException {
        file("f.txt", 1);

        Result.Failure<DirectoryListing> missing = failure(service.list(tmp.resolve("missing")));
        assertEquals(ErrorCode.LOCATION_NOT_FOUND, missing.code());
        assertTrue(missing.message().contains("missing"), missing.message());
        Result.Failure<DirectoryListing> notDir = failure(service.list(tmp.resolve("f.txt")));
        assertEquals(ErrorCode.NOT_A_DIRECTORY, notDir.code());
        assertTrue(notDir.message().contains("f.txt"), notDir.message());
    }

    @Test
    @DisplayName("DFS-032.AC-7 A link given as the folder is refused")
    void linkAsFolder() throws IOException {
        Path target = Files.createDirectory(tmp.resolve("T"));
        Files.write(target.resolve("a.txt"), new byte[1]);
        Fixtures.createSymlinkOrSkip(tmp.resolve("L"), target);

        Result.Failure<DirectoryListing> failure = failure(service.list(tmp.resolve("L")));
        assertEquals(ErrorCode.LOCATION_IS_LINK, failure.code());
        assertTrue(failure.message().contains("L"), failure.message());
    }

    @Test
    @DisplayName("DFS-032.AC-7 A junction given as the folder is refused")
    void junctionAsFolder() throws IOException {
        assumeTrue(Fixtures.isWindows(), "junctions exist on Windows only");
        Path target = Files.createDirectory(tmp.resolve("T"));
        Fixtures.createJunction(tmp.resolve("J"), target);

        assertEquals(ErrorCode.LOCATION_IS_LINK, failure(service.list(tmp.resolve("J"))).code());
    }

    @Test
    @DisplayName("DFS-032.AC-7 A folder that cannot be opened is refused with the exception's message")
    void unopenableFolder() {
        IOException boom = new AccessDeniedException("x", null, "cannot open this folder");
        ListDirectory listing = new ListDirectoryService(new NioDirectoryReader(new FakeAccess(null, boom)));

        Result.Failure<DirectoryListing> failure = failure(listing.list(tmp));

        assertEquals(ErrorCode.LOCATION_UNREADABLE, failure.code());
        assertEquals(boom.getMessage(), failure.message());
    }

    @Test
    @DisplayName("DFS-032.AC-7 A null folder is a caller bug")
    void nullFolder() {
        try {
            service.list(null);
            fail("expected NullPointerException");
        } catch (NullPointerException expected) {
            // expected
        }
    }

    private static Map<String, String> snapshot(Path folder) throws IOException {
        Map<String, String> map = new TreeMap<>();
        try (Stream<Path> children = Files.list(folder)) {
            for (Path child : (Iterable<Path>) children::iterator) {
                FileTime mtime = Files.getLastModifiedTime(child);
                long size = Files.isDirectory(child) ? -1 : Files.size(child);
                map.put(child.getFileName().toString(), size + "@" + mtime.toInstant());
            }
        }
        return map;
    }

    @Test
    @DisplayName("DFS-032.AC-8 Listing changes nothing")
    void listingChangesNothing() throws IOException {
        file("a.txt", 3);
        file("b.jpg", 5);
        Files.createDirectory(tmp.resolve("sub"));
        Files.setLastModifiedTime(tmp.resolve("a.txt"), FileTime.from(Instant.parse("2020-01-02T03:04:05Z")));
        Map<String, String> before = snapshot(tmp);

        ok(service.list(tmp));

        assertEquals(before, snapshot(tmp));
        assertEquals(3, before.size());
    }

    @Test
    @DisplayName("DFS-032.AC-9 ScannerFactories.listDirectory lists a folder")
    void factoryLists() throws IOException {
        file("a.txt", 2);

        DirectoryListing listing = ok(ScannerFactories.listDirectory().list(tmp));

        assertEquals(List.of("a.txt"), names(listing));
    }
}
