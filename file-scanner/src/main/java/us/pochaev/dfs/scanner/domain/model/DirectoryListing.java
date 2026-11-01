package us.pochaev.dfs.scanner.domain.model;

import java.nio.file.Path;
import java.util.List;

/**
 * The direct children of one folder, as {@code ListDirectory} read them from disk (DFS-032 decision 2).
 *
 * @param folder  the folder as the caller passed it
 * @param entries one entry per direct child, ordered by {@link Path#compareTo} of their names, kinds mixed
 */
public record DirectoryListing(Path folder, List<DirectoryEntry> entries) {

    public DirectoryListing {
    }
}
