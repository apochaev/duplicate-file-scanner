package us.pochaev.dfs.scanner.infra.adapter;

import us.pochaev.dfs.scanner.infra.port.LocationResolver;

import java.io.IOException;
import java.nio.file.Path;

/** {@link LocationResolver} on {@link Path#toRealPath}, which follows a link given as the location (DFS-005). */
public final class NioLocationResolver implements LocationResolver {

    @Override
    public Path realPath(Path location) throws IOException {
        throw new UnsupportedOperationException("not implemented: DFS-005");
        // implementer: requireNonNull(location); Path real = location.toRealPath(); if it is not a
        // directory (Files.isDirectory(real)), throw new NotDirectoryException(location.toString()).
    }
}
