package us.pochaev.dfs.scanner.infra.port;

import java.io.IOException;
import java.nio.file.Path;

public interface FileTreeWalker {

    /**
     * Walks {@code root} depth-first and reports every regular file and every path that was left out.
     * <p>
     * Links are never followed: symbolic links, junctions and other special entries below
     * {@code root} are reported as skipped. Problems below {@code root} are reported as skipped
     * too, and the walk continues. If {@code root} itself is a link it is resolved, so the
     * folder it points to is walked.
     *
     * @throws java.nio.file.NoSuchFileException  if {@code root} does not exist
     * @throws java.nio.file.NotDirectoryException if {@code root} is not a directory
     * @throws IOException                          if {@code root} cannot be read
     */
    void walk(Path root, WalkListener listener) throws IOException;
}
