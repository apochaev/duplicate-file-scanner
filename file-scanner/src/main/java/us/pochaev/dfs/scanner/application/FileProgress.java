package us.pochaev.dfs.scanner.application;

import us.pochaev.dfs.scanner.domain.model.FileEntry;

/**
 * Per-file progress of a hash stage (DFS-024): {@link ScanPipeline} turns these calls into
 * {@code ScanEvent.HashProgress}. Called on the stage's thread.
 */
public interface FileProgress {

    /** Reports nothing. */
    FileProgress NONE = new FileProgress() {
        @Override
        public void bytesRead(FileEntry file, long bytes) {
        }

        @Override
        public void fileDone(FileEntry file) {
        }
    };

    /** {@code bytes} of {@code file} have been read so far; only the full-hash stage reports this. */
    void bytesRead(FileEntry file, long bytes);

    /** The stage is finished with {@code file}: hashed, dropped as unreadable, or passed through unread. */
    void fileDone(FileEntry file);
}
