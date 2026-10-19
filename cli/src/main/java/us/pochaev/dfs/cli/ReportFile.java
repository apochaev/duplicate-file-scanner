package us.pochaev.dfs.cli;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * The {@code --output} file (DFS-029). A temporary file is created beside the target before the scan,
 * which is also the check that the folder takes a new file (decision 3); once the scan completes the
 * report is written into it as UTF-8 and it is moved over the target (decision 4), so the target only
 * ever holds a complete report. {@link #discard} deletes the temporary file if it is still there, and a
 * shutdown hook does the same on Ctrl+C.
 */
final class ReportFile {

    /** Opens the temporary file for writing; {@code Files::newOutputStream} outside tests. */
    @FunctionalInterface
    interface Opener {
        OutputStream open(Path file) throws IOException;
    }

    private final Path target;
    private final Path temp;
    private final Opener opener;
    private final Thread cleanup;

    private ReportFile(Path target, Path temp, Opener opener) {
        this.target = target;
        this.temp = temp;
        this.opener = opener;
        this.cleanup = new Thread(this::deleteTemp, "report-file-cleanup");
        Runtime.getRuntime().addShutdownHook(cleanup);
    }

    /**
     * Creates the temporary file in {@code target}'s folder.
     *
     * @param target an absolute path whose parent folder exists
     * @throws IOException when no file can be created there
     */
    static ReportFile create(Path target, Opener opener) throws IOException {
        Objects.requireNonNull(opener, "opener");
        Path temp = Files.createTempFile(target.getParent(), target.getFileName() + ".", ".part");
        return new ReportFile(target, temp, opener);
    }

    Path target() {
        return target;
    }

    /** Writes the report into the temporary file and moves it over the target. */
    void commit(Consumer<PrintStream> report) throws IOException {
        FailureCapture stream = new FailureCapture(opener.open(temp));
        try (PrintStream out = new PrintStream(stream, false, StandardCharsets.UTF_8)) {
            report.accept(out);
            out.flush();
            stream.rethrow();
            if (out.checkError()) {
                throw new IOException("write failed");
            }
        }
        stream.rethrow();
        try {
            Files.move(temp, target, ATOMIC_MOVE, REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, REPLACE_EXISTING);
        }
    }

    /** Deletes the temporary file if it is still there; called whether or not the report was written. */
    void discard() {
        deleteTemp();
        try {
            Runtime.getRuntime().removeShutdownHook(cleanup);
        } catch (IllegalStateException e) {
            // shutting down: the hook runs anyway
        }
    }

    private void deleteTemp() {
        try {
            Files.deleteIfExists(temp);
        } catch (IOException e) {
            // best effort; a hard kill can leave the file too (decision 4)
        }
    }

    /** Keeps the first {@link IOException}, which {@link PrintStream} would otherwise swallow. */
    private static final class FailureCapture extends FilterOutputStream {

        private IOException failure;

        FailureCapture(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            try {
                out.write(b);
            } catch (IOException e) {
                throw keep(e);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            try {
                out.write(b, off, len);
            } catch (IOException e) {
                throw keep(e);
            }
        }

        @Override
        public void flush() throws IOException {
            try {
                out.flush();
            } catch (IOException e) {
                throw keep(e);
            }
        }

        @Override
        public void close() throws IOException {
            try {
                out.close();
            } catch (IOException e) {
                throw keep(e);
            }
        }

        private IOException keep(IOException e) {
            if (failure == null) {
                failure = e;
            }
            return e;
        }

        void rethrow() throws IOException {
            if (failure != null) {
                throw failure;
            }
        }
    }
}
