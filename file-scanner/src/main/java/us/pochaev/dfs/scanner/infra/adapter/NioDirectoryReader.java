package us.pochaev.dfs.scanner.infra.adapter;

import us.pochaev.dfs.scanner.domain.model.DirectoryEntry;
import us.pochaev.dfs.scanner.infra.port.DirectoryReader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * {@link DirectoryReader} on {@code Files.newDirectoryStream}, with every attribute read without following links
 * (DFS-032 decision 5), classified by {@link EntryKind} like {@link NioFileTreeWalker}.
 */
public final class NioDirectoryReader implements DirectoryReader {

    private final DirectoryAccess access;

    /** Reads the real file system. */
    public NioDirectoryReader() {
        this(DirectoryAccess.NIO);
    }

    /** The test seam: {@code access} stands in for the two file-system calls. */
    NioDirectoryReader(DirectoryAccess access) {
        this.access = access;
    }

    @Override
    public List<DirectoryEntry> read(Path folder) throws IOException {
        throw new UnsupportedOperationException("not implemented: DFS-032");
    }
}
