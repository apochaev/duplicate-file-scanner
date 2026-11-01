package us.pochaev.dfs.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The OpenAPI spec generated from the controllers, committed as server/frontend/openapi.json (DFS-038 decision 1).
 * The test task compares the live spec with that file; {@code ./gradlew :server:updateOpenApi} runs the same test
 * with {@code dfs.openapi.update=true}, which writes the file instead.
 */
@ExtendWith(OutputCaptureExtension.class)
class OpenApiSpecTest {

    static final String UPDATE_COMMAND = "./gradlew :server:updateOpenApi";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static Path specFile() {
        String file = System.getProperty("dfs.openapi.file");
        assertNotNull(file, "system property dfs.openapi.file is not set");
        return Path.of(file);
    }

    private static JsonNode liveSpec() throws IOException, InterruptedException {
        try (TestServer server = TestServer.start(new Class<?>[0], "--springdoc.api-docs.enabled=true")) {
            HttpResponse<String> r = server.get("/v3/api-docs", null);
            assertEquals(200, r.statusCode(), "GET /v3/api-docs with springdoc switched on: " + r.body());
            return JSON.readTree(r.body());
        }
    }

    /** Fails, naming the update command, when the committed spec is not the live one (compared as JSON). */
    static void assertSpecMatches(JsonNode live, JsonNode committed) {
        if (!live.equals(committed)) {
            throw new AssertionError("server/frontend/openapi.json is not the spec the controllers produce; run "
                    + UPDATE_COMMAND + " and commit the file");
        }
    }

    @Test
    @DisplayName("DFS-038.AC-1 The spec describes GET /api/session and equals the committed openapi.json")
    void committedSpecMatchesTheControllers() throws Exception {
        JsonNode live = liveSpec();

        if (Boolean.getBoolean("dfs.openapi.update")) {
            Files.writeString(specFile(), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(live) + "\n",
                    StandardCharsets.UTF_8);
            return;
        }

        JsonNode get = live.path("paths").path("/api/session").path("get");
        assertFalse(get.isMissingNode(), "no GET /api/session in " + live);
        assertTrue(contains(get.path("tags"), "session"), "tags: " + get.path("tags"));
        assertEquals("getSession", get.path("operationId").asString());
        JsonNode content = get.path("responses").path("200").path("content");
        assertTrue(content.size() > 0, "no 200 content: " + get);
        String ref = content.iterator().next().path("schema").path("$ref").asString();
        assertEquals("#/components/schemas/SessionStatus", ref);
        JsonNode status = live.path("components").path("schemas").path("SessionStatus");
        assertEquals("boolean", status.path("properties").path("authenticated").path("type").asString(), status.toString());
        assertTrue(contains(status.path("required"), "authenticated"), "required: " + status.path("required"));
        for (JsonNode server : live.path("servers")) {
            assertFalse(server.path("url").asString().matches(".*:\\d+.*"), "server URL with a port: " + server);
        }

        Path file = specFile();
        assertTrue(Files.isRegularFile(file), file + " is missing; run " + UPDATE_COMMAND);
        assertSpecMatches(live, JSON.readTree(Files.readString(file, StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("DFS-038.AC-2 A committed spec that differs from the live one fails, naming the update command")
    void staleSpecNamesTheFix() throws Exception {
        JsonNode live = liveSpec();
        ObjectNode stale = (ObjectNode) live.deepCopy();
        ((ObjectNode) stale.path("components").path("schemas").path("SessionStatus").path("properties"))
                .remove("authenticated");

        AssertionError e = assertThrows(AssertionError.class, () -> assertSpecMatches(live, stale));
        assertTrue(e.getMessage().contains(UPDATE_COMMAND), e.getMessage());
    }

    @Test
    @DisplayName("DFS-038.AC-3 A normal run serves no spec, with or without the cookie")
    void noSpecOnANormalRun(CapturedOutput output) throws Exception {
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));

            assertEquals(404, server.get("/v3/api-docs", null).statusCode());
            assertEquals(404, server.get("/v3/api-docs", cookie).statusCode());
            HttpResponse<String> session = server.get("/api/session", cookie);
            assertEquals(200, session.statusCode());
            assertEquals(JSON.readTree("{\"authenticated\":true}"), JSON.readTree(session.body()));
        }
    }

    private static boolean contains(JsonNode array, String value) {
        for (JsonNode n : array) {
            if (value.equals(n.asString())) {
                return true;
            }
        }
        return false;
    }
}
