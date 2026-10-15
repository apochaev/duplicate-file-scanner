package us.pochaev.dfs.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import us.pochaev.dfs.scanner.domain.factory.ScanUseCases;
import us.pochaev.dfs.scanner.domain.factory.ScannerFactories;
import us.pochaev.dfs.scanner.domain.model.DuplicateSet;
import us.pochaev.dfs.scanner.domain.model.FileEntry;
import us.pochaev.dfs.scanner.domain.model.PhysicalCopy;
import us.pochaev.dfs.scanner.domain.model.Result;
import us.pochaev.dfs.scanner.domain.model.ScanConfig;
import us.pochaev.dfs.scanner.domain.model.ScanEvent;
import us.pochaev.dfs.scanner.domain.model.ScanId;
import us.pochaev.dfs.scanner.domain.model.ScanListener;
import us.pochaev.dfs.scanner.domain.model.ScanPhase;
import us.pochaev.dfs.scanner.domain.model.ScanResult;
import us.pochaev.dfs.scanner.domain.model.ScanSnapshot;
import us.pochaev.dfs.scanner.domain.model.ScanStatus;
import us.pochaev.dfs.scanner.domain.usecases.command.StartScan;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanResult;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@link ScanCommand} without Spring (DFS-020 decision 8): real scanning through
 * {@link ScannerFactories#scanUseCases} on temporary folders, and hand-written fake ports where a
 * scenario needs a scripted scan.
 */
class ScanCommandTest {

    private static final String DUP = "hello duplicate";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    Path tmp;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void stopExecutor() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("DFS-020.AC-2, DFS-020.AC-5, DFS-020.AC-8, DFS-020.AC-11 Duplicates across two folders become one set line and a summary")
    void duplicatesAcrossTwoFolders() throws IOException {
        Path a = Files.createDirectories(tmp.resolve("a"));
        Path b = Files.createDirectories(tmp.resolve("b"));
        Path x = write(a.resolve("x.txt"), DUP);
        Path y = write(b.resolve("y.txt"), DUP);
        write(b.resolve("z.txt"), "unique");

        Run run = scan(a.toString(), b.toString());

        assertEquals(0, run.exitCode);
        List<JsonNode> lines = run.jsonLines();
        assertEquals(2, lines.size(), run.out);
        JsonNode set = lines.get(0);
        assertEquals("set", set.get("type").asString());
        assertEquals(15, set.get("size").asLong());
        assertEquals(sha256(DUP), set.get("digest").asString());
        assertEquals(List.of(List.of(real(x)), List.of(real(y))), copies(set));
        assertEquals(JSON.readTree("{\"type\":\"summary\",\"sets\":1,\"files\":2,\"skipped\":0,\"reclaimableBytes\":15}"),
                lines.get(1));
    }

    @Test
    @DisplayName("DFS-020.AC-3 Hard links are one copy, not duplicates")
    void hardLinksAreOneCopy() throws IOException {
        Path x = write(tmp.resolve("x.txt"), DUP);
        Path link = tmp.resolve("x-link.txt");
        try {
            Files.createLink(link, x);
        } catch (UnsupportedOperationException | FileSystemException e) {
            abort("hard links are not available here: " + e.getMessage());
        }
        Path y = write(tmp.resolve("y.txt"), DUP);

        Run run = scan(tmp.toString());

        List<JsonNode> lines = run.jsonLines();
        assertEquals(List.of(List.of(real(link), real(x)), List.of(real(y))), copies(lines.get(0)));
        JsonNode summary = lines.getLast();
        assertEquals(3, summary.get("files").asInt());
        assertEquals(15, summary.get("reclaimableBytes").asLong());
    }

    @Test
    @DisplayName("DFS-020.AC-4 A symbolic link is reported as skipped, after the sets")
    void symbolicLinkIsSkipped() throws IOException {
        Path x = write(tmp.resolve("x.txt"), DUP);
        write(tmp.resolve("y.txt"), DUP);
        Path link = tmp.resolve("link.txt");
        try {
            Files.createSymbolicLink(link, x);
        } catch (UnsupportedOperationException | IOException e) {
            abort("symbolic links are not available here: " + e.getMessage());
        }

        Run run = scan(tmp.toString());

        List<JsonNode> lines = run.jsonLines();
        assertEquals(3, lines.size(), run.out);
        assertEquals("set", lines.get(0).get("type").asString());
        assertFalse(lines.get(0).toString().contains("link.txt"));
        JsonNode skipped = lines.get(1);
        assertEquals("skipped", skipped.get("type").asString());
        assertEquals(tmp.toRealPath().resolve("link.txt").toString(), skipped.get("path").asString());
        assertEquals("SYMLINK", skipped.get("reason").asString());
        assertTrue(skipped.get("detail").isString());
        assertEquals("summary", lines.get(2).get("type").asString());
        assertEquals(1, lines.get(2).get("skipped").asInt());
    }

    @Test
    @DisplayName("DFS-020.AC-4 A Windows junction is skipped, so its target's files are not counted twice")
    void junctionIsSkipped() throws IOException, InterruptedException {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"), "junctions exist only on Windows");
        Path data = Files.createDirectories(tmp.resolve("data"));
        Path x = write(data.resolve("x.txt"), DUP);
        Path y = write(data.resolve("y.txt"), DUP);
        Path junction = tmp.resolve("j");
        Process mklink = new ProcessBuilder("cmd", "/c", "mklink", "/J", junction.toString(), data.toString())
                .redirectErrorStream(true).start();
        mklink.getInputStream().readAllBytes();
        assertEquals(0, mklink.waitFor(), "mklink /J failed");

        Run run = scan(tmp.toString());

        List<JsonNode> lines = run.jsonLines();
        assertEquals(3, lines.size(), run.out);
        assertEquals(List.of(List.of(real(x)), List.of(real(y))), copies(lines.get(0)));
        JsonNode skipped = lines.get(1);
        assertEquals("skipped", skipped.get("type").asString());
        assertEquals(tmp.toRealPath().resolve("j").toString(), skipped.get("path").asString());
        assertEquals("SPECIAL_FILE", skipped.get("reason").asString());
    }

    @Test
    @DisplayName("DFS-020.AC-5, DFS-020.AC-11 No duplicates still ends with a summary")
    void noDuplicatesStillEndsWithSummary() throws IOException {
        write(tmp.resolve("a.txt"), "one");
        write(tmp.resolve("b.txt"), "two");
        write(tmp.resolve("e1.txt"), "");
        write(tmp.resolve("e2.txt"), "");

        Run run = scan(tmp.toString());

        assertEquals(0, run.exitCode);
        assertEquals("{\"type\":\"summary\",\"sets\":0,\"files\":0,\"skipped\":0,\"reclaimableBytes\":0}\n", run.out);
    }

    @Test
    @DisplayName("DFS-020.AC-9 --min-size leaves small duplicates out")
    void minSizeLeavesSmallDuplicatesOut() throws IOException {
        write(tmp.resolve("s1.txt"), "abc");
        write(tmp.resolve("s2.txt"), "abc");
        write(tmp.resolve("b1.txt"), DUP);
        write(tmp.resolve("b2.txt"), DUP);

        Run run = scan(tmp.toString(), "--min-size=10");

        List<JsonNode> lines = run.jsonLines();
        assertEquals(2, lines.size(), run.out);
        assertEquals(15, lines.get(0).get("size").asLong());
        assertEquals(1, lines.get(1).get("sets").asInt());
        assertEquals(2, lines.get(1).get("files").asInt());
    }

    @ParameterizedTest(name = "[{index}] args: {0}")
    @ValueSource(strings = {"", "--min-size=10", "<folder> --recursive", "<folder> --min-size=abc",
            "<folder> --min-size=0", "<folder> --min-size=-5"})
    @DisplayName("DFS-020.AC-10, DFS-020.AC-12 A usage error starts no scan")
    void usageErrorStartsNoScan(String args) {
        FakePorts fake = new FakePorts();
        String[] argv = args.isEmpty() ? new String[0] : args.replace("<folder>", tmp.toString()).split(" ");

        Run run = run(fake, argv);

        assertEquals(2, run.exitCode);
        assertTrue(run.err.startsWith("error: "), run.err);
        assertTrue(run.err.contains(ScanCommand.USAGE_LINE), run.err);
        assertEquals("", run.out);
        assertEquals(0, fake.starts);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"missing:LOCATION_NOT_FOUND", "file:NOT_A_DIRECTORY", "good+missing:LOCATION_NOT_FOUND"})
    @DisplayName("DFS-020.AC-13 A refused start exits 1 with the scanner's error code")
    void refusedStartExitsOne(String example) throws IOException {
        Path a = Files.createDirectories(tmp.resolve("a"));
        Path file = write(tmp.resolve("f.txt"), "x");
        String[] parts = example.split(":");
        String[] args = switch (parts[0]) {
            case "missing" -> new String[] {a.resolve("missing").toString()};
            case "file" -> new String[] {file.toString()};
            default -> new String[] {a.toString(), a.resolve("missing").toString()};
        };

        Run run = scan(args);

        assertEquals(1, run.exitCode);
        assertTrue(run.err.startsWith("error: " + parts[1] + ": "), run.err);
        assertEquals("", run.out);
    }

    @Test
    @DisplayName("DFS-020.AC-14 A failed scan writes no JSONL and exits 1")
    void failedScanWritesNoJsonl() {
        FakePorts fake = new FakePorts(
                snapshot(ScanStatus.RUNNING, null), snapshot(ScanStatus.RUNNING, null),
                snapshot(ScanStatus.FAILED, "java.io.UncheckedIOException: disk gone"));

        Run run = run(fake, tmp.toString());

        assertEquals(1, run.exitCode);
        assertTrue(run.err.lines().anyMatch("error: scan failed: java.io.UncheckedIOException: disk gone"::equals), run.err);
        assertEquals("", run.out);
        assertEquals(0, fake.resultCalls);
    }

    @Test
    @DisplayName("DFS-020.AC-14, DFS-020.AC-2 Output is written only once the scan has completed")
    void outputOnlyAfterCompletion() {
        FakePorts fake = new FakePorts(
                snapshot(ScanStatus.QUEUED, null), snapshot(ScanStatus.RUNNING, null),
                snapshot(ScanStatus.COMPLETED, null));
        FileEntry p = new FileEntry(Path.of("p.txt"), 4, Instant.EPOCH);
        FileEntry q = new FileEntry(Path.of("q.txt"), 4, Instant.EPOCH);
        fake.result = new ScanResult(List.of(new DuplicateSet(4, "ab", List.of(p, q),
                List.of(new PhysicalCopy(List.of(p)), new PhysicalCopy(List.of(q))))), List.of());

        Run run = run(fake, tmp.toString());

        assertEquals(0, run.exitCode);
        assertEquals(List.of("status", "status", "status", "result"), fake.calls);
        List<JsonNode> lines = run.jsonLines();
        assertEquals(List.of("set", "summary"), lines.stream().map(l -> l.get("type").asString()).toList());
    }

    // --- DFS-023: the text report

    @Test
    @DisplayName("DFS-023.AC-1, DFS-023.AC-2, DFS-023.AC-7 A file in three places across two folders is one set of three copies")
    void textReportOneSetOfThreeCopies() throws IOException {
        Path a = Files.createDirectories(tmp.resolve("a"));
        Path b = Files.createDirectories(tmp.resolve("b"));
        Path x = write(a.resolve("x.txt"), DUP);
        write(a.resolve("u.txt"), "unique");
        Path y = write(b.resolve("y.txt"), DUP);
        Path z = write(Files.createDirectories(b.resolve("sub")).resolve("z.txt"), DUP);

        Run run = scan(a.toString(), b.toString(), "--format=text");

        assertEquals(0, run.exitCode, run.err);
        assertEquals(List.of(
                "#1  30 B reclaimable  (3 copies x 15 B, sha256 " + sha256(DUP).substring(0, 12) + ")",
                "    " + real(x),
                "    " + real(z),
                "    " + real(y),
                "Summary: 1 sets, 3 files, 30 B reclaimable, 0 skipped"), run.out.lines().toList());
        assertTrue(run.out.endsWith("\n"), run.out);
        assertTrue(run.err.contains("Walking:"), run.err);
    }

    @ParameterizedTest(name = "[{index}] options: {0}")
    @ValueSource(strings = {"", "--format=jsonl"})
    @DisplayName("DFS-023.AC-1 JSONL stays the default and is unchanged")
    void jsonlStaysTheDefault(String option) throws IOException {
        write(tmp.resolve("x.txt"), DUP);
        write(tmp.resolve("y.txt"), DUP);

        Run run = option.isEmpty() ? scan(tmp.toString()) : scan(tmp.toString(), option);

        assertEquals(0, run.exitCode, run.err);
        List<JsonNode> lines = run.jsonLines();
        assertEquals(2, lines.size(), run.out);
        assertEquals("set", lines.get(0).get("type").asString());
        assertEquals(JSON.readTree("{\"type\":\"summary\",\"sets\":1,\"files\":2,\"skipped\":0,\"reclaimableBytes\":15}"),
                lines.get(1));
    }

    @Test
    @DisplayName("DFS-023.AC-3 Hard links are shown under their copy, not as copies")
    void textReportHardLinksUnderTheirCopy() throws IOException {
        Path x = write(tmp.resolve("x.txt"), DUP);
        Path link = tmp.resolve("x-link.txt");
        try {
            Files.createLink(link, x);
        } catch (UnsupportedOperationException | FileSystemException e) {
            abort("hard links are not available here: " + e.getMessage());
        }
        Path y = write(tmp.resolve("y.txt"), DUP);

        Run run = scan(tmp.toString(), "--format=text");

        assertEquals(List.of(
                "#1  15 B reclaimable  (2 copies x 15 B, sha256 " + sha256(DUP).substring(0, 12) + ")",
                "    " + real(link),
                "      (hard link) " + real(x),
                "    " + real(y),
                "Summary: 1 sets, 3 files, 15 B reclaimable, 0 skipped"), run.out.lines().toList());
    }

    @Test
    @DisplayName("DFS-023.AC-6, DFS-023.AC-7 Skipped paths are listed after the sets, before the summary")
    void textReportListsSkippedPaths() throws IOException {
        Path x = write(tmp.resolve("x.txt"), DUP);
        write(tmp.resolve("y.txt"), DUP);
        Path link = tmp.resolve("link.txt");
        try {
            Files.createSymbolicLink(link, x);
        } catch (UnsupportedOperationException | IOException e) {
            abort("symbolic links are not available here: " + e.getMessage());
        }

        Run run = scan(tmp.toString(), "--format=text");

        List<String> lines = run.out.lines().toList();
        assertEquals(6, lines.size(), run.out);
        assertTrue(lines.get(0).startsWith("#1  15 B reclaimable  (2 copies x 15 B"), run.out);
        assertEquals("Skipped (1):", lines.get(3));
        assertTrue(lines.get(4).startsWith("    " + tmp.toRealPath().resolve("link.txt") + "  SYMLINK  "), lines.get(4));
        assertEquals("Summary: 1 sets, 2 files, 15 B reclaimable, 1 skipped", lines.get(5));
    }

    @Test
    @DisplayName("DFS-023.AC-6, DFS-023.AC-7 No duplicates and nothing skipped")
    void textReportNoDuplicates() throws IOException {
        write(tmp.resolve("a.txt"), "one");
        write(tmp.resolve("b.txt"), "two");

        Run run = scan(tmp.toString(), "--format=text");

        assertEquals(0, run.exitCode, run.err);
        assertEquals("No duplicates found.\nSummary: 0 sets, 0 files, 0 B reclaimable, 0 skipped\n", run.out);
    }

    @ParameterizedTest(name = "[{index}] args: {0}")
    @ValueSource(strings = {"<folder> --format=txt", "<folder> --format=", "<folder> --format=TEXT",
            "<folder> --format=text --format=jsonl"})
    @DisplayName("DFS-023.AC-9 A bad --format starts no scan")
    void badFormatStartsNoScan(String args) {
        FakePorts fake = new FakePorts();

        Run run = run(fake, args.replace("<folder>", tmp.toString()).split(" "));

        assertEquals(2, run.exitCode);
        assertTrue(run.err.startsWith("error: "), run.err);
        assertTrue(run.err.contains(ScanCommand.USAGE_LINE), run.err);
        assertTrue(ScanCommand.USAGE_LINE.contains("--format=jsonl|text"), ScanCommand.USAGE_LINE);
        assertEquals("", run.out);
        assertEquals(0, fake.starts);
    }

    // --- DFS-024: the progress display

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"", "--progress=live", "--progress=log", "--progress=off"})
    @DisplayName("DFS-024.AC-10, DFS-024.AC-13 Stdout is the same in every progress mode")
    void stdoutIsTheSameInEveryProgressMode(String option) throws IOException {
        Path x = write(tmp.resolve("x.txt"), DUP);
        Path y = write(tmp.resolve("y.txt"), DUP);

        Run run = option.isEmpty() ? scan(tmp.toString(), "--format=text") : scan(tmp.toString(), "--format=text", option);

        assertEquals(0, run.exitCode, run.err);
        assertEquals(List.of(
                "#1  15 B reclaimable  (2 copies x 15 B, sha256 " + sha256(DUP).substring(0, 12) + ")",
                "    " + real(x),
                "    " + real(y),
                "Summary: 1 sets, 2 files, 15 B reclaimable, 0 skipped"), run.out.lines().toList());
        switch (option) {
            case "--progress=off" -> assertEquals("", run.err);
            case "--progress=log" -> {
                assertFalse(run.err.contains("\r"), run.err);
                assertTrue(run.err.lines().toList().getLast().startsWith("Done in "), run.err);
            }
            default -> {
                assertTrue(run.err.contains("\r"), run.err);
                assertTrue(run.err.lines().toList().getLast().startsWith("Done in "), run.err);
            }
        }
    }

    @Test
    @DisplayName("DFS-024.AC-11 A completed scan ends with the Done line")
    void completedScanEndsWithDoneLine() throws IOException {
        write(tmp.resolve("x.txt"), DUP);
        write(tmp.resolve("y.txt"), DUP);
        write(tmp.resolve("z.txt"), "unique!");

        Run run = scan(tmp.toString(), "--progress=log");

        String last = run.err.lines().toList().getLast();
        assertTrue(last.matches("Done in \\d+:\\d\\d: 3 files found, 2 compared, 1 duplicate sets"), run.err);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"--progress=live", "--progress=off"})
    @DisplayName("DFS-024.AC-10, DFS-024.AC-11 A failed scan finishes the line and reports the error")
    void failedScanFinishesTheLine(String option) {
        FakePorts fake = new FakePorts(
                snapshot(ScanStatus.RUNNING, null),
                snapshot(ScanStatus.FAILED, "java.io.UncheckedIOException: disk gone"));
        fake.events = List.of(new ScanEvent.PhaseStarted(ScanPhase.WALK), new ScanEvent.FileWalked(1, 10));

        Run run = run(fake, tmp.toString(), option);

        assertEquals(1, run.exitCode);
        String error = "error: scan failed: java.io.UncheckedIOException: disk gone\n";
        if (option.equals("--progress=off")) {
            assertEquals(error, run.err.replace("\r\n", "\n"));
        } else {
            String err = run.err.replace(System.lineSeparator(), "\n");
            assertTrue(err.matches("(?s)\rWalking:    0 folders, 1 files, 10 B found  0:\\d\\d\n" + java.util.regex.Pattern.quote(error)),
                    err);
        }
        assertFalse(run.err.contains("Done in"), run.err);
    }

    @ParameterizedTest(name = "[{index}] args: {0}")
    @ValueSource(strings = {"<folder> --progress=quiet", "<folder> --progress=", "<folder> --progress=LIVE",
            "<folder> --progress=log --progress=off"})
    @DisplayName("DFS-024.AC-12 A bad --progress starts no scan")
    void badProgressStartsNoScan(String args) {
        FakePorts fake = new FakePorts();

        Run run = run(fake, args.replace("<folder>", tmp.toString()).split(" "));

        assertEquals(2, run.exitCode);
        assertTrue(run.err.startsWith("error: "), run.err);
        assertTrue(run.err.contains(ScanCommand.USAGE_LINE), run.err);
        assertTrue(ScanCommand.USAGE_LINE.contains("--progress=live|log|off"), ScanCommand.USAGE_LINE);
        assertEquals("", run.out);
        assertEquals(0, fake.starts);
    }

    @Test
    @DisplayName("DFS-028.AC-1 A path with a NUL character is a usage error on every platform")
    void nulInPathIsUsageError() {
        FakePorts fake = new FakePorts();
        String bad = "C:/scan/a\u0000b";
        InvalidPathException rejected = assertThrows(InvalidPathException.class, () -> Path.of(bad));

        Run run = run(fake, bad, "--format=text");

        assertEquals(2, run.exitCode);
        List<String> lines = run.err.lines().toList();
        assertEquals(2, lines.size(), run.err);
        assertTrue(lines.get(0).startsWith("error: not a valid path: \"C:/scan/a"), lines.get(0));
        assertTrue(lines.get(0).endsWith("\" (" + rejected.getReason()
                + (rejected.getIndex() < 0 ? "" : " at index " + rejected.getIndex()) + ")"), lines.get(0));
        assertEquals(ScanCommand.USAGE_LINE, lines.get(1));
        assertEquals("", run.out);
        assertEquals(0, fake.starts);
    }

    @Test
    @DisplayName("DFS-028.AC-1 A folder name ending in a space is a usage error on Windows")
    void trailingSpaceIsUsageErrorOnWindows() {
        assumeTrue(System.getProperty("os.name").startsWith("Windows"), "only Windows rejects a trailing space");
        FakePorts fake = new FakePorts();

        Run run = run(fake, "E:/Media/iPhone/2018-03 Photoslibrary ");

        assertEquals(2, run.exitCode);
        assertEquals("error: not a valid path: \"E:/Media/iPhone/2018-03 Photoslibrary \" (Trailing char < > at index 37)",
                run.err.lines().findFirst().orElseThrow());
        assertEquals("", run.out);
        assertEquals(0, fake.starts);
    }

    @Test
    @DisplayName("DFS-028.AC-1 Only the first rejected path is reported, and a valid folder before it is not scanned")
    void onlyFirstRejectedPathIsReported() {
        FakePorts fake = new FakePorts();

        Run run = run(fake, "C:/scan/ok", "C:/scan/a\u0000b", "C:/scan/c\u0000d");

        assertEquals(2, run.exitCode);
        List<String> errors = run.err.lines().filter(line -> line.startsWith("error:")).toList();
        assertEquals(1, errors.size(), run.err);
        assertTrue(errors.getFirst().startsWith("error: not a valid path: \"C:/scan/a"), errors.getFirst());
        assertEquals("", run.out);
        assertEquals(0, fake.starts);
    }

    // --- helpers

    private Run scan(String... args) {
        ScanUseCases ports = ScannerFactories.scanUseCases(executor);
        return run(new ScanCommand(ports.start(), ports.status(), ports.result(), Duration.ofMillis(10)), args);
    }

    private Run run(FakePorts fake, String... args) {
        return run(new ScanCommand(fake, fake, fake, Duration.ZERO), args);
    }

    private static Run run(ScanCommand command, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = command.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Run(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private record Run(int exitCode, String out, String err) {

        /** Every stdout line, each asserted to parse as exactly one JSON object. */
        List<JsonNode> jsonLines() {
            assertTrue(out.isEmpty() || out.endsWith("\n"), out);
            List<JsonNode> nodes = new ArrayList<>();
            for (String line : out.lines().toList()) {
                JsonNode node = JSON.readTree(line);
                assertTrue(node.isObject(), line);
                nodes.add(node);
            }
            return nodes;
        }
    }

    private static List<List<String>> copies(JsonNode set) {
        List<List<String>> copies = new ArrayList<>();
        for (JsonNode copy : set.get("copies")) {
            List<String> links = new ArrayList<>();
            copy.forEach(link -> links.add(link.asString()));
            copies.add(links);
        }
        return copies;
    }

    private static Path write(Path file, String content) throws IOException {
        return Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static String real(Path path) throws IOException {
        return path.getParent().toRealPath().resolve(path.getFileName()).toString();
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static ScanSnapshot snapshot(ScanStatus status, String failureMessage) {
        return new ScanSnapshot(status, null, 0, 0, 0, failureMessage, null);
    }

    /** Scripted ports: {@code status} returns the given snapshots in turn, then repeats the last. */
    private static final class FakePorts implements StartScan, GetScanStatus, GetScanResult {

        private final ScanId id = ScanId.random();
        private final Deque<ScanSnapshot> snapshots;
        final List<String> calls = new ArrayList<>();
        ScanResult result = new ScanResult(List.of(), List.of());
        List<ScanEvent> events = List.of();
        int starts;
        int resultCalls;

        FakePorts(ScanSnapshot... snapshots) {
            this.snapshots = new ArrayDeque<>(Arrays.asList(snapshots));
        }

        @Override
        public Result<ScanId> start(List<Path> locations, ScanConfig config) {
            return start(locations, config, event -> { });
        }

        @Override
        public Result<ScanId> start(List<Path> locations, ScanConfig config, ScanListener listener) {
            starts++;
            events.forEach(listener::onEvent);
            return new Result.Success<>(id);
        }

        @Override
        public Result<ScanSnapshot> status(ScanId scanId) {
            calls.add("status");
            ScanSnapshot next = snapshots.size() > 1 ? snapshots.pop() : snapshots.peek();
            return new Result.Success<>(next);
        }

        @Override
        public Result<ScanResult> result(ScanId scanId) {
            calls.add("result");
            resultCalls++;
            return new Result.Success<>(result);
        }
    }
}
