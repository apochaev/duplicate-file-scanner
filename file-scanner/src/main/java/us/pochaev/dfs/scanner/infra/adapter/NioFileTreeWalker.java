package us.pochaev.dfs.scanner.infra.adapter;

import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.QuarantineFolder;
import us.pochaev.dfs.scanner.domain.model.SkipReason;
import us.pochaev.dfs.scanner.domain.model.WalkEvent;
import us.pochaev.dfs.scanner.infra.port.FileTreeWalker;
import us.pochaev.dfs.scanner.infra.port.WalkListener;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Walks with {@link Files#walkFileTree}, which reuses the attributes read while listing a directory.
 * <p>
 * {@code isOther()} is what catches Windows junctions and other reparse points: the JDK's own walker
 * descends into a junction (it reports {@code isDirectory}, not {@code isSymbolicLink}), so it must be
 * checked explicitly.
 */
public final class NioFileTreeWalker implements FileTreeWalker {

    /** The detail of the {@code Skipped(dir, QUARANTINE, ...)} event for a pruned quarantine folder (DFS-052). */
    static final String QUARANTINE_DETAIL = "quarantine folder, not scanned";

    @Override
    public void walk(Path root, WalkListener listener) throws IOException {
        Path start = resolveRoot(root);
        Files.walkFileTree(start, new Visitor(start, listener));
    }

    private static Path resolveRoot(Path root) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        Path start = root;
        if (attrs.isSymbolicLink() || attrs.isOther()) {
            start = root.toRealPath();
            attrs = Files.readAttributes(start, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        }
        if (!attrs.isDirectory()) {
            throw new NotDirectoryException(root.toString());
        }
        return start;
    }

    private static final class Visitor extends SimpleFileVisitor<Path> {

        private final Path root;
        private final WalkListener listener;

        Visitor(Path root, WalkListener listener) {
            this.root = root;
            this.listener = listener;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (!dir.equals(root) && EntryKind.of(attrs) == EntryKind.SPECIAL_DIRECTORY) {
                EntryKind kind = EntryKind.SPECIAL_DIRECTORY;
                return emit(skipped(dir, kind.reason(), kind.detail()), FileVisitResult.SKIP_SUBTREE);
            }
            if (!dir.equals(root) && isQuarantine(dir)) {
                return emit(skipped(dir, SkipReason.QUARANTINE, QUARANTINE_DETAIL), FileVisitResult.SKIP_SUBTREE);
            }
            return emit(new WalkEvent.DirectoryEntered(dir), FileVisitResult.CONTINUE);
        }

        /**
         * Whether the plain directory {@code dir} is a quarantine folder: its file name equals
         * {@link QuarantineFolder#NAME}, exactly and case-sensitively, decided from the name alone (DFS-052 decision 2).
         */
        private static boolean isQuarantine(Path dir) {
            throw new UnsupportedOperationException("not implemented: DFS-052");
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            EntryKind kind = EntryKind.of(attrs);
            if (kind == EntryKind.SYMLINK) {
                return emit(skipped(file, kind.reason(), kind.detail()), FileVisitResult.CONTINUE);
            }
            if (kind == EntryKind.REGULAR_FILE) {
                FileEntry entry = new FileEntry(file, attrs.size(), attrs.lastModifiedTime().toInstant());
                return emit(new WalkEvent.FileFound(entry), FileVisitResult.CONTINUE);
            }
            return emit(skipped(file, EntryKind.SPECIAL_FILE.reason(), EntryKind.SPECIAL_FILE.detail()), FileVisitResult.CONTINUE);
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
            return emit(skipped(file, SkipReason.UNREADABLE, exc.toString()), FileVisitResult.CONTINUE);
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
            if (exc != null) {
                return emit(skipped(dir, SkipReason.UNREADABLE, exc.toString()), FileVisitResult.CONTINUE);
            }
            return FileVisitResult.CONTINUE;
        }

        private FileVisitResult emit(WalkEvent event, FileVisitResult whenContinuing) {
            return listener.onEvent(event) ? whenContinuing : FileVisitResult.TERMINATE;
        }

        private static WalkEvent skipped(Path path, SkipReason reason, String detail) {
            return new WalkEvent.Skipped(path, reason, detail);
        }
    }
}
