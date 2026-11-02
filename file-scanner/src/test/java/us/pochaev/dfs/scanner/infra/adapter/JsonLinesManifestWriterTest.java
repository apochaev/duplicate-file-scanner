package us.pochaev.dfs.scanner.infra.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.pochaev.dfs.scanner.domain.model.JobStatus;
import us.pochaev.dfs.scanner.domain.model.PlanExecutionId;
import us.pochaev.dfs.scanner.domain.model.SkipCause;
import us.pochaev.dfs.scanner.infra.port.ManifestLine;
import us.pochaev.dfs.scanner.infra.port.ManifestWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** The JSON Lines manifest adapter on real files (DFS-051 decision 6). */
class JsonLinesManifestWriterTest {

    @TempDir
    Path tmp;

    private static final Instant T = Instant.parse("2024-03-01T10:00:00Z");

    private static ManifestLine.Run header(PlanExecutionId run, Path root) {
        return new ManifestLine.Run(run, "a".repeat(64), List.of(root), T);
    }

    private static List<String> lines(Path manifest) throws IOException {
        return Files.readAllLines(manifest, StandardCharsets.UTF_8).stream().filter(l -> !l.isBlank()).toList();
    }

    private static String type(String line) {
        Matcher m = Pattern.compile("\"type\"\\s*:\\s*\"(\\w+)\"").matcher(line);
        assertTrue(m.find(), line);
        return m.group(1);
    }

    private static String str(String line, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(line);
        assertTrue(m.find(), field + " in " + line);
        String raw = m.group(1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char n = raw.charAt(++i);
            if (n == 'u') {
                out.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                i += 4;
            } else {
                out.append(n);
            }
        }
        return out.toString();
    }

    @Test
    @DisplayName("DFS-051.AC-9 Create writes the folder and the manifest with the run line first")
    void createWritesTheRunLine() throws Exception {
        PlanExecutionId run = new PlanExecutionId(UUID.randomUUID());
        Path folder = tmp.resolve("a").resolve("manifests");

        Path manifest = new JsonLinesManifestWriter().create(folder, header(run, tmp.resolve("lib")));

        assertEquals(folder.resolve(run.value() + ".jsonl"), manifest);
        List<String> lines = lines(manifest);
        assertEquals(1, lines.size());
        assertEquals("run", type(lines.get(0)));
        assertEquals(run.value().toString(), str(lines.get(0), "runId"));
        assertEquals("a".repeat(64), str(lines.get(0), "planId"));
        assertTrue(lines.get(0).contains("2024-03-01T10:00:00Z"), lines.get(0));
    }

    @Test
    @DisplayName("DFS-051.AC-9 Each appended line is readable at once, in order, with its fields")
    void appendedLinesAreReadableAtOnceInOrder() throws Exception {
        PlanExecutionId run = new PlanExecutionId(UUID.randomUUID());
        ManifestWriter writer = new JsonLinesManifestWriter();
        Path manifest = writer.create(tmp.resolve("m"), header(run, tmp));
        Path original = tmp.resolve("lib").resolve("B").resolve("фото é.jpg");
        Path quarantine = tmp.resolve(".dfs-quarantine").resolve("x").resolve("фото é.jpg");
        Path keeper = tmp.resolve("lib").resolve("A").resolve("photo.jpg");

        try (ManifestWriter.Session session = writer.open(manifest)) {
            session.append(new ManifestLine.Moving(original, quarantine, keeper, "d1", 27, T));
            List<String> afterMoving = lines(manifest);
            assertEquals(List.of("run", "moving"), afterMoving.stream().map(JsonLinesManifestWriterTest::type).toList());
            assertEquals(original.toString(), str(afterMoving.get(1), "path"));
            assertEquals(quarantine.toString(), str(afterMoving.get(1), "quarantine"));
            assertEquals(keeper.toString(), str(afterMoving.get(1), "keeper"));
            assertEquals("d1", str(afterMoving.get(1), "digest"));
            assertTrue(Pattern.compile("\"size\"\\s*:\\s*27\\b").matcher(afterMoving.get(1)).find(),
                    afterMoving.get(1));

            session.append(new ManifestLine.Moved(original, quarantine));
            session.append(new ManifestLine.Skipped(keeper, SkipCause.DIFFERS, keeper, "first difference at byte 3"));
            session.append(new ManifestLine.End(JobStatus.CANCELLED, 1, 1, 2, T));
        }

        List<String> all = lines(manifest);
        assertEquals(List.of("run", "moving", "moved", "skipped", "end"),
                all.stream().map(JsonLinesManifestWriterTest::type).toList());
        assertEquals(original.toString(), str(all.get(2), "path"));
        assertEquals("DIFFERS", str(all.get(3), "reason"));
        assertEquals(keeper.toString(), str(all.get(3), "subject"));
        assertEquals("first difference at byte 3", str(all.get(3), "detail"));
        assertEquals("CANCELLED", str(all.get(4), "status"));
        assertTrue(Pattern.compile("\"notAttempted\"\\s*:\\s*2\\b").matcher(all.get(4)).find(), all.get(4));
    }

    @Test
    @DisplayName("DFS-051.AC-9 A reopened manifest is appended to, never overwritten")
    void reopenedManifestIsAppendedTo() throws Exception {
        PlanExecutionId run = new PlanExecutionId(UUID.randomUUID());
        ManifestWriter writer = new JsonLinesManifestWriter();
        Path manifest = writer.create(tmp.resolve("m"), header(run, tmp));
        try (ManifestWriter.Session session = writer.open(manifest)) {
            session.append(new ManifestLine.Moved(tmp.resolve("a"), tmp.resolve("b")));
        }
        try (ManifestWriter.Session session = writer.open(manifest)) {
            session.append(new ManifestLine.End(JobStatus.COMPLETED, 1, 0, 0, T));
        }

        assertEquals(List.of("run", "moved", "end"), lines(manifest).stream()
                .map(JsonLinesManifestWriterTest::type).toList());
    }

    @Test
    @DisplayName("DFS-051.AC-10 A folder path that is a regular file cannot hold a manifest")
    void folderThatIsAFileCannotHoldAManifest() throws Exception {
        Path notAFolder = tmp.resolve("manifests");
        Files.writeString(notAFolder, "in the way", StandardCharsets.UTF_8);

        try {
            new JsonLinesManifestWriter().create(notAFolder, header(new PlanExecutionId(UUID.randomUUID()), tmp));
            fail("expected an IOException");
        } catch (IOException expected) {
            // expected
        }
        assertEquals("in the way", Files.readString(notAFolder, StandardCharsets.UTF_8));
    }
}
