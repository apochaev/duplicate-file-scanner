package us.pochaev.dfs.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class SecurityTest {

    @Test
    @DisplayName("DFS-036.AC-1 Startup prints the link with a URL-safe 128-bit token, and nowhere else")
    void startupPrintsTheLinkWithTheToken(CapturedOutput output) {
        try (TestServer server = TestServer.start()) {
            String token = server.token(output.getOut());
            String link = "Open http://127.0.0.1:" + server.port() + "/?token=" + token;

            assertEquals(1, output.getOut().lines().filter(l -> l.endsWith(link)).count(), "one 'Open' line");
            assertTrue(token.matches("[A-Za-z0-9_-]{22,}"), "token too short or not URL-safe: " + token);
            assertTrue(output.getOut().contains("DFS server listening on http://127.0.0.1:" + server.port() + "/"));
            assertEquals(1, output.toString().lines().filter(l -> l.contains(token)).count(),
                    "the token appears in another log line");
        }
    }

    @Test
    @DisplayName("DFS-036.AC-2 The token is exchanged for an HttpOnly, SameSite=Strict cookie by redirect")
    void tokenIsExchangedByRedirect(CapturedOutput output) throws Exception {
        try (TestServer server = TestServer.start()) {
            HttpResponse<String> r = server.get("/?token=" + server.token(output.getOut()), null);

            assertEquals(302, r.statusCode());
            assertTrue(r.headers().firstValue("Location").orElse("").matches("(http://127\\.0\\.0\\.1:\\d+)?/"),
                    "Location: " + r.headers().firstValue("Location"));
            String cookie = TestServer.setCookie(r).orElseThrow(() -> new AssertionError("no DFS_SESSION cookie"));
            assertTrue(cookie.contains("HttpOnly"), cookie);
            assertTrue(cookie.contains("SameSite=Strict"), cookie);
        }
    }

    @Test
    @DisplayName("DFS-036.AC-2 The token can be used again in the same run and gives a new session")
    void tokenCanBeUsedAgain(CapturedOutput output) throws Exception {
        try (TestServer server = TestServer.start()) {
            String token = server.token(output.getOut());
            String first = server.login(token);

            HttpResponse<String> again = server.get("/?token=" + token, null);
            assertEquals(302, again.statusCode());
            String second = TestServer.sessionCookie(again).orElseThrow(() -> new AssertionError("no cookie"));
            assertNotEquals(first, second);
        }
    }

    @Test
    @DisplayName("DFS-036.AC-3 A wrong token gets 403 and no cookie")
    void wrongTokenGetsNothing() throws Exception {
        try (TestServer server = TestServer.start()) {
            HttpResponse<String> r = server.get("/?token=wrong-token", null);

            assertEquals(403, r.statusCode());
            assertTrue(TestServer.setCookie(r).isEmpty(), "a cookie was set");
        }
    }

    @Test
    @DisplayName("DFS-036.AC-3 The page itself is public")
    void pageIsPublic() throws Exception {
        try (TestServer server = TestServer.start()) {
            HttpResponse<String> r = server.get("/", null);

            assertEquals(200, r.statusCode());
            // The page is the Angular app now; its text is rendered in the browser (DFS-037.AC-7).
            assertTrue(r.body().contains("<app-root"), r.body());
            assertTrue(r.body().contains("<title>DFS</title>"), r.body());
        }
    }

    @Test
    @DisplayName("DFS-036.AC-4 /api/session needs the cookie, and so does every other /api path")
    void sessionEndpointNeedsTheCookie(CapturedOutput output) throws Exception {
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));

            HttpResponse<String> with = server.get("/api/session", cookie);
            assertEquals(200, with.statusCode());
            assertEquals("{\"authenticated\":true}", with.body().replace(" ", ""));
            assertEquals(403, server.get("/api/session", null).statusCode());
            assertEquals(403, server.get("/api/anything", null).statusCode());
        }
    }

    @Test
    @DisplayName("DFS-036.AC-4 A cookie from an earlier server run is refused")
    void cookieFromEarlierRunIsRefused(CapturedOutput output) throws Exception {
        String cookie;
        try (TestServer first = TestServer.start()) {
            cookie = first.login(first.token(output.getOut()));
        }
        try (TestServer second = TestServer.start()) {
            assertEquals(403, second.get("/api/session", cookie).statusCode());
        }
    }

    @ParameterizedTest(name = "DFS-036.AC-5 Host {1} on {0} is refused")
    @CsvSource({"/, evil.example:P", "/api/session, evil.example:P", "/api/session, 127.0.0.1:1", "/, NONE"})
    @DisplayName("DFS-036.AC-5 A foreign Host is refused")
    void foreignHostIsRefused(String path, String host, CapturedOutput output) throws Exception {
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String h = host.equals("NONE") ? null : host.replace("P", String.valueOf(server.port()));

            assertEquals(403, server.rawGet(path, h, cookie));
        }
    }

    @Test
    @DisplayName("DFS-036.AC-5 localhost is accepted as Host")
    void localhostIsAccepted() throws Exception {
        try (TestServer server = TestServer.start()) {
            assertEquals(200, server.rawGet("/", "localhost:" + server.port(), null));
            assertEquals(200, server.rawGet("/", "127.0.0.1:" + server.port(), null));
        }
    }

    @ParameterizedTest(name = "DFS-036.AC-6 POST with Origin {0} gets {1}")
    @CsvSource({"NONE, 403", "http://evil.example, 403", "http://127.0.0.1:1, 403",
            "http://127.0.0.1:P, 405", "http://localhost:P, 405"})
    @DisplayName("DFS-036.AC-6 A state-changing request needs a matching Origin")
    void stateChangingRequestNeedsMatchingOrigin(String origin, int status, CapturedOutput output) throws Exception {
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            String o = origin.equals("NONE") ? null : origin.replace("P", String.valueOf(server.port()));

            assertEquals(status, server.send("POST", "/api/session", cookie, o).statusCode());
        }
    }

    @Test
    @DisplayName("DFS-036.AC-6 PUT, PATCH and DELETE need a matching Origin too")
    void otherStateChangingMethodsNeedMatchingOrigin(CapturedOutput output) throws Exception {
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            for (String method : List.of("PUT", "PATCH", "DELETE")) {
                assertEquals(403, server.send(method, "/api/session", cookie, null).statusCode(), method);
                assertEquals(403, server.send(method, "/api/session", cookie, "http://evil.example").statusCode(),
                        method);
            }
        }
    }

    @Test
    @DisplayName("DFS-036.AC-6 No response carries an Access-Control-Allow-Origin header")
    void noCorsHeaders(CapturedOutput output) throws Exception {
        try (TestServer server = TestServer.start()) {
            String cookie = server.login(server.token(output.getOut()));
            List<HttpResponse<String>> responses = new ArrayList<>();
            var preflight = java.net.http.HttpRequest.newBuilder(java.net.URI.create(server.base() + "/api/session"))
                    .method("OPTIONS", java.net.http.HttpRequest.BodyPublishers.noBody())
                    .header("Origin", "http://evil.example")
                    .header("Access-Control-Request-Method", "POST")
                    .build();
            try (var client = TestServer.client()) {
                responses.add(client.send(preflight, HttpResponse.BodyHandlers.ofString()));
            }
            responses.add(server.get("/", null));
            responses.add(server.get("/api/session", cookie));
            responses.add(server.send("POST", "/api/session", cookie, "http://evil.example"));

            for (HttpResponse<String> r : responses) {
                assertFalse(r.headers().firstValue("Access-Control-Allow-Origin").isPresent(),
                        r.request().method() + " " + r.uri() + " has Access-Control-Allow-Origin");
            }
        }
    }
}
