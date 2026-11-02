package us.pochaev.dfs.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the start script {@code installBootDist} writes (DFS-043 decision 3), from the install folder the build passes
 * in system property {@code dfs.installDir}, the way the server's {@code LaunchScriptTest} does.
 */
class LaunchScriptTest {

    private static Path bin() {
        String install = System.getProperty("dfs.installDir");
        assertNotNull(install, "system property dfs.installDir is not set");
        return Path.of(install, "bin");
    }

    @Test
    @DisplayName("DFS-043.AC-1 The CLI installs as dfs, not cli")
    void theCliInstallsAsDfs() {
        Path bin = bin();
        assertTrue(Files.isRegularFile(bin.resolve("dfs")), "missing bin/dfs");
        assertTrue(Files.isRegularFile(bin.resolve("dfs.bat")), "missing bin/dfs.bat");
        assertFalse(Files.exists(bin.resolve("cli")), "bin/cli still exists");
        assertFalse(Files.exists(bin.resolve("cli.bat")), "bin/cli.bat still exists");
    }

    @Test
    @DisplayName("DFS-043.AC-2 The installed script scans a folder and reports the duplicate")
    void theInstalledScriptScansAFolder(@TempDir Path temp) throws Exception {
        Path folder = Files.createDirectories(temp.resolve("scan"));
        Path a = Files.createDirectories(folder.resolve("a")).resolve("same.txt");
        Path b = Files.createDirectories(folder.resolve("b")).resolve("same.txt");
        Files.writeString(a, "dfs-043");
        Files.writeString(b, "dfs-043");
        Path stdout = temp.resolve("stdout.jsonl");
        Path stderr = temp.resolve("stderr.txt");

        Path bin = bin();
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        List<String> command = windows
                ? List.of("cmd.exe", "/c", bin.resolve("dfs.bat").toString(), folder.toString(), "--progress=off")
                : List.of("sh", bin.resolve("dfs").toString(), folder.toString(), "--progress=off");
        ProcessBuilder builder = new ProcessBuilder(command)
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile());
        // The scripts take Java from JAVA_HOME; use this test's Java 25 rather than whatever is on PATH.
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));

        Process process = builder.start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "the script did not finish within 60 seconds");
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
        String errors = Files.readString(stderr, StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), "exit code; stderr: " + errors);

        List<String> lines = Files.readAllLines(stdout, StandardCharsets.UTF_8);
        List<String> sets = lines.stream().filter(line -> line.startsWith("{\"type\":\"set\"")).toList();
        List<String> summaries = lines.stream().filter(line -> line.startsWith("{\"type\":\"summary\"")).toList();
        assertEquals(1, sets.size(), "set lines in " + lines);
        assertTrue(sets.getFirst().contains(jsonPath(a)), sets.getFirst());
        assertTrue(sets.getFirst().contains(jsonPath(b)), sets.getFirst());
        assertEquals(1, summaries.size(), "summary lines in " + lines);
    }

    /**
     * The file's last two path elements as they appear inside a JSON string ({@code a\\same.txt} on Windows,
     * {@code a/same.txt} elsewhere); the scanner may report the temp folder through its real path, so the full path is
     * not compared.
     */
    private static String jsonPath(Path path) {
        return (path.getParent().getFileName() + path.getFileSystem().getSeparator() + path.getFileName())
                .replace("\\", "\\\\");
    }
}
