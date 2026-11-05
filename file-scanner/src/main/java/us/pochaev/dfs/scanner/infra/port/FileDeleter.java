package us.pochaev.dfs.scanner.infra.port;

import us.pochaev.dfs.scanner.domain.model.PlanExecutionId;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Takes one file out of its folder (CLAUDE.md decision 2, DFS-051 decision 5). The first adapter moves it into a
 * quarantine folder on its own volume; a permanent-delete adapter behind the same port is a later ticket. Never
 * follows a link: a symlink or junction given as {@code file} is moved as itself, never through. Implementations must
 * be safe to call from several threads at once and must not create anything before {@link #move} is called.
 */
public interface FileDeleter {

    /**
     * Names where {@link #move} would put {@code file} for run {@code run}:
     * {@code <base>/.dfs-quarantine/<run ID>/<file's path below base>}, where {@code base} is the deepest configured
     * quarantine anchor {@code file} lies below, else the root of {@code file}'s volume. Creates nothing.
     *
     * @throws IOException if {@code file}'s volume cannot be determined (for example its drive is gone)
     */
    Path quarantinePath(Path file, PlanExecutionId run) throws IOException;

    /**
     * Creates {@code quarantinePath}'s parent folders if absent, then renames {@code file} to {@code quarantinePath}
     * atomically ({@code ATOMIC_MOVE}), never replacing an existing file.
     *
     * @return {@link MoveResult.Moved}; {@link MoveResult.QuarantineNotWritable} when the folders cannot be created;
     *         or {@link MoveResult.Failed} when the rename fails (locked, access denied, another volume, the
     *         quarantine path already exists). Never throws an {@code IOException}
     */
    MoveResult move(Path file, Path quarantinePath);

    /** The result of one {@link #move}. */
    sealed interface MoveResult {

        /** The file is now at the quarantine path and no longer at its own. */
        record Moved() implements MoveResult {
        }

        /** The quarantine folders could not be created; the file was not touched. {@code detail} says why. */
        record QuarantineNotWritable(String detail) implements MoveResult {
        }

        /** The rename failed; the file is still at its own path. {@code detail} says why. */
        record Failed(String detail) implements MoveResult {
        }
    }
}
