package us.pochaev.dfs.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NativeAccessTest {

    @Test
    @DisplayName("DFS-035.AC-6 dfs-server.jar's manifest carries Enable-Native-Access: ALL-UNNAMED")
    void bootJarManifestEnablesNativeAccess() throws IOException {
        String jar = System.getProperty("dfs.bootJar");
        assertNotNull(jar, "system property dfs.bootJar is not set");
        assertEquals("dfs-server.jar", java.nio.file.Path.of(jar).getFileName().toString());
        try (JarFile jarFile = new JarFile(jar)) {
            assertEquals("ALL-UNNAMED", jarFile.getManifest().getMainAttributes().getValue("Enable-Native-Access"));
        }
    }
}
