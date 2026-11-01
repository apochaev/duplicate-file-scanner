package us.pochaev.dfs.scanner.infra.adapter;

import us.pochaev.dfs.scanner.infra.port.FileAttributesReader;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * {@link FileAttributesReader} on {@code Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW_LINKS)}
 * (DFS-033 decision 6). Stateless.
 */
public final class NioFileAttributesReader implements FileAttributesReader {

    @Override
    public BasicFileAttributes read(Path path) throws IOException {
        return java.nio.file.Files.readAttributes(path, BasicFileAttributes.class, java.nio.file.LinkOption.NOFOLLOW_LINKS);
    }
}
