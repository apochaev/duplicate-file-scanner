package us.pochaev.dfs.scanner.infra.adapter;

import us.pochaev.dfs.scanner.infra.port.LocationResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.Objects;

/** {@link LocationResolver} on {@link Path#toRealPath}, which follows a link given as the location (DFS-005). */
public final class NioLocationResolver implements LocationResolver {

    @Override
    public Path realPath(Path location) throws IOException {
        Objects.requireNonNull(location, "location");
        Path real = location.toRealPath();
        if (!Files.isDirectory(real)) {
            throw new NotDirectoryException(location.toString());
        }
        return real;
    }
}
