package us.pochaev.dfs.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The real Spring application, end to end, with stdout and stderr captured apart (DFS-020 decision 8). */
@ExtendWith(OutputCaptureExtension.class)
class CliApplicationTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("DFS-020.AC-6, DFS-020.AC-7 Stdout is pure JSONL and progress goes to stderr")
    void stdoutIsPureJsonl(CapturedOutput output) throws IOException {
        Files.writeString(tmp.resolve("x.txt"), "hello duplicate");
        Files.writeString(tmp.resolve("y.txt"), "hello duplicate");

        int exitCode;
        try (ConfigurableApplicationContext context = SpringApplication.run(Main.class, tmp.toString())) {
            exitCode = SpringApplication.exit(context);
        }

        assertEquals(0, exitCode, output.getErr());
        JsonMapper json = JsonMapper.builder().build();
        List<String> lines = output.getOut().lines().toList();
        assertFalse(lines.isEmpty());
        for (String line : lines) {
            JsonNode node = json.readTree(line);
            assertTrue(node.isObject() && node.hasNonNull("type"), "not a JSONL line on stdout: " + line);
        }
        assertEquals(List.of("scan", "folder", "set", "summary"), lines.stream().map(l -> json.readTree(l).get("type").asString()).toList());
        // the phase lines' wording is DFS-024's (it replaced "scan: walk started" and the like)
        assertTrue(output.getErr().contains("Walking:"), output.getErr());
        assertTrue(output.getErr().contains("Hashing:"), output.getErr());
    }
}
