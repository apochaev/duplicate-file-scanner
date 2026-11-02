package us.pochaev.dfs.scanner.infra.adapter;

import us.pochaev.dfs.scanner.domain.model.PlanExecutionId;
import us.pochaev.dfs.scanner.infra.port.FileDeleter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * {@link FileDeleter} that moves a file into {@code <base>/.dfs-quarantine/<run ID>/<path below base>} with
 * {@code Files.move(file, target, ATOMIC_MOVE)} (DFS-051 decision 5): {@code base} is the deepest quarantine anchor the
 * file lies below, else the root of its volume (the highest ancestor whose {@code FileStore} is the file's own).
 * Stateless apart from the anchors; creates nothing until {@link #move}.
 */
public final class NioQuarantineDeleter implements FileDeleter {

    /** The quarantine folder's name below a volume root or an anchor. */
    public static final String QUARANTINE_FOLDER = ".dfs-quarantine";

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
        throw new UnsupportedOperationException("not implemented: DFS-051");
    }

    @Override
    public MoveResult move(Path file, Path quarantinePath) {
        throw new UnsupportedOperationException("not implemented: DFS-051");
    }
}
