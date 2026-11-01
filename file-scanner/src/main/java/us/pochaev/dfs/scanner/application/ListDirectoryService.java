package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.DirectoryListing;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.usecases.query.ListDirectory;
import us.pochaev.dfs.scanner.infra.port.DirectoryReader;

import java.nio.file.Path;
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
        throw new UnsupportedOperationException("not implemented: DFS-032");
    }
}
