package us.pochaev.dfs.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultApplicationArguments;
import us.pochaev.dfs.scanner.domain.factory.ScanUseCases;
import us.pochaev.dfs.scanner.domain.factory.ScannerFactories;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.abort;

/**
 * {@link ScanRunner}'s stdout encoding (DFS-023 decision 7): {@code System.out} is replaced by a
 * stream that keeps raw bytes, so the test checks what reaches the process's stdout, not a string
 * a capture has already decoded.
 */
class ScanRunnerTest {

    /** "фото" (Cyrillic), outside Cp1252 and most other single-byte code pages. */
    private static final String FOLDER = "фото";

    @TempDir
    Path tmp;

    private final PrintStream systemOut = System.out;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void restore() throws InterruptedException {
        System.setOut(systemOut);
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @ParameterizedTest(name = "[{index}] options: {0}")
    @ValueSource(strings = {"", "--format=text"})
    @DisplayName("DFS-023.AC-8 Non-ASCII paths are written as UTF-8")
    void nonAsciiPathsAreUtf8(String option) throws IOException {
        Path folder;
        try {
            folder = Files.createDirectories(tmp.resolve(FOLDER));
        } catch (InvalidPathException e) {
            abort("the file system cannot name a folder " + FOLDER + ": " + e.getMessage());
            return;
        }
        Files.writeString(folder.resolve("x.txt"), "hello duplicate");
        Files.writeString(folder.resolve("y.txt"), "hello duplicate");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        // a single-byte charset without Cyrillic, like the Cp1252 console stdout this machine has
        // outside a test (Gradle's test JVM gives System.out UTF-8, which would hide the bug)
        System.setOut(new PrintStream(bytes, true, StandardCharsets.ISO_8859_1));
        ScanUseCases ports = ScannerFactories.scanUseCases(executor);
        ScanRunner runner = new ScanRunner(ports.start(), ports.status(), ports.result());

        String[] args = option.isEmpty() ? new String[] {folder.toString()} : new String[] {folder.toString(), option};
        runner.run(new DefaultApplicationArguments(args));

        assertEquals(0, runner.getExitCode());
        String out;
        try {
            out = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (CharacterCodingException e) {
            throw new AssertionError("stdout is not valid UTF-8", e);
        }
        assertTrue(out.contains(FOLDER), out);
        assertFalse(out.contains("?"), out);
    }
}
