package us.pochaev.dfs.scanner.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import us.pochaev.dfs.scanner.domain.model.DirectoryEntry;
import us.pochaev.dfs.scanner.domain.model.DirectoryEntry.FileItem;
import us.pochaev.dfs.scanner.domain.model.DirectoryEntry.FolderItem;
import us.pochaev.dfs.scanner.domain.model.DirectoryListing;
import us.pochaev.dfs.scanner.domain.model.ErrorCode;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.infra.port.DirectoryReader;
import us.pochaev.dfs.scanner.infra.port.LinkNotFollowedException;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ListDirectoryService against a fake DirectoryReader (DFS-032). */
class ListDirectoryServiceTest {

    private static final Path FOLDER = Path.of("folder");

    private static ListDirectoryService serviceThrowing(IOException e) {
        return new ListDirectoryService(folder -> {
            throw e;
        });
    }

    @Test
    @DisplayName("DFS-032.AC-6 Entries the reader returns out of order are sorted by name, kinds mixed")
    void sortsReaderOutput() {
        Instant t = Instant.EPOCH;
        DirectoryReader reader = folder -> List.of(
                new FileItem("d.txt", folder.resolve("d.txt"), 1, t),
                new FolderItem("c", folder.resolve("c"), t),
                new FileItem("b.txt", folder.resolve("b.txt"), 1, t),
                new FolderItem("a", folder.resolve("a"), t));

        Result<DirectoryListing> result = new ListDirectoryService(reader).list(FOLDER);

        DirectoryListing listing = ((Result.Success<DirectoryListing>)
                assertInstanceOf(Result.Success.class, result)).value();
        assertEquals(List.of("a", "b.txt", "c", "d.txt"),
                listing.entries().stream().map(DirectoryEntry::name).toList());
        assertEquals(FOLDER, listing.folder());
    }

    @Test
    @DisplayName("DFS-032.AC-7 The reader's failures map to the four error codes")
    void mapsReaderFailures() {
        assertEquals(ErrorCode.LOCATION_NOT_FOUND, code(serviceThrowing(new NoSuchFileException("folder"))));
        assertEquals(ErrorCode.NOT_A_DIRECTORY, code(serviceThrowing(new NotDirectoryException("folder"))));
        assertEquals(ErrorCode.LOCATION_IS_LINK, code(serviceThrowing(new LinkNotFollowedException("folder"))));
        assertEquals(ErrorCode.LOCATION_UNREADABLE, code(serviceThrowing(new IOException("disk on fire"))));
    }

    @Test
    @DisplayName("DFS-032.AC-7 LOCATION_UNREADABLE carries the exception's message")
    void unreadableCarriesMessage() {
        Result<DirectoryListing> result = serviceThrowing(new IOException("disk on fire")).list(FOLDER);

        Result.Failure<DirectoryListing> failure =
                (Result.Failure<DirectoryListing>) assertInstanceOf(Result.Failure.class, result);
        assertEquals("disk on fire", failure.message());
    }

    @Test
    @DisplayName("DFS-032.AC-7 A not-found failure names the folder")
    void notFoundNamesFolder() {
        Result.Failure<DirectoryListing> failure = (Result.Failure<DirectoryListing>) assertInstanceOf(
                Result.Failure.class, serviceThrowing(new NoSuchFileException("folder")).list(FOLDER));
        assertTrue(failure.message().contains("folder"), failure.message());
    }

    private static ErrorCode code(ListDirectoryService service) {
        Result<DirectoryListing> result = service.list(FOLDER);
        return ((Result.Failure<DirectoryListing>) assertInstanceOf(Result.Failure.class, result)).code();
    }
}
