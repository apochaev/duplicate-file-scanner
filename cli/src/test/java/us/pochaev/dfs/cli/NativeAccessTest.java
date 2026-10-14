package us.pochaev.dfs.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import us.pochaev.dfs.scanner.domain.factory.ScannerFactories;

import java.io.IOException;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain test (no story ID) of the native-access settings for the scanner's Windows file-ID lookup:
 * the cli test JVM allows native access, and the bootJar manifest carries the attribute.
 */
class NativeAccessTest {

    @Test
    @DisplayName("The cli test JVM has native access enabled for the scanner")
    void cliTestJvmHasNativeAccessEnabled() {
        assertTrue(ScannerFactories.class.getModule().isNativeAccessEnabled());
    }

    @Test
    @DisplayName("The bootJar manifest carries Enable-Native-Access: ALL-UNNAMED")
    void bootJarManifestEnablesNativeAccess() throws IOException {
        String jar = System.getProperty("dfs.bootJar");
        assertNotNull(jar, "system property dfs.bootJar is not set");
        try (JarFile jarFile = new JarFile(jar)) {
            assertEquals("ALL-UNNAMED", jarFile.getManifest().getMainAttributes().getValue("Enable-Native-Access"));
        }
    }
}
