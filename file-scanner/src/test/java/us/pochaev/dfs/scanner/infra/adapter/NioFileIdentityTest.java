package us.pochaev.dfs.scanner.infra.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import us.pochaev.dfs.scanner.infra.port.FileIdentity;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@link NioFileIdentity#key} against real files in a {@code @TempDir} (DFS-026; the adapter's whole
 * job is the real filesystem). Each story test calls {@code key} first, before any assumption or
 * assertion, so that it reaches the stub even where it would otherwise skip.
 */
class NioFileIdentityTest {

    @Test
    @DisplayName("DFS-026.AC-3 Linux and macOS use the file key")
    void linuxAndMacOsUseTheFileKey(@TempDir Path dir) throws IOException {
        Path x = dir.resolve("x.bin");
        writeBytes(x, 10 * 1024);
        NioFileIdentity identity = new NioFileIdentity();

        Object keyOfX = identity.key(x);

        Object fileKey = Files.readAttributes(x, BasicFileAttributes.class).fileKey();
        assumeTrue(fileKey != null, "fileKey() is null on this platform (Windows)");
        Path y = dir.resolve("y.bin");
        Fixtures.createHardLinkOrSkip(y, x);
        Path z = dir.resolve("z.bin");
        writeBytes(z, 10 * 1024);
        assertEquals(fileKey, keyOfX);
        assertEquals(keyOfX, identity.key(y));
        assertNotEquals(keyOfX, identity.key(z));
    }

    @Test
    @DisplayName("DFS-026.AC-4 Windows reads the NTFS file ID")
    void windowsReadsTheNtfsFileId(@TempDir Path dir) throws IOException {
        Path x = dir.resolve("x.bin");
        writeBytes(x, 10 * 1024);
        NioFileIdentity identity = new NioFileIdentity();

        Object keyOfX = identity.key(x);

        assumeTrue(Fixtures.isWindows(), "NTFS file IDs are read on Windows only");
        Path y = dir.resolve("y.bin");
        Fixtures.createHardLinkOrSkip(y, x);
        Path z = dir.resolve("z.bin");
        Files.copy(x, z);
        assertEquals(keyOfX, identity.key(y));
        assertNotEquals(keyOfX, identity.key(z));
    }

    @Test
    @DisplayName("DFS-026.AC-6 The port hands out keys")
    void thePortHandsOutKeys(@TempDir Path dir) throws IOException {
        Path x = dir.resolve("x.bin");
        writeBytes(x, 1024);
        NioFileIdentity identity = new NioFileIdentity();

        Object first = identity.key(x);
        Object second = identity.key(x);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        List<String> methods = Arrays.stream(FileIdentity.class.getDeclaredMethods())
                .map(m -> m.getName() + Arrays.toString(m.getParameterTypes()))
                .toList();
        assertEquals(List.of("key[interface java.nio.file.Path]"), methods);
    }

    @Test
    @DisplayName("DFS-026.AC-7 Native access is enabled where the scanner runs")
    void nativeAccessIsEnabledInTheTestJvm(@TempDir Path dir) throws IOException {
        Path x = dir.resolve("x.bin");
        writeBytes(x, 1024);

        new NioFileIdentity().key(x);

        assertTrue(NioFileIdentity.class.getModule().isNativeAccessEnabled());
    }

    @Test
    @DisplayName("DFS-026.AC-8 A refused native call fails loudly")
    void aRefusedNativeCallFailsLoudly(@TempDir Path dir) throws Exception {
        Path x = dir.resolve("x.bin");
        writeBytes(x, 1024);
        new NioFileIdentity().key(x);

        assumeTrue(Fixtures.isWindows(), "native access is used on Windows only");
        Path java = Path.of(ProcessHandle.current().info().command().orElseThrow());
        Process process = new ProcessBuilder(
                        java.toString(),
                        "--illegal-native-access=deny",
                        "-cp", System.getProperty("java.class.path"),
                        NativeAccessProbe.class.getName(),
                        x.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "child JVM did not finish");

        assertTrue(output.contains("java.lang.IllegalStateException"), output);
        assertTrue(output.contains("--enable-native-access"), output);
    }

    /** Writes {@code size} bytes of a repeating pattern. Generated at test time; never committed. */
    private static void writeBytes(Path path, int size) throws IOException {
        try (OutputStream out = Files.newOutputStream(path)) {
            for (int i = 0; i < size; i++) {
                out.write((byte) i);
            }
        }
    }
}
