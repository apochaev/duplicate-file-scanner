package us.pochaev.dfs.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchScriptTest {

    private static final String LISTENING = "DFS server listening on http://127.0.0.1:";

    @Test
    @DisplayName("DFS-035.AC-7 The installed start script launches the server and the whole process tree stops")
    void startScriptLaunchesTheServer() throws Exception {
        String install = System.getProperty("dfs.installDir");
        assertNotNull(install, "system property dfs.installDir is not set");
        Path bin = Path.of(install, "bin");
        assertTrue(Files.isRegularFile(bin.resolve("dfs-server")), "missing bin/dfs-server");
        assertTrue(Files.isRegularFile(bin.resolve("dfs-server.bat")), "missing bin/dfs-server.bat");

        boolean windows = System.getProperty("os.name").startsWith("Windows");
        List<String> command = windows
                ? List.of("cmd.exe", "/c", bin.resolve("dfs-server.bat").toString())
                : List.of("sh", bin.resolve("dfs-server").toString());
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        // The scripts take Java from JAVA_HOME; use this test's Java 25 rather than whatever is on PATH.
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));

        Process process = builder.start();
        List<ProcessHandle> started = new ArrayList<>();
        try {
            CompletableFuture<String> listening = CompletableFuture.supplyAsync(() -> firstLineWith(process, LISTENING));
            String line = listening.get(60, TimeUnit.SECONDS);
            assertNotNull(line, "the script ended without printing the listening line");
            assertTrue(line.contains(LISTENING), line);
        } finally {
            started.add(process.toHandle());
            process.descendants().forEach(started::add);
            started.reversed().forEach(ProcessHandle::destroyForcibly);
            for (ProcessHandle handle : started) {
                handle.onExit().get(30, TimeUnit.SECONDS);
            }
        }
        assertEquals(List.of(), started.stream().filter(ProcessHandle::isAlive).toList(), "processes still running");
    }

    private static String firstLineWith(Process process, String text) {
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(text)) {
                    return line;
                }
            }
            return null;
        } catch (java.io.IOException e) {
            return null;
        }
    }
}
