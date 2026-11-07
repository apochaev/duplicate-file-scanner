package us.pochaev.dfs.scanner.infra.adapter;

import us.pochaev.dfs.scanner.domain.model.PlanExecutionId;
import us.pochaev.dfs.scanner.domain.model.QuarantineFolder;
import us.pochaev.dfs.scanner.infra.port.FileDeleter;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * {@link FileDeleter} that moves a file into {@code <base>/.dfs-quarantine/<run ID>/<path below base>} with
 * {@code Files.move(file, target, ATOMIC_MOVE)} (DFS-051 decision 5): {@code base} is the deepest quarantine anchor the
 * file lies below, else the root of its volume (the highest ancestor whose {@code FileStore} is the file's own).
 * Stateless apart from the anchors; creates nothing until {@link #move}.
 */
public final class NioQuarantineDeleter implements FileDeleter {

    /** The quarantine folder's name below a volume root or an anchor; the shared constant since DFS-052. */
    public static final String QUARANTINE_FOLDER = QuarantineFolder.NAME;

    private final List<Path> anchors;

    /**
     * @param anchors absolute folders that stand in for a volume root ({@code ExecutionConfig.quarantineAnchors})
     * @throws NullPointerException if {@code anchors} is or holds null
     */
    public NioQuarantineDeleter(List<Path> anchors) {
        this.anchors = List.copyOf(anchors);
    }

    @Override
    public Path quarantinePath(Path file, PlanExecutionId run) throws IOException {
        Path absolute = file.toAbsolutePath().normalize();
        Path base = null;
        for (Path anchor : anchors) {
            Path a = anchor.toAbsolutePath().normalize();
            if (absolute.startsWith(a) && (base == null || a.getNameCount() > base.getNameCount())) {
                base = a;
            }
        }
        if (base == null) {
            base = volumeRoot(absolute);
        }
        return base.resolve(QUARANTINE_FOLDER).resolve(run.value().toString()).resolve(base.relativize(absolute));
    }

    private static Path volumeRoot(Path file) throws IOException {
        Path ancestor = file.getParent();
        while (ancestor != null && !Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) {
            ancestor = ancestor.getParent();
        }
        if (ancestor == null) {
            throw new IOException("no existing folder above " + file);
        }
        FileStore store = Files.getFileStore(ancestor);
        Path parent = ancestor.getParent();
        while (parent != null && store.equals(Files.getFileStore(parent))) {
            ancestor = parent;
            parent = ancestor.getParent();
        }
        return ancestor;
    }

    @Override
    public MoveResult move(Path file, Path quarantinePath) {
        try {
            Files.createDirectories(quarantinePath.getParent());
        } catch (IOException | RuntimeException e) {
            return new MoveResult.QuarantineNotWritable(describe(e));
        }
        try {
            if (Files.exists(quarantinePath, LinkOption.NOFOLLOW_LINKS)) {
                return new MoveResult.Failed("quarantine path exists: " + quarantinePath);
            }
            Files.move(file, quarantinePath, StandardCopyOption.ATOMIC_MOVE);
            return new MoveResult.Moved();
        } catch (IOException | RuntimeException e) {
            return new MoveResult.Failed(describe(e));
        }
    }

    private static String describe(Exception e) {
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.toString() : m;
    }
}
