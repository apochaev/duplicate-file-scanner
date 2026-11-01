package us.pochaev.dfs.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A real server on a random port for the security tests, plus the request helpers they share. */
record TestServer(ConfigurableApplicationContext context, int port) implements AutoCloseable {

    static final Pattern OPEN_LINE = Pattern.compile("Open (http://127\\.0\\.0\\.1:(\\d+)/\\?token=([A-Za-z0-9_-]+))");

    /** Starts the server with the browser opener off, plus any extra sources (a fake opener) and arguments. */
    static TestServer start(Class<?>[] extraSources, String... args) {
        List<Class<?>> sources = new ArrayList<>(List.of(Main.class));
        sources.addAll(List.of(extraSources));
        SpringApplication app = new SpringApplication(sources.toArray(Class<?>[]::new));
        List<String> all = new ArrayList<>(List.of("--dfs.server.open-browser=false"));
        all.addAll(List.of(args));
        ConfigurableApplicationContext context = app.run(all.toArray(String[]::new));
        return new TestServer(context, ((WebServerApplicationContext) context).getWebServer().getPort());
    }

    static TestServer start() {
        return start(new Class<?>[0]);
    }

    @Override
    public void close() {
        context.close();
    }

    String base() {
        return "http://127.0.0.1:" + port;
    }

    /** The token from this server's "Open ..." line in the captured output. */
    String token(String output) {
        Matcher m = OPEN_LINE.matcher(output);
        while (m.find()) {
            if (Integer.parseInt(m.group(2)) == port) {
                return m.group(3);
            }
        }
        throw new AssertionError("no 'Open http://127.0.0.1:" + port + "/?token=' line in the output");
    }

    static HttpClient client() {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    }

    HttpResponse<String> get(String path, String cookie) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base() + path)).GET();
        if (cookie != null) {
            b.header("Cookie", cookie);
        }
        try (HttpClient c = client()) {
            return c.send(b.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    HttpResponse<String> send(String method, String path, String cookie, String origin)
            throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base() + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (cookie != null) {
            b.header("Cookie", cookie);
        }
        if (origin != null) {
            b.header("Origin", origin);
        }
        try (HttpClient c = client()) {
            return c.send(b.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    /** Exchanges the token and returns the session cookie as "DFS_SESSION=value". */
    String login(String token) throws IOException, InterruptedException {
        HttpResponse<String> r = get("/?token=" + token, null);
        return sessionCookie(r).orElseThrow(() -> new AssertionError("no DFS_SESSION cookie, status " + r.statusCode()));
    }

    static Optional<String> sessionCookie(HttpResponse<?> response) {
        return setCookie(response).map(h -> h.split(";", 2)[0].trim());
    }

    static Optional<String> setCookie(HttpResponse<?> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(h -> h.startsWith("DFS_SESSION=")).findFirst();
    }

    /**
     * Sends a GET over a plain socket, because {@code HttpClient} does not let a caller set {@code Host}.
     * A null host sends an HTTP/1.0 request without any Host header. Returns the status code.
     */
    int rawGet(String path, String host, String cookie) throws IOException {
        StringBuilder request = new StringBuilder();
        if (host == null) {
            request.append("GET ").append(path).append(" HTTP/1.0\r\n");
        } else {
            request.append("GET ").append(path).append(" HTTP/1.1\r\nHost: ").append(host).append("\r\n");
        }
        if (cookie != null) {
            request.append("Cookie: ").append(cookie).append("\r\n");
        }
        request.append("Connection: close\r\n\r\n");
        try (Socket socket = new Socket("127.0.0.1", port)) {
            OutputStream out = socket.getOutputStream();
            out.write(request.toString().getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String status = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)).readLine();
            return Integer.parseInt(status.split(" ")[1]);
        }
    }
}
