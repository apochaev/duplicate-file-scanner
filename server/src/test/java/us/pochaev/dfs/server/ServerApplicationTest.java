package us.pochaev.dfs.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class ServerApplicationTest {

    private static ConfigurableApplicationContext start() {
        return SpringApplication.run(Main.class, "--dfs.server.open-browser=false");
    }

    private static int port(ConfigurableApplicationContext context) {
        return ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    @Test
    @DisplayName("DFS-035.AC-2 The server binds to 127.0.0.1 on a port chosen at startup")
    void bindsToLoopbackOnAFreshPort() throws IOException {
        try (var first = start(); var second = start()) {
            int a = port(first);
            int b = port(second);
            assertNotEquals(a, b, "two starts got the same port");
            assertNotEquals(8080, a);
            assertNotEquals(8080, b);

            for (var context : List.of(first, second)) {
                assertEquals("127.0.0.1", context.getEnvironment().getProperty("server.address"));
                int port = port(context);
                assertTrue(accepts(InetAddress.getLoopbackAddress(), port), "not reachable on 127.0.0.1:" + port);
                for (InetAddress other : nonLoopbackIpv4Addresses()) {
                    assertFalse(accepts(other, port), "reachable on " + other.getHostAddress() + ":" + port);
                }
            }
        }
    }

    @Test
    @DisplayName("DFS-035.AC-3 Startup logs the URL with the bound port")
    void logsTheUrlWithTheBoundPort(CapturedOutput output) {
        try (var context = start()) {
            String line = "DFS server listening on http://127.0.0.1:" + port(context) + "/";
            long count = output.getOut().lines().filter(l -> l.endsWith(line)).count();
            assertEquals(1, count, "expected one line ending with: " + line);
            assertEquals(1, Pattern.compile("DFS server listening on").matcher(output.getOut()).results().count());
        }
    }

    @Test
    @DisplayName("DFS-035.AC-4 GET / serves the placeholder page")
    void servesPlaceholder() throws IOException, InterruptedException {
        try (var context = start(); HttpClient client = HttpClient.newHttpClient()) {
            String base = "http://127.0.0.1:" + port(context);

            HttpResponse<String> page = client.send(
                    HttpRequest.newBuilder(URI.create(base + "/")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("DFS server is running"), page.body());
            // "/api does not exist" (404) became "/api needs the session cookie" (403): DFS-036.AC-4.
        }
    }

    private static boolean accepts(InetAddress address, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, port), 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** This machine's own non-loopback IPv4 addresses; empty on a machine with none, which checks loopback only. */
    private static List<InetAddress> nonLoopbackIpv4Addresses() throws IOException {
        return NetworkInterface.networkInterfaces()
                .filter(nic -> {
                    try {
                        return nic.isUp() && !nic.isLoopback();
                    } catch (IOException e) {
                        return false;
                    }
                })
                .flatMap(NetworkInterface::inetAddresses)
                .filter(a -> a instanceof Inet4Address && !a.isLoopbackAddress())
                .toList();
    }
}
