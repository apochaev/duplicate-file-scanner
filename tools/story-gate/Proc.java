import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Runs a child process and captures its output as UTF-8. */
final class Proc {

    /** {@code errors} is empty when stderr was merged into {@code output}. */
    record Result(int exit, String output, String errors) {}

    private Proc() {}

    static Result run(Path dir, List<String> command) {
        return run(dir, command, Map.of(), true);
    }

    static Result run(Path dir, String... command) {
        return run(dir, new ArrayList<>(List.of(command)));
    }

    /**
     * {@code env} entries are added to the child's environment; a null value removes the variable. With
     * {@code mergeErrors} false, stderr is captured apart from stdout, for output that is parsed as data
     * (git prints warnings on stderr, and they must never end up inside a path).
     */
    static Result run(Path dir, List<String> command, Map<String, String> env, boolean mergeErrors) {
        ProcessBuilder pb = new ProcessBuilder(command).directory(dir.toFile());
        pb.redirectInput(ProcessBuilder.Redirect.from(new File(isWindows() ? "NUL" : "/dev/null")));
        env.forEach((key, value) -> {
            if (value == null) pb.environment().remove(key);
            else pb.environment().put(key, value);
        });
        Path errorFile = null;
        try {
            if (mergeErrors) {
                pb.redirectErrorStream(true);
            } else {
                errorFile = Files.createTempFile("story-gate-stderr-", ".txt");
                pb.redirectError(errorFile.toFile());
            }
            Process p = pb.start();
            byte[] out = p.getInputStream().readAllBytes();
            int exit = p.waitFor();
            String errors = errorFile == null ? "" : new String(Files.readAllBytes(errorFile), StandardCharsets.UTF_8);
            return new Result(exit, new String(out, StandardCharsets.UTF_8), errors);
        } catch (IOException e) {
            throw new GateError("cannot run " + command.get(0) + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GateError("interrupted while running " + command.get(0), e);
        } finally {
            if (errorFile != null) errorFile.toFile().delete();
        }
    }

    static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
