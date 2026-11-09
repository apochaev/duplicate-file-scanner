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
     * folder it points to is walked. A plain directory named {@code .dfs-quarantine} below
     * {@code root} is never entered and is reported as skipped with {@code QUARANTINE}, whatever
     * {@code skipSystemFolders} says (DFS-052).
     * <p>
     * With {@code skipSystemFolders}, a plain directory below {@code root} whose name is one of the
     * operating system's own folders (DFS-061 decision 1) is not entered and is reported once as
     * skipped with {@code SYSTEM_FOLDER}; {@code root} itself and its parents are never tested
     * (DFS-061 decision 4). Without it, such folders are walked like any other.
     *
     * @param skipSystemFolders whether to leave the operating-system folders out; the scan passes
     *                          {@code ScanConfig.defaultExcludes()} (DFS-061 decision 3)
     * @throws java.nio.file.NoSuchFileException  if {@code root} does not exist
     * @throws java.nio.file.NotDirectoryException if {@code root} is not a directory
     * @throws IOException                          if {@code root} cannot be read
     */
    void walk(Path root, boolean skipSystemFolders, WalkListener listener) throws IOException;

    /**
     * Walks {@code root} with the default setting: operating-system folders skipped, as with
     * {@code ScanConfig.defaults()}. Same as {@code walk(root, true, listener)}.
     *
     * @throws java.nio.file.NoSuchFileException  if {@code root} does not exist
     * @throws java.nio.file.NotDirectoryException if {@code root} is not a directory
     * @throws IOException                          if {@code root} cannot be read
     */
    default void walk(Path root, WalkListener listener) throws IOException {
        walk(root, true, listener);
    }
}
