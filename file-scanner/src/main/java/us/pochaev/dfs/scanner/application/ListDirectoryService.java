package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.DirectoryEntry;
import us.pochaev.dfs.scanner.domain.model.DirectoryListing;
import us.pochaev.dfs.scanner.domain.model.ErrorCode;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.usecases.query.ListDirectory;
import us.pochaev.dfs.scanner.infra.port.DirectoryReader;
import us.pochaev.dfs.scanner.infra.port.LinkNotFollowedException;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Lists one folder through a {@link DirectoryReader} (DFS-032): maps the reader's exceptions to {@code Failure}
 * values and orders the entries by name. Its only field is the port, so concurrent calls share nothing.
 */
public final class ListDirectoryService implements ListDirectory {

    private final DirectoryReader reader;

    public ListDirectoryService(DirectoryReader reader) {
        this.reader = Objects.requireNonNull(reader, "reader");
    }

    @Override
    public Result<DirectoryListing> list(Path folder) {
        Objects.requireNonNull(folder, "folder");
        List<DirectoryEntry> entries;
        try {
            entries = reader.read(folder);
        } catch (NoSuchFileException e) {
            return new Result.Failure<>(ErrorCode.LOCATION_NOT_FOUND, "folder not found: " + folder);
        } catch (NotDirectoryException e) {
            return new Result.Failure<>(ErrorCode.NOT_A_DIRECTORY, "not a directory: " + folder);
        } catch (LinkNotFollowedException e) {
            return new Result.Failure<>(ErrorCode.LOCATION_IS_LINK, "folder is a link, not followed: " + folder);
        } catch (IOException e) {
            String message = e.getMessage();
            return new Result.Failure<>(ErrorCode.LOCATION_UNREADABLE,
                    message == null || message.isBlank() ? e.toString() : message);
        }
        List<DirectoryEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(e -> folder.getFileSystem().getPath(e.name())));
        return new Result.Success<>(new DirectoryListing(folder, sorted));
    }
}
