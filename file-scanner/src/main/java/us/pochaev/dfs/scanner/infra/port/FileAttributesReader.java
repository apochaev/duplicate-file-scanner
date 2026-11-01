package us.pochaev.dfs.scanner.infra.port;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Reads one path's basic attributes without following links (DFS-033 decision 6): a symlink or junction is
 * described as itself, never as its target, and no content is read.
 */
public interface FileAttributesReader {

    /**
     * @return {@code path}'s attributes, read without following a link
     * @throws java.nio.file.NoSuchFileException if {@code path} does not exist
     * @throws IOException                       if the attributes cannot be read for another reason
     */
    BasicFileAttributes read(Path path) throws IOException;
}
