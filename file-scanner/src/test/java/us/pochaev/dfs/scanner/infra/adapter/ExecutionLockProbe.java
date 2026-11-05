package us.pochaev.dfs.scanner.infra.adapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;

/**
 * Child-JVM entry point for the DFS-055 cross-process tests, and the helpers the tests use to start it.
 *
 * <p>{@code hold <file>}: opens the file with CREATE and WRITE, calls {@code tryLock}, prints {@code LOCKED} or
 * {@code BUSY}, then blocks reading stdin until EOF and exits. {@code try <file>}: calls {@code tryLock}, prints
 * {@code FREE} (after releasing it) or {@code HELD}, and exits.
 */
public final class ExecutionLockProbe {

    private ExecutionLockProbe() {
    }

    public static void main(String[] args) throws Exception {
        Path file = Path.of(args[1]);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock = channel.tryLock();
            if ("hold".equals(args[0])) {
                System.out.println(lock != null ? "LOCKED" : "BUSY");
                System.out.flush();
                while (System.in.read() != -1) {
                    // wait for the parent to close stdin
                }
            } else {
                if (lock != null) {
                    lock.release();
                    System.out.println("FREE");
                } else {
                    System.out.println("HELD");
                }
            }
        }
    }

    /** A child JVM running {@code hold}; closing it ends the child, bounded by a timeout, never leaving it running. */
    public static final class Holder implements AutoCloseable {
        private final Process process;
        private final String firstLine;

        private Holder(Process process, String firstLine) {
            this.process = process;
            this.firstLine = firstLine;
        }

        /** {@code LOCKED} or {@code BUSY}. */
        public String firstLine() {
            return firstLine;
        }

        @Override
        public void close() throws IOException, InterruptedException {
            try {
                process.getOutputStream().close();
                if (!process.waitFor(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("probe child did not end");
                }
            } finally {
                process.destroyForcibly();
            }
        }
    }

    private static Process spawn(String mode, Path file) throws IOException {
        Path java = Path.of(ProcessHandle.current().info().command().orElseThrow());
        return new ProcessBuilder(java.toString(), "-cp", System.getProperty("java.class.path"),
                ExecutionLockProbe.class.getName(), mode, file.toString())
                .redirectErrorStream(true)
                .start();
    }

    private static String firstLineOf(Process process) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(),
                StandardCharsets.UTF_8));
        return reader.readLine();
    }

    /** Starts a child that holds the lock on {@code file} until the returned holder is closed. */
    public static Holder hold(Path file) throws IOException {
        Process process = spawn("hold", file);
        String line;
        try {
            line = firstLineOf(process);
        } catch (IOException | RuntimeException e) {
            process.destroyForcibly();
            throw e;
        }
        return new Holder(process, line);
    }

    /** Runs a child that tries the lock on {@code file} once: {@code FREE} or {@code HELD}. */
    public static String tryLock(Path file) throws IOException, InterruptedException {
        Process process = spawn("try", file);
        try {
            String line = firstLineOf(process);
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("probe child did not end");
            }
            return line;
        } finally {
            process.destroyForcibly();
        }
    }
}
