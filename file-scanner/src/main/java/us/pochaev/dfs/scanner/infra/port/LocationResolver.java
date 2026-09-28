package us.pochaev.dfs.scanner.infra.port;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Resolves a scan's start location to its real path (DFS-005): case folded where the file system
 * ignores case, a link or junction given as the location resolved to its target.
 */
public interface LocationResolver {

    /**
     * @return the real path of {@code location}, which is a directory
     * @throws java.nio.file.NoSuchFileException   if {@code location} does not exist
     * @throws java.nio.file.NotDirectoryException if {@code location} is not a directory
     * @throws IOException                         if it cannot be resolved for another reason
     */
    Path realPath(Path location) throws IOException;
}
