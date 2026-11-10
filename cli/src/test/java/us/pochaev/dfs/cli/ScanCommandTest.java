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
import us.pochaev.dfs.scanner.domain.model.FolderTotal;
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
import us.pochaev.dfs.scanner.domain.model.SkipReason;
import us.pochaev.dfs.scanner.domain.model.WalkEvent;
import us.pochaev.dfs.scanner.domain.usecases.command.StartScan;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanResult;
import us.pochaev.dfs.scanner.domain.usecases.query.GetScanStatus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
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
    /** A fixed clock for scans whose times do not matter, so two runs write identical headers. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-31T09:12:03Z"), ZoneOffset.UTC);

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
        assertEquals(JSON.readTree("{\"type\":\"summary\",\"sets\":1,\"files\":2,\"skipped\":0,\"reclaimableBytes\":15,\"excluded\":0}"),
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
        assertEquals("{\"type\":\"summary\",\"sets\":0,\"files\":0,\"skipped\":0,\"reclaimableBytes\":0,\"excluded\":0}",
                run.out.lines().toList().getLast());
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
        fake.result = new ScanResult(List.of(), List.of(), List.of(new DuplicateSet(4, "ab", List.of(p, q),
                List.of(new PhysicalCopy(List.of(p)), new PhysicalCopy(List.of(q))))), List.of(), 0);

        Run run = run(fake, tmp.toString());

        assertEquals(0, run.exitCode);
        assertEquals(List.of("status", "status", "status", "result"), fake.calls);
        assertEquals(List.of("scan", "set", "summary"),
                run.allJsonLines().stream().map(l -> l.get("type").asString()).toList());
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
                "Summary: 1 sets, 3 files, 30 B reclaimable, 0 skipped, 0 excluded"), run.textBody());
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
        assertEquals(JSON.readTree("{\"type\":\"summary\",\"sets\":1,\"files\":2,\"skipped\":0,\"reclaimableBytes\":15,\"excluded\":0}"),
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
                "Summary: 1 sets, 3 files, 15 B reclaimable, 0 skipped, 0 excluded"), run.textBody());
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

        List<String> lines = run.textBody();
        assertEquals(6, lines.size(), run.out);
        assertTrue(lines.get(0).startsWith("#1  15 B reclaimable  (2 copies x 15 B"), run.out);
        assertEquals("Skipped (1):", lines.get(3));
        assertTrue(lines.get(4).startsWith("    " + tmp.toRealPath().resolve("link.txt") + "  SYMLINK  "), lines.get(4));
        assertEquals("Summary: 1 sets, 2 files, 15 B reclaimable, 1 skipped, 0 excluded", lines.get(5));
    }

    @Test
    @DisplayName("DFS-023.AC-6, DFS-023.AC-7 No duplicates and nothing skipped")
    void textReportNoDuplicates() throws IOException {
        write(tmp.resolve("a.txt"), "one");
        write(tmp.resolve("b.txt"), "two");

        Run run = scan(tmp.toString(), "--format=text");

        assertEquals(0, run.exitCode, run.err);
        assertEquals(List.of("No duplicates found.",
                "Summary: 0 sets, 0 files, 0 B reclaimable, 0 skipped, 0 excluded"), run.textBody());
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
                "Summary: 1 sets, 2 files, 15 B reclaimable, 0 skipped, 0 excluded"), run.textBody());
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

    // --- DFS-027: default excludes

    /** The ticket's Background: photo.jpg, ._photo.jpg and .DS_Store in folders a and b. */
    private Path mediaFolder() throws IOException {
        Path media = Files.createDirectories(tmp.resolve("media"));
        for (String dir : List.of("a", "b")) {
            Files.createDirectories(media.resolve(dir));
            write(media.resolve(dir).resolve("photo.jpg"), "P".repeat(6 * 1024));
            write(media.resolve(dir).resolve("._photo.jpg"), "M".repeat(4 * 1024));
            write(media.resolve(dir).resolve(".DS_Store"), "D".repeat(6 * 1024));
        }
        return media;
    }

    private static List<String> setPaths(JsonNode set) {
        return copies(set).stream().flatMap(List::stream).toList();
    }

    private static List<JsonNode> setLines(Run run) {
        return run.jsonLines().stream().filter(l -> l.get("type").asString().equals("set")).toList();
    }

    @Test
    @DisplayName("DFS-027.AC-1, DFS-027.AC-3 macOS metadata files are left out by default and counted")
    void metadataFilesAreLeftOutAndCounted() throws IOException {
        Path media = mediaFolder();

        Run run = scan(media.toString());

        assertEquals(0, run.exitCode, run.err);
        List<JsonNode> lines = run.jsonLines();
        assertEquals(2, lines.size(), run.out);
        assertEquals(List.of(real(media.resolve("a/photo.jpg")), real(media.resolve("b/photo.jpg"))),
                setPaths(lines.getFirst()));
        assertFalse(run.out.contains("._photo.jpg"), run.out);
        assertFalse(run.out.contains(".DS_Store"), run.out);
        JsonNode summary = lines.getLast();
        assertEquals("summary", summary.get("type").asString());
        assertEquals(4, summary.get("excluded").asLong());
        assertEquals(0, summary.get("skipped").asLong());
    }

    @Test
    @DisplayName("DFS-027.AC-3 The text report ends with the excluded count")
    void textReportEndsWithExcludedCount() throws IOException {
        Path media = mediaFolder();

        Run run = scan(media.toString(), "--format=text");

        assertEquals(0, run.exitCode, run.err);
        assertEquals("Summary: 1 sets, 2 files, 6.0 KB reclaimable, 0 skipped, 4 excluded", run.textBody().getLast());
    }

    @Test
    @DisplayName("DFS-027.AC-1 The match is case-sensitive, so .ds_store and .DS_STORE are scanned")
    void matchIsCaseSensitive() throws IOException {
        Path media = mediaFolder();
        // Delete first and recreate under the new name: Files.move is a no-op on a case-insensitive filesystem.
        Files.delete(media.resolve("a/.DS_Store"));
        Files.delete(media.resolve("b/.DS_Store"));
        write(media.resolve("a/.ds_store"), "D".repeat(6 * 1024));
        write(media.resolve("b/.DS_STORE"), "D".repeat(6 * 1024));

        Run run = scan(media.toString());

        List<String> expected = List.of(real(media.resolve("a/.ds_store")), real(media.resolve("b/.DS_STORE")));
        assertTrue(setLines(run).stream().anyMatch(l -> setPaths(l).equals(expected)), run.out);
        assertEquals(2, run.jsonLines().getLast().get("excluded").asLong());
    }

    @Test
    @DisplayName("DFS-027.AC-1 Only a name starting with ._ or exactly .DS_Store matches")
    void onlyExactNamesMatch() throws IOException {
        Path media = mediaFolder();
        for (String dir : List.of("a", "b")) {
            write(media.resolve(dir).resolve("x._photo.jpg"), "X".repeat(5 * 1024));
            write(media.resolve(dir).resolve(".DS_Store.bak"), "Y".repeat(5 * 1024));
        }

        Run run = scan(media.toString());

        List<JsonNode> sets = setLines(run);
        assertEquals(3, sets.size(), run.out);
        assertTrue(sets.stream().anyMatch(l -> setPaths(l).stream().allMatch(p -> p.endsWith("x._photo.jpg"))), run.out);
        assertTrue(sets.stream().anyMatch(l -> setPaths(l).stream().allMatch(p -> p.endsWith(".DS_Store.bak"))), run.out);
        assertEquals(4, run.jsonLines().getLast().get("excluded").asLong());
    }

    @Test
    @DisplayName("DFS-027.AC-2, DFS-027.AC-3 --no-default-excludes scans metadata files like any other")
    void noDefaultExcludesScansMetadataFiles() throws IOException {
        Path media = mediaFolder();

        Run run = scan(media.toString(), "--no-default-excludes");

        assertEquals(0, run.exitCode, run.err);
        List<JsonNode> sets = setLines(run);
        assertEquals(3, sets.size(), run.out);
        assertTrue(sets.stream().anyMatch(l -> setPaths(l).stream().allMatch(p -> p.endsWith("._photo.jpg"))), run.out);
        assertTrue(sets.stream().anyMatch(l -> setPaths(l).stream().allMatch(p -> p.endsWith(".DS_Store"))), run.out);
        assertEquals(0, run.jsonLines().getLast().get("excluded").asLong());
    }

    @Test
    @DisplayName("DFS-027.AC-3 Nothing matched gives an excluded count of 0, in both formats")
    void nothingMatchedGivesZeroExcluded() throws IOException {
        Path plain = Files.createDirectories(tmp.resolve("plain"));
        for (String dir : List.of("a", "b")) {
            Files.createDirectories(plain.resolve(dir));
            write(plain.resolve(dir).resolve("photo.jpg"), "P".repeat(6 * 1024));
        }

        Run json = scan(plain.toString());
        Run text = scan(plain.toString(), "--format=text");

        assertEquals(0, json.jsonLines().getLast().get("excluded").asLong());
        assertTrue(text.out.lines().toList().getLast().endsWith(", 0 skipped, 0 excluded"), text.out);
    }

    @Test
    @DisplayName("DFS-027.AC-2 --no-default-excludes given twice is a usage error and starts no scan")
    void noDefaultExcludesTwiceIsUsageError() {
        FakePorts fake = new FakePorts();

        Run run = run(fake, tmp.toString(), "--no-default-excludes", "--no-default-excludes");

        assertEquals(2, run.exitCode);
        assertTrue(run.err.contains("--no-default-excludes given more than once"), run.err);
        assertTrue(run.err.contains(ScanCommand.USAGE_LINE), run.err);
        assertTrue(ScanCommand.USAGE_LINE.contains("--no-default-excludes"), ScanCommand.USAGE_LINE);
        assertEquals("", run.out);
        assertEquals(0, fake.starts);
    }

    @Test
    @DisplayName("DFS-027.AC-2 --no-default-excludes reaches the scanner as a config with the flag off")
    void flagReachesTheScanConfig() {
        FakePorts withFlag = new FakePorts(snapshot(ScanStatus.COMPLETED, null));
        FakePorts without = new FakePorts(snapshot(ScanStatus.COMPLETED, null));

        Run off = run(withFlag, tmp.toString(), "--no-default-excludes");
        Run on = run(without, tmp.toString());

        assertEquals(0, off.exitCode, off.err);
        assertEquals(0, on.exitCode, on.err);
        assertFalse(withFlag.config.defaultExcludes());
        assertTrue(without.config.defaultExcludes());
    }

    @Test
    @DisplayName("DFS-027.AC-3 The summary reports the excluded count of the scan result")
    void summaryReportsResultCount() {
        FakePorts fake = new FakePorts(snapshot(ScanStatus.COMPLETED, null));
        fake.result = new ScanResult(List.of(), List.of(), List.of(), List.of(), 29565);

        Run run = run(fake, tmp.toString());

        assertEquals(List.of("scan", "summary"),
                run.allJsonLines().stream().map(l -> l.get("type").asString()).toList());
        assertEquals(29565, run.jsonLines().getLast().get("excluded").asLong());
    }

    @Test
    @DisplayName("DFS-027.AC-4 Excluded files still count as found in the walk progress")
    void excludedFilesCountInWalkingLine() throws IOException {
        Path media = mediaFolder();

        Run run = scan(media.toString(), "--progress=log");

        assertEquals(0, run.exitCode, run.err);
        List<String> walking = run.err.lines().filter(l -> l.startsWith("Walking:")).toList();
        assertTrue(walking.getLast().contains("6 files, 32.0 KB found"), run.err);
    }

    @Test
    @DisplayName("DFS-027.AC-6 An empty metadata file is counted as excluded, not dropped as too small")
    void emptyMetadataFilesAreCounted() throws IOException {
        Path media = mediaFolder();
        write(media.resolve("a/.DS_Store"), "");
        write(media.resolve("b/.DS_Store"), "");
        Files.createDirectories(media.resolve("c"));
        write(media.resolve("c/._empty"), "");

        Run run = scan(media.toString());

        assertEquals(5, run.jsonLines().getLast().get("excluded").asLong(), run.out);
    }

    @Test
    @DisplayName("DFS-027.AC-6 With --no-default-excludes an empty metadata file is dropped by size and counted nowhere")
    void emptyMetadataFilesWithFlagOffAreDropped() throws IOException {
        Path media = mediaFolder();
        write(media.resolve("a/.DS_Store"), "");
        write(media.resolve("b/.DS_Store"), "");

        Run run = scan(media.toString(), "--no-default-excludes");

        assertEquals(0, run.exitCode, run.err);
        assertFalse(run.out.contains(".DS_Store"), run.out);
        assertEquals(0, run.jsonLines().getLast().get("excluded").asLong());
    }

    // --- DFS-029: --output

    /** The Background: "Café" holds two files with the same 2,000 bytes, and "out" is an empty folder. */
    private Path cafe() throws IOException {
        Path cafe = Files.createDirectories(tmp.resolve("Café"));
        String content = "x".repeat(2_000);
        write(cafe.resolve("a.txt"), content);
        write(cafe.resolve("b.txt"), content);
        Files.createDirectories(tmp.resolve("out"));
        return cafe;
    }

    @Test
    @DisplayName("DFS-029.AC-1 The text report goes to the file as UTF-8 and not to stdout")
    void textReportGoesToFile() throws IOException {
        Path cafe = cafe();
        Path report = tmp.resolve("out").resolve("report.txt");

        Run plain = scan(cafe.toString(), "--format=text", "--progress=off");
        Run run = scan(cafe.toString(), "--format=text", "--progress=log", "--output=" + report);

        assertEquals(0, run.exitCode, run.err);
        assertEquals("", run.out);
        assertTrue(plain.out.contains(cafe.resolve("a.txt").toString()), plain.out);
        assertEquals(Arrays.toString(plain.out.getBytes(StandardCharsets.UTF_8)),
                Arrays.toString(Files.readAllBytes(report)));
        List<String> err = run.err.lines().toList();
        assertEquals("Report written to " + report.toAbsolutePath(), err.getLast(), run.err);
        assertTrue(err.get(err.size() - 2).startsWith("Done in "), run.err);
        assertEquals(List.of("report.txt"), names(tmp.resolve("out")));
    }

    @Test
    @DisplayName("DFS-029.AC-1 The JSONL report goes to the file")
    void jsonlReportGoesToFile() throws IOException {
        Path cafe = cafe();
        Path report = tmp.resolve("out").resolve("report.jsonl");

        Run run = scan(cafe.toString(), "--progress=log", "--output=" + report);

        assertEquals(0, run.exitCode, run.err);
        assertEquals("", run.out);
        List<JsonNode> lines = new Run(0, Files.readString(report, StandardCharsets.UTF_8), "").allJsonLines();
        assertEquals(List.of("scan", "folder", "set", "summary"),
                lines.stream().map(l -> l.get("type").asString()).toList());
        assertEquals(2, lines.get(2).get("copies").size());
    }

    @Test
    @DisplayName("DFS-029.AC-1 No confirmation line with progress off")
    void noConfirmationWithProgressOff() throws IOException {
        Path cafe = cafe();
        Path report = tmp.resolve("out").resolve("report.jsonl");

        Run run = scan(cafe.toString(), "--progress=off", "--output=" + report);

        assertEquals(0, run.exitCode, run.err);
        assertEquals("", run.err);
        assertEquals("", run.out);
        assertTrue(Files.readString(report, StandardCharsets.UTF_8).contains("\"type\":\"summary\""));
    }

    @Test
    @DisplayName("DFS-029.AC-2 An existing file is replaced")
    void existingFileIsReplaced() throws IOException {
        Path cafe = cafe();
        Path report = write(tmp.resolve("out").resolve("report.txt"), "old report");

        Run run = scan(cafe.toString(), "--format=text", "--progress=off", "--output=" + report);

        assertEquals(0, run.exitCode, run.err);
        String content = Files.readString(report, StandardCharsets.UTF_8);
        assertFalse(content.contains("old report"), content);
        assertTrue(content.contains(cafe.resolve("a.txt").toString()), content);
        assertEquals(List.of("report.txt"), names(tmp.resolve("out")));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"missing", "parent is a file", "folder", "invalid"})
    @DisplayName("DFS-029.AC-3 A bad output location is a usage error before scanning")
    void badOutputLocationIsUsageError(String example) throws IOException {
        Path out = Files.createDirectories(tmp.resolve("out"));
        String output = switch (example) {
            case "missing" -> out.resolve("missing").resolve("report.txt").toString();
            case "parent is a file" -> write(out.resolve("file.txt"), "x").resolve("report.txt").toString();
            case "folder" -> out.toString();
            default -> out + "/a\u0000b.txt";
        };
        List<String> before = names(out);
        FakePorts fake = new FakePorts(snapshot(ScanStatus.COMPLETED, null));

        Run run = run(fake, tmp.toString(), "--progress=log", "--output=" + output);

        assertEquals(2, run.exitCode, run.err);
        List<String> err = run.err.lines().toList();
        assertTrue(err.getFirst().startsWith("error: "), run.err);
        assertEquals(ScanCommand.USAGE_LINE, err.getLast(), run.err);
        assertEquals(2, err.size(), run.err);
        assertEquals(0, fake.starts);
        assertEquals(before, names(out));
    }

    @Test
    @DisplayName("DFS-029.AC-3 A folder where no file can be created is a usage error before scanning")
    void unwritableFolderIsUsageError() throws IOException {
        Path out = Files.createDirectories(tmp.resolve("out"));
        PosixFileAttributeView view = Files.getFileAttributeView(out, PosixFileAttributeView.class);
        assumeTrue(view != null, "no POSIX permissions on this platform");
        Set<PosixFilePermission> original = view.readAttributes().permissions();
        view.setPermissions(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
        try {
            assumeTrue(!Files.isWritable(out), "the current user can write anyway (root)");
            FakePorts fake = new FakePorts(snapshot(ScanStatus.COMPLETED, null));

            Run run = run(fake, tmp.toString(), "--output=" + out.resolve("report.txt"));

            assertEquals(2, run.exitCode, run.err);
            assertTrue(run.err.startsWith("error: cannot create a file in " + out), run.err);
            assertEquals(0, fake.starts);
            assertEquals(List.of(), names(out));
        } finally {
            view.setPermissions(original);
        }
    }

    @Test
    @DisplayName("DFS-029.AC-4, DFS-029.AC-5 Writing fails after the scan")
    void writeFailureKeepsOldReport() throws IOException {
        Path out = Files.createDirectories(tmp.resolve("out"));
        Path report = write(out.resolve("report.txt"), "old report");
        FakePorts fake = new FakePorts(snapshot(ScanStatus.COMPLETED, null));
        ReportFile.Opener failing = file -> new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("disk full");
            }
        };

        Run run = run(new ScanCommand(fake, fake, fake, Duration.ZERO, CLOCK, failing),
                tmp.toString(), "--format=text", "--progress=log", "--output=" + report);

        assertEquals(1, run.exitCode, run.err);
        assertEquals("error: could not write the report to " + report + ": IOException: disk full",
                run.err.lines().toList().getLast(), run.err);
        assertEquals("", run.out);
        assertEquals("old report", Files.readString(report, StandardCharsets.UTF_8));
        assertEquals(List.of("report.txt"), names(out));
    }

    @Test
    @DisplayName("DFS-029.AC-5 A failed scan leaves an existing file and no temporary file")
    void failedScanKeepsOldReport() throws IOException {
        Path out = Files.createDirectories(tmp.resolve("out"));
        Path report = write(out.resolve("report.txt"), "old report");
        FakePorts fake = new FakePorts(snapshot(ScanStatus.FAILED, "java.io.UncheckedIOException: disk gone"));

        Run run = run(fake, tmp.toString(), "--output=" + report);

        assertEquals(1, run.exitCode, run.err);
        assertEquals("old report", Files.readString(report, StandardCharsets.UTF_8));
        assertEquals(List.of("report.txt"), names(out));
    }

    @Test
    @DisplayName("DFS-029.AC-5 A refused scan leaves no file")
    void refusedScanLeavesNoFile() throws IOException {
        Path out = Files.createDirectories(tmp.resolve("out"));

        Run run = scan(tmp.resolve("does-not-exist").toString(), "--output=" + out.resolve("report.txt"));

        assertEquals(1, run.exitCode, run.err);
        assertEquals(List.of(), names(out));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"twice", "empty"})
    @DisplayName("DFS-029.AC-6 --output given twice or empty is a usage error")
    void outputTwiceOrEmpty(String example) throws IOException {
        Path out = Files.createDirectories(tmp.resolve("out"));
        String[] args = example.equals("twice")
                ? new String[] {tmp.toString(), "--output=" + out.resolve("a.txt"), "--output=" + out.resolve("b.txt")}
                : new String[] {tmp.toString(), "--output="};
        FakePorts fake = new FakePorts(snapshot(ScanStatus.COMPLETED, null));

        Run run = run(fake, args);

        assertEquals(2, run.exitCode, run.err);
        String message = example.equals("twice") ? "--output given more than once" : "--output needs a file path";
        assertEquals(List.of("error: " + message, ScanCommand.USAGE_LINE), run.err.lines().toList());
        assertEquals(0, fake.starts);
        assertEquals(List.of(), names(out));
    }

    // --- DFS-030: the complete export

    private static final String START = "2026-10-31T09:12:03Z";
    private static final String FINISH = "2026-10-31T11:47:55Z";

    /** A clock that returns the given instants in turn, then repeats the last: one read per call to instant(). */
    private static Clock clockReading(String... instants) {
        Deque<Instant> queue = new ArrayDeque<>(Arrays.stream(instants).map(Instant::parse).toList());
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return queue.size() > 1 ? queue.pop() : queue.peek();
            }
        };
    }

    private static FakePorts completed(ScanResult result) {
        FakePorts fake = new FakePorts(snapshot(ScanStatus.COMPLETED, null));
        fake.result = result;
        return fake;
    }

    private static ScanResult resultWithRoots(String... roots) {
        return new ScanResult(Arrays.stream(roots).map(Path::of).toList(), List.of(), List.of(), List.of(), 0);
    }

    private static List<String> types(List<JsonNode> lines) {
        return lines.stream().map(l -> l.get("type").asString()).toList();
    }

    @Test
    @DisplayName("DFS-030.AC-7 The JSONL output starts with the scan header")
    void jsonlStartsWithTheScanHeader() {
        FakePorts fake = completed(resultWithRoots("D:\\Photos", "E:\\Backup"));

        Run run = run(fake, clockReading(START, FINISH), tmp.toString());

        assertEquals(0, run.exitCode, run.err);
        assertEquals(JSON.readTree("{\"type\":\"scan\",\"schemaVersion\":2,\"roots\":[\"D:\\\\Photos\",\"E:\\\\Backup\"],"
                + "\"minSize\":1,\"sampleSize\":65536,\"partialHashThreshold\":131072,\"defaultExcludes\":true,"
                + "\"startedAt\":\"2026-10-31T09:12:03Z\",\"finishedAt\":\"2026-10-31T11:47:55Z\"}"),
                run.allJsonLines().getFirst());
    }

    @Test
    @DisplayName("DFS-030.AC-7 The scan header holds the settings the scan ran with")
    void scanHeaderHoldsTheScanSettings() {
        FakePorts fake = completed(resultWithRoots("D:\\Photos"));

        Run run = run(fake, clockReading(START, FINISH), tmp.toString(), "--min-size=10", "--no-default-excludes");

        JsonNode header = run.allJsonLines().getFirst();
        assertEquals("scan", header.get("type").asString());
        assertEquals(10, header.get("minSize").asLong());
        assertFalse(header.get("defaultExcludes").asBoolean());
        assertEquals(2, header.get("schemaVersion").asInt());
    }

    @Test
    @DisplayName("DFS-030.AC-7 The scan header times are read before the scan starts and after it finishes")
    void scanHeaderTimesComeFromTheClockAroundTheScan() {
        FakePorts fake = new FakePorts(snapshot(ScanStatus.RUNNING, null), snapshot(ScanStatus.COMPLETED, null));
        fake.result = resultWithRoots("D:\\Photos");
        Clock clock = clockReading("2026-01-02T03:04:05.5Z", "2026-01-02T05:06:07Z", "2099-01-01T00:00:00Z");

        Run run = run(fake, clock, tmp.toString());

        JsonNode header = run.allJsonLines().getFirst();
        assertEquals("2026-01-02T03:04:05.5Z", header.get("startedAt").asString());
        assertEquals("2026-01-02T05:06:07Z", header.get("finishedAt").asString());
    }

    @Test
    @DisplayName("DFS-030.AC-8 Folder lines follow the header, one per folder total")
    void folderLinesFollowTheHeader() {
        FakePorts fake = completed(new ScanResult(List.of(Path.of("D:\\Photos")), List.of(
                new FolderTotal(Path.of("D:\\Photos\\2019"), 400, 10485760000L),
                new FolderTotal(Path.of("D:\\Photos\\2020"), 3, 900)), List.of(), List.of(), 0));

        Run run = run(fake, tmp.toString());

        List<JsonNode> lines = run.allJsonLines();
        assertEquals(JSON.readTree("{\"type\":\"folder\",\"path\":\"D:\\\\Photos\\\\2019\",\"files\":400,\"bytes\":10485760000}"),
                lines.get(1));
        assertEquals(JSON.readTree("{\"type\":\"folder\",\"path\":\"D:\\\\Photos\\\\2020\",\"files\":3,\"bytes\":900}"),
                lines.get(2));
    }

    @Test
    @DisplayName("DFS-030.AC-9 Each path in a set carries its modification time")
    void eachPathInASetCarriesItsModificationTime() {
        FileEntry a = new FileEntry(Path.of("D:\\a\\x.jpg"), 27, Instant.parse("2019-07-14T10:22:31.4512345Z"));
        FileEntry b = new FileEntry(Path.of("D:\\b\\x.jpg"), 27, Instant.parse("2020-01-02T03:04:05Z"));
        FileEntry link = new FileEntry(Path.of("D:\\b\\link.jpg"), 27, Instant.parse("2020-01-02T03:04:05Z"));
        DuplicateSet set = new DuplicateSet(27, "dg", List.of(a, b, link),
                List.of(new PhysicalCopy(List.of(a)), new PhysicalCopy(List.of(b, link))));
        FakePorts fake = completed(new ScanResult(List.of(), List.of(), List.of(set), List.of(), 0));

        Run run = run(fake, tmp.toString());

        JsonNode setLine = setLines(run).getFirst();
        assertEquals(27, setLine.get("size").asLong());
        assertEquals(JSON.readTree("[[{\"path\":\"D:\\\\a\\\\x.jpg\",\"modified\":\"2019-07-14T10:22:31.4512345Z\"}],"
                + "[{\"path\":\"D:\\\\b\\\\x.jpg\",\"modified\":\"2020-01-02T03:04:05Z\"},"
                + "{\"path\":\"D:\\\\b\\\\link.jpg\",\"modified\":\"2020-01-02T03:04:05Z\"}]]"), setLine.get("copies"));
    }

    @Test
    @DisplayName("DFS-030.AC-10 The line types come in a fixed order and the summary is unchanged")
    void lineTypesComeInAFixedOrder() {
        FileEntry p = new FileEntry(Path.of("p.txt"), 4, Instant.EPOCH);
        FileEntry q = new FileEntry(Path.of("q.txt"), 4, Instant.EPOCH);
        DuplicateSet set = new DuplicateSet(4, "ab", List.of(p, q));
        WalkEvent.Skipped skipped = new WalkEvent.Skipped(Path.of("s.txt"), SkipReason.SYMLINK, "symbolic link");
        FakePorts fake = completed(new ScanResult(List.of(Path.of("root")), List.of(
                new FolderTotal(Path.of("root/a"), 1, 4), new FolderTotal(Path.of("root/b"), 1, 4)),
                List.of(set), List.of(skipped), 7));

        Run run = run(fake, tmp.toString());

        List<JsonNode> lines = run.allJsonLines();
        assertEquals(List.of("scan", "folder", "folder", "set", "skipped", "summary"), types(lines));
        assertEquals(JSON.readTree("{\"type\":\"summary\",\"sets\":1,\"files\":2,\"skipped\":1,"
                + "\"reclaimableBytes\":4,\"excluded\":7}"), lines.getLast());
    }

    @Test
    @DisplayName("DFS-030.AC-10 An empty result still has the header and the summary")
    void anEmptyResultStillHasHeaderAndSummary() {
        FakePorts fake = completed(new ScanResult(List.of(), List.of(), List.of(), List.of(), 0));

        Run run = run(fake, tmp.toString());

        assertEquals(List.of("scan", "summary"), types(run.allJsonLines()));
    }

    @Test
    @DisplayName("DFS-030.AC-11 The text report starts with what was scanned")
    void textReportStartsWithWhatWasScanned() {
        FileEntry p = new FileEntry(Path.of("p.txt"), 4, Instant.EPOCH);
        FileEntry q = new FileEntry(Path.of("q.txt"), 4, Instant.EPOCH);
        FakePorts fake = completed(new ScanResult(List.of(Path.of("D:\\Photos"), Path.of("E:\\Backup")), List.of(),
                List.of(new DuplicateSet(4, "ab", List.of(p, q))), List.of(), 0));

        Run run = run(fake, clockReading(START, FINISH), tmp.toString(), "--format=text");

        List<String> lines = run.out.lines().toList();
        assertEquals("Scan of D:\\Photos, E:\\Backup", lines.get(0));
        assertEquals("  2026-10-31 09:12:03Z to 11:47:55Z, minimum size 1 B, default excludes on", lines.get(1));
        assertTrue(lines.get(2).startsWith("#1  "), run.out);
    }

    @Test
    @DisplayName("DFS-030.AC-11 A scan that crosses midnight shows both dates")
    void aScanThatCrossesMidnightShowsBothDates() {
        FakePorts fake = completed(resultWithRoots("D:\\Photos"));

        Run run = run(fake, clockReading("2026-10-31T23:50:00Z", "2026-11-01T00:20:00Z"), tmp.toString(),
                "--format=text", "--min-size=1048576", "--no-default-excludes");

        List<String> lines = run.out.lines().toList();
        assertEquals("Scan of D:\\Photos", lines.get(0));
        assertEquals("  2026-10-31 23:50:00Z to 2026-11-01 00:20:00Z, minimum size 1.0 MB, default excludes off",
                lines.get(1));
    }

    // --- DFS-052: quarantine folders

    /** A/photo.jpg and a byte-identical copy under .dfs-quarantine/run-1/A/, created at test time. */
    private Path quarantineTree() throws IOException {
        Files.createDirectories(tmp.resolve("A"));
        write(tmp.resolve("A/photo.jpg"), DUP);
        Files.createDirectories(tmp.resolve(".dfs-quarantine/run-1/A"));
        write(tmp.resolve(".dfs-quarantine/run-1/A/photo.jpg"), DUP);
        return tmp.toRealPath().resolve(".dfs-quarantine");
    }

    @Test
    @DisplayName("DFS-052.AC-5 JSON Lines hold a skipped line with reason QUARANTINE and no set line")
    void jsonLinesReportTheQuarantineFolder() throws IOException {
        Path quarantine = quarantineTree();

        Run run = scan(tmp.toString());

        assertEquals(0, run.exitCode, run.err);
        List<JsonNode> lines = run.jsonLines();
        assertEquals(2, lines.size(), run.out);
        JsonNode skipped = lines.get(0);
        assertEquals("skipped", skipped.get("type").asString());
        assertEquals(quarantine.toString(), skipped.get("path").asString());
        assertEquals("QUARANTINE", skipped.get("reason").asString());
        assertEquals("summary", lines.get(1).get("type").asString());
        assertEquals(1, lines.get(1).get("skipped").asInt());
        assertEquals(0, lines.get(1).get("sets").asInt());
    }

    @Test
    @DisplayName("DFS-052.AC-5 The text report lists the quarantine folder under Skipped and counts it")
    void textReportListsTheQuarantineFolder() throws IOException {
        Path quarantine = quarantineTree();

        Run run = scan(tmp.toString(), "--format=text");

        assertEquals(0, run.exitCode, run.err);
        List<String> lines = run.textBody();
        assertTrue(lines.contains("Skipped (1):"), run.out);
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("    " + quarantine + "  QUARANTINE  ")), run.out);
        assertEquals("Summary: 0 sets, 0 files, 0 B reclaimable, 1 skipped, 0 excluded", lines.getLast());
    }

    /**
     * The ticket text says exit 2; the user decided on exit 1 (ScanCommand.ERROR, the code of every
     * refused start, DFS-020.AC-13), see design.md open note 6 and test-plan.md.
     */
    @Test
    @DisplayName("DFS-052.AC-5 A location in a quarantine is refused with exit 1 and the message on stderr")
    void aLocationInAQuarantineIsRefused() throws IOException {
        quarantineTree();
        Path location = tmp.resolve(".dfs-quarantine/run-1");

        Run run = scan(location.toString());

        assertEquals(ScanCommand.ERROR, run.exitCode);
        assertTrue(run.err.startsWith("error: LOCATION_IN_QUARANTINE: "), run.err);
        assertTrue(run.err.contains(location.toString()), run.err);
        assertEquals("", run.out);
    }

    // --- DFS-061: operating-system folders

    private static final String SYSTEM_DETAIL = "operating-system folder, not scanned";

    private static Path put(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        return write(file, content);
    }

    /** drive/docs/photo.jpg, a copy in drive/$RECYCLE.BIN/S-1-5-21-1/ and an unrelated $I file. */
    private Path driveTree() throws IOException {
        Path drive = Files.createDirectories(tmp.resolve("drive"));
        put(drive.resolve("docs/photo.jpg"), DUP);
        Files.createDirectories(drive.resolve("$RECYCLE.BIN/S-1-5-21-1"));
        put(drive.resolve("$RECYCLE.BIN/S-1-5-21-1/$R5Y7A3U.ARQ"), DUP);
        put(drive.resolve("$RECYCLE.BIN/S-1-5-21-1/$I5Y7A3U.ARQ"), "I");
        return drive.toRealPath();
    }

    private static List<JsonNode> ofType(Run run, String type) {
        return run.jsonLines().stream().filter(l -> l.get("type").asString().equals(type)).toList();
    }

    @Test
    @DisplayName("DFS-061.AC-1, DFS-061.AC-2 A recycle bin copy is not reported as a duplicate")
    void aRecycleBinCopyIsNotReportedAsADuplicate() throws IOException {
        Path drive = driveTree();

        Run run = scan(drive.toString());

        assertEquals(0, run.exitCode, run.err);
        assertEquals(List.of(), ofType(run, "set"));
        List<JsonNode> skipped = ofType(run, "skipped");
        assertEquals(1, skipped.size(), run.out);
        assertEquals(drive.resolve("$RECYCLE.BIN").toString(), skipped.getFirst().get("path").asString());
        assertEquals("SYSTEM_FOLDER", skipped.getFirst().get("reason").asString());
        assertEquals(SYSTEM_DETAIL, skipped.getFirst().get("detail").asString());
        assertEquals(1, ofType(run, "summary").getFirst().get("skipped").asInt());
    }

    @Test
    @DisplayName("DFS-061.AC-2 The text report lists the skipped folder")
    void theTextReportListsTheSkippedSystemFolder() throws IOException {
        Path drive = driveTree();

        Run run = scan(drive.toString(), "--format=text");

        assertEquals(0, run.exitCode, run.err);
        List<String> lines = run.textBody();
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("    " + drive.resolve("$RECYCLE.BIN")
                + "  SYSTEM_FOLDER  ")), run.out);
        assertTrue(lines.getLast().endsWith(", 1 skipped, 0 excluded"), run.out);
    }

    @Test
    @DisplayName("DFS-061.AC-2 Files under a skipped folder are not counted as found")
    void filesUnderASkippedSystemFolderAreNotCountedAsFound() throws IOException {
        Path drive = driveTree();

        Run run = scan(drive.toString(), "--progress=log");

        assertEquals(0, run.exitCode, run.err);
        List<String> walking = run.err.lines().filter(l -> l.startsWith("Walking:")).toList();
        assertTrue(walking.getLast().contains(" 1 files, 15 B found"), run.err);
    }

    @Test
    @DisplayName("DFS-061.AC-1 A known folder is skipped at any depth and look-alikes are scanned")
    void aKnownFolderIsSkippedAtAnyDepth() throws IOException {
        Path drive = Files.createDirectories(tmp.resolve("drive")).toRealPath();
        put(drive.resolve("deep/er/.Trashes/x.bin"), DUP);
        put(drive.resolve("docs/x.bin"), DUP);
        put(drive.resolve("my.Trash/y.bin"), "other");
        put(drive.resolve("docs/y.bin"), "other");

        Run run = scan(drive.toString());

        assertEquals(0, run.exitCode, run.err);
        assertEquals(List.of(drive.resolve("deep/er/.Trashes").toString()),
                ofType(run, "skipped").stream().map(s -> s.get("path").asString()).toList());
        List<JsonNode> sets = ofType(run, "set");
        assertEquals(1, sets.size(), run.out);
        assertEquals(List.of(drive.resolve("docs/y.bin").toString(), drive.resolve("my.Trash/y.bin").toString()),
                setPaths(sets.getFirst()).stream().sorted().toList());
    }

    @Test
    @DisplayName("DFS-061.AC-3 With --no-default-excludes the folders are scanned")
    void noDefaultExcludesScansTheSystemFolders() throws IOException {
        Path drive = driveTree();

        Run run = scan(drive.toString(), "--no-default-excludes");

        assertEquals(0, run.exitCode, run.err);
        List<JsonNode> sets = ofType(run, "set");
        assertEquals(1, sets.size(), run.out);
        assertEquals(List.of(drive.resolve("$RECYCLE.BIN/S-1-5-21-1/$R5Y7A3U.ARQ").toString(),
                        drive.resolve("docs/photo.jpg").toString()),
                setPaths(sets.getFirst()).stream().sorted().toList());
        assertEquals(List.of(), ofType(run, "skipped"));
    }

    @Test
    @DisplayName("DFS-061.AC-4 A location inside a system folder is walked")
    void aLocationInsideASystemFolderIsWalked() throws IOException {
        Path drive = driveTree();
        put(drive.resolve("$RECYCLE.BIN/S-1-5-21-1/copy/$R5Y7A3U.ARQ"), DUP);

        Run run = scan(drive.resolve("$RECYCLE.BIN/S-1-5-21-1").toString());

        assertEquals(0, run.exitCode, run.err);
        assertEquals(1, ofType(run, "set").size(), run.out);
        assertEquals(List.of(), ofType(run, "skipped"));
    }

    @Test
    @DisplayName("DFS-061.AC-4 A location named like a system folder is walked, folders below it are skipped")
    void aLocationNamedLikeASystemFolderIsWalked() throws IOException {
        Path drive = driveTree();
        put(drive.resolve("$RECYCLE.BIN/System Volume Information/x.bin"), "xx");
        put(drive.resolve("$RECYCLE.BIN/S-1-5-21-1/x.bin"), "xx");
        put(drive.resolve("$RECYCLE.BIN/S-1-5-21-1/y.bin"), "xx");

        Run run = scan(drive.resolve("$RECYCLE.BIN").toString());

        assertEquals(0, run.exitCode, run.err);
        assertEquals(List.of(drive.resolve("$RECYCLE.BIN/System Volume Information").toString()),
                ofType(run, "skipped").stream().map(s -> s.get("path").asString()).toList());
        assertEquals(1, ofType(run, "set").size(), run.out);
    }

    @Test
    @DisplayName("DFS-061.AC-5 The quarantine folder keeps reason QUARANTINE with or without default excludes")
    void quarantineKeepsItsReason() throws IOException {
        Path quarantine = quarantineTree();

        for (String[] args : new String[][]{{tmp.toString()}, {tmp.toString(), "--no-default-excludes"}}) {
            Run run = scan(args);

            assertEquals(0, run.exitCode, run.err);
            List<JsonNode> skipped = ofType(run, "skipped");
            assertEquals(1, skipped.size(), run.out);
            assertEquals(quarantine.toString(), skipped.getFirst().get("path").asString());
            assertEquals("QUARANTINE", skipped.getFirst().get("reason").asString());
        }
    }

    @Test
    @DisplayName("DFS-061.AC-6 A regular file with a known name is scanned")
    void aRegularFileWithAKnownNameIsScanned() throws IOException {
        Path drive = Files.createDirectories(tmp.resolve("drive")).toRealPath();
        put(drive.resolve("a/lost+found"), DUP);
        put(drive.resolve("b/lost+found"), DUP);

        Run run = scan(drive.toString());

        assertEquals(0, run.exitCode, run.err);
        List<JsonNode> sets = ofType(run, "set");
        assertEquals(1, sets.size(), run.out);
        assertEquals(2, setPaths(sets.getFirst()).size());
        assertEquals(List.of(), ofType(run, "skipped"));
    }

    @Test
    @DisplayName("DFS-061.AC-6 A symbolic link with a known name keeps its SYMLINK reason")
    void aSymlinkWithAKnownNameKeepsItsReason() throws IOException {
        Path drive = driveTree();
        Path link = drive.resolve(".Trashes");
        try {
            Files.createSymbolicLink(link, drive.resolve("docs"));
        } catch (UnsupportedOperationException | IOException e) {
            abort("symbolic links are not available here: " + e.getMessage());
        }

        Run run = scan(drive.toString());

        assertEquals(0, run.exitCode, run.err);
        List<JsonNode> skipped = ofType(run, "skipped");
        JsonNode linkLine = skipped.stream().filter(s -> s.get("path").asString().equals(link.toString()))
                .findFirst().orElseThrow();
        assertEquals("SYMLINK", linkLine.get("reason").asString());
    }

    // --- helpers

    private Run scan(String... args) {
        ScanUseCases ports = ScannerFactories.scanUseCases(executor);
        return run(new ScanCommand(ports.start(), ports.status(), ports.result(), Duration.ofMillis(10), CLOCK), args);
    }

    private Run run(FakePorts fake, String... args) {
        return run(fake, CLOCK, args);
    }

    private Run run(FakePorts fake, Clock clock, String... args) {
        return run(new ScanCommand(fake, fake, fake, Duration.ZERO, clock), args);
    }

    private static Run run(ScanCommand command, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = command.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Run(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private record Run(int exitCode, String out, String err) {

        /** The set, skipped and summary lines: every stdout line but the scan header and the folder totals (DFS-030). */
        List<JsonNode> jsonLines() {
            return allJsonLines().stream()
                    .filter(l -> !l.get("type").asString().equals("scan") && !l.get("type").asString().equals("folder"))
                    .toList();
        }

        /** The text report without its two header lines (DFS-030 decision 8). */
        List<String> textBody() {
            return out.lines().skip(2).toList();
        }

        /** Every stdout line, each asserted to parse as exactly one JSON object. */
        List<JsonNode> allJsonLines() {
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
            copy.forEach(link -> links.add(link.get("path").asString()));
            copies.add(links);
        }
        return copies;
    }

    private static Path write(Path file, String content) throws IOException {
        return Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static List<String> names(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).sorted().toList();
        }
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
        ScanResult result = new ScanResult(List.of(), List.of(), List.of(), List.of(), 0);
        List<ScanEvent> events = List.of();
        int starts;
        ScanConfig config;
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
            this.config = config;
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
