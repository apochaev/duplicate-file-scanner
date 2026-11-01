package us.pochaev.dfs.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Angular app built by Gradle, packaged into the jar and served publicly behind the Host check (DFS-037). */
class FrontendTest {

    private static final String STATIC = "BOOT-INF/classes/static/";
    private static final Pattern REFERENCE = Pattern.compile("(?:src|href)=\"([^\"]+)\"");

    /** Local files an index.html refers to: every src/href except the base "/" and absolute URLs. */
    static List<String> references(String indexHtml) {
        return REFERENCE.matcher(indexHtml).results()
                .map(m -> m.group(1))
                .filter(r -> !r.equals("/") && !r.contains("://") && !r.startsWith("data:"))
                .map(r -> r.startsWith("/") ? r.substring(1) : r)
                .toList();
    }

    static List<String> scriptsAndStyles(String indexHtml) {
        return references(indexHtml).stream().filter(r -> r.endsWith(".js") || r.endsWith(".css")).toList();
    }

    @Test
    @DisplayName("DFS-037.AC-1 dfs-server.jar holds the Angular index.html and every file it references")
    void jarCarriesTheBuiltApp() throws IOException {
        String jar = System.getProperty("dfs.bootJar");
        assertNotNull(jar, "system property dfs.bootJar is not set");
        try (JarFile jarFile = new JarFile(jar)) {
            ZipEntry index = jarFile.getEntry(STATIC + "index.html");
            assertNotNull(index, "no " + STATIC + "index.html in " + jar);
            String html;
            try (InputStream in = jarFile.getInputStream(index)) {
                html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertTrue(html.contains("<app-root"), html);
            List<String> refs = references(html);
            assertFalse(scriptsAndStyles(html).isEmpty(), "index.html references no JS or CSS: " + html);
            for (String ref : refs) {
                assertNotNull(jarFile.getEntry(STATIC + ref), "index.html references " + ref + ", missing from the jar");
            }
        }
    }

    @Test
    @DisplayName("DFS-037.AC-2 The Angular page and its JS and CSS files are served without a cookie")
    void pageAndAssetsArePublic() throws Exception {
        try (TestServer server = TestServer.start()) {
            HttpResponse<String> page = server.get("/", null);

            assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("<app-root"), page.body());
            assertTrue(page.body().contains("<title>DFS</title>"), page.body());
            List<String> assets = scriptsAndStyles(page.body());
            assertFalse(assets.isEmpty(), "the page references no JS or CSS");
            for (String asset : assets) {
                HttpResponse<String> r = server.get("/" + asset, null);
                assertEquals(200, r.statusCode(), asset);
                String type = r.headers().firstValue("Content-Type").orElse("");
                String expected = asset.endsWith(".js") ? "text/javascript" : "text/css";
                assertTrue(type.startsWith(expected), asset + " has content type " + type);
            }
        }
    }

    @Test
    @DisplayName("DFS-037.AC-2 The app's JS files are behind the Host check")
    void assetsAreBehindTheHostCheck() throws Exception {
        try (TestServer server = TestServer.start()) {
            String script = scriptsAndStyles(server.get("/", null).body()).stream()
                    .filter(r -> r.endsWith(".js")).findFirst()
                    .orElseThrow(() -> new AssertionError("the page references no JS file"));

            assertEquals(403, server.rawGet("/" + script, "evil.example:" + server.port(), null));
            assertEquals(200, server.rawGet("/" + script, "127.0.0.1:" + server.port(), null));
        }
    }
}
