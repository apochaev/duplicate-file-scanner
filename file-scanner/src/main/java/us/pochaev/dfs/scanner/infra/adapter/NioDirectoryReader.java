package us.pochaev.dfs.scanner.infra.adapter;

import us.pochaev.dfs.scanner.domain.model.DirectoryEntry;
import us.pochaev.dfs.scanner.domain.model.SkipReason;
import us.pochaev.dfs.scanner.infra.port.DirectoryReader;
import us.pochaev.dfs.scanner.infra.port.LinkNotFollowedException;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link DirectoryReader} on {@code Files.newDirectoryStream}, with every attribute read without following links
 * (DFS-032 decision 5), classified by {@link EntryKind} like {@link NioFileTreeWalker}.
 */
public final class NioDirectoryReader implements DirectoryReader {

    private final DirectoryAccess access;

    /** Reads the real file system. */
    public NioDirectoryReader() {
        this(DirectoryAccess.NIO);
    }

    /** The test seam: {@code access} stands in for the two file-system calls. */
    NioDirectoryReader(DirectoryAccess access) {
        this.access = access;
    }

    @Override
    public List<DirectoryEntry> read(Path folder) throws IOException {
        EntryKind folderKind = EntryKind.of(access.attributes(folder));
        switch (folderKind) {
            case SYMLINK, SPECIAL_DIRECTORY -> throw new LinkNotFollowedException(folder.toString());
            case REGULAR_FILE, SPECIAL_FILE -> throw new NotDirectoryException(folder.toString());
            case DIRECTORY -> { }
        }
        List<DirectoryEntry> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = access.open(folder)) {
            for (Path child : stream) {
                String name = child.getFileName().toString();
                Path path = folder.resolve(name);
                BasicFileAttributes attrs;
                try {
                    attrs = access.attributes(child);
                } catch (IOException e) {
                    entries.add(new DirectoryEntry.SkippedItem(name, path, SkipReason.UNREADABLE, describe(e)));
                    continue;
                }
                EntryKind kind = EntryKind.of(attrs);
                switch (kind) {
                    case REGULAR_FILE -> entries.add(new DirectoryEntry.FileItem(name, path, attrs.size(),
                            attrs.lastModifiedTime().toInstant()));
                    case DIRECTORY -> entries.add(new DirectoryEntry.FolderItem(name, path,
                            attrs.lastModifiedTime().toInstant()));
                    default -> entries.add(new DirectoryEntry.SkippedItem(name, path, kind.reason(), kind.detail()));
                }
            }
        } catch (DirectoryIteratorException e) {
            throw e.getCause();
        }
        return entries;
    }

    private static String describe(IOException e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.toString() : message;
    }
}
