package us.pochaev.dfs.scanner.domain.usecases.query;

import us.pochaev.dfs.scanner.domain.model.DirectoryListing;
import us.pochaev.dfs.scanner.domain.model.Result;

import java.nio.file.Path;

/**
 * Lists the direct children of one folder as the disk shows them (DFS-032), for the UI's folder detail view. A
 * read-only query, one level deep, that follows the scan's rules: regular files and plain directories are listed,
 * symlinks, junctions and other special entries are reported as skipped and never followed, and an unreadable child
 * is reported as skipped without failing the listing. It takes no scan: matching entries against a scan is a
 * follow-up (decision 1).
 */
public interface ListDirectory {

    /**
     * @param folder the folder to list; its children's paths are {@code folder.resolve(name)}, so they keep the form
     *               the caller passed in
     * @return {@code Success} holding one entry per direct child, ordered by {@link Path#compareTo} of their names; or
     *         {@code Failure} with {@code LOCATION_NOT_FOUND} (no such folder), {@code NOT_A_DIRECTORY} (a regular or
     *         other non-directory file), {@code LOCATION_IS_LINK} (a symlink, junction or other reparse point) or
     *         {@code LOCATION_UNREADABLE} (cannot be opened or read to the end; the message is the exception's)
     * @throws NullPointerException if {@code folder} is null
     */
    Result<DirectoryListing> list(Path folder);
}
