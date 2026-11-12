package us.pochaev.dfs.server.analysis;

import java.util.Objects;

/**
 * One duplicate set as a {@link FolderGroup} lists it (DFS-067, docs/design/folder-groups.md G5).
 *
 * @param digest lowercase hexadecimal SHA-256 of the content; identifies the set
 * @param name   the file name of the set's first path (its {@code files()} are in ascending path order)
 * @param size   size of each file in bytes
 * @param copies the set's physical copies, at least two
 */
public record GroupSet(String digest, String name, long size, int copies) {

    public GroupSet {
        Objects.requireNonNull(digest, "digest");
        Objects.requireNonNull(name, "name");
    }
}
