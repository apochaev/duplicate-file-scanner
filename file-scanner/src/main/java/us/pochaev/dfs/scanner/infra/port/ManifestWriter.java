package us.pochaev.dfs.scanner.infra.port;

import us.pochaev.dfs.scanner.domain.model.PlanExecutionId;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Writes a plan execution's append-only JSON Lines manifest, the run's audit log and the input of a later undo
 * (DFS-051 decision 6). Every line is forced to disk before the call returns. Safe to call from several threads at
 * once for different manifests.
 */
public interface ManifestWriter {

    /**
     * Creates {@code folder} (and its parents) if absent, creates {@code <folder>/<run ID>.jsonl} as a new file, writes
     * {@code header} as its first line, forces it to disk and closes it.
     *
     * @return the manifest's absolute path
     * @throws IOException if the folder or the file cannot be created or written (an existing file of that name
     *                     included)
     */
    Path create(Path folder, ManifestLine.Run header) throws IOException;

    /**
     * The absolute path {@link #create} gives the manifest of {@code runId} in {@code folder},
     * {@code <folder>/<run ID>.jsonl}, without touching the disk (DFS-055: a run's job is recorded before its manifest
     * is created).
     *
     * @throws NullPointerException if an argument is null
     */
    default Path manifestPath(Path folder, PlanExecutionId runId) {
        java.util.Objects.requireNonNull(folder, "folder");
        java.util.Objects.requireNonNull(runId, "runId");
        return folder.resolve(runId.value() + ".jsonl").toAbsolutePath();
    }

    /**
     * Opens an existing manifest for appending.
     *
     * @throws IOException if it cannot be opened
     */
    Session open(Path manifest) throws IOException;

    /** An open manifest. Used by one thread at a time. */
    interface Session extends Closeable {

        /**
         * Appends {@code line} and forces it to disk before returning.
         *
         * @throws IOException if the line cannot be written or forced; the caller must then stop before the next
         *                     rename
         */
        void append(ManifestLine line) throws IOException;
    }
}
