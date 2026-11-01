package us.pochaev.dfs.scanner.infra.adapter;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * The two file-system calls {@link NioDirectoryReader} makes, behind a package-private seam so a test can make a
 * child's attributes or the folder itself unreadable without platform tricks (DFS-032, AC-5 and AC-7).
 */
interface DirectoryAccess {

    /** The real file system: {@code Files.newDirectoryStream} and {@code Files.readAttributes(..., NOFOLLOW_LINKS)}. */
    DirectoryAccess NIO = new DirectoryAccess() {
        @Override
        public DirectoryStream<Path> open(Path folder) throws IOException {
            throw new UnsupportedOperationException("not implemented: DFS-032");
        }

        @Override
        public BasicFileAttributes attributes(Path path) throws IOException {
            throw new UnsupportedOperationException("not implemented: DFS-032");
        }
    };

    /** Opens {@code folder} for listing its direct children. */
    DirectoryStream<Path> open(Path folder) throws IOException;

    /** Reads {@code path}'s basic attributes without following a link. */
    BasicFileAttributes attributes(Path path) throws IOException;
}
