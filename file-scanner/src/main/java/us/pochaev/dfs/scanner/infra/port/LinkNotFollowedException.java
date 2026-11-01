package us.pochaev.dfs.scanner.infra.port;

import java.nio.file.FileSystemException;

/**
 * A folder given to {@link DirectoryReader#read} is a symlink, junction or other reparse point, which is never
 * followed (DFS-032 decision 4). The JDK has no exception for this case.
 */
public final class LinkNotFollowedException extends FileSystemException {

    /** @param file the folder as given */
    public LinkNotFollowedException(String file) {
        super(file, null, "a link or reparse point, not followed");
    }
}
