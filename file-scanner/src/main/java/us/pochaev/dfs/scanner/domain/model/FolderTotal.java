package us.pochaev.dfs.scanner.domain.model;

import java.nio.file.Path;

/**
 * How many counted files one folder directly holds, and their summed size (DFS-030 decision 2).
 * Direct counts only: files in subfolders belong to those subfolders' own totals. A counted file is
 * one the walk kept: a regular file of at least {@link ScanConfig#minSize()} that the default
 * excludes did not leave out. Totals count paths as walked, so two hard links to one file in the same
 * folder count as two files and twice the bytes.
 *
 * @param folder the folder, as the walk reported it (the parent of each counted file's path)
 * @param files  how many counted files it directly holds; at least 1, since a folder without one
 *               has no entry
 * @param bytes  the counted files' summed size in bytes; never negative
 */
public record FolderTotal(Path folder, long files, long bytes) {

    /**
     * @throws NullPointerException     if {@code folder} is null
     * @throws IllegalArgumentException if {@code files} is below 1 or {@code bytes} is negative
     */
    public FolderTotal {
        throw new UnsupportedOperationException("not implemented: DFS-030");
        // implementer: requireNonNull(folder, "folder"); reject files < 1 and bytes < 0 with
        // IllegalArgumentException (DFS-030.AC-6). Deliberately not in the stub, so the
        // validation tests fail red on this throw instead of passing (L-013).
    }
}
