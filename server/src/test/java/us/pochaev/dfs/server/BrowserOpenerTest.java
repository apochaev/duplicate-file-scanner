package us.pochaev.dfs.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(OutputCaptureExtension.class)
class BrowserOpenerTest {

    static final List<String> OPENED = new CopyOnWriteArrayList<>();

    @Configuration
    static class RecordingOpener {
        @Bean
        @Primary
        BrowserOpener recordingBrowserOpener() {
            return OPENED::add;
        }
    }

    @Configuration
    static class FailingOpener {
        @Bean
        @Primary
        BrowserOpener failingBrowserOpener() {
            return url -> {
                throw new IllegalStateException("no browser here");
            };
        }
    }

    @Test
    @DisplayName("DFS-036.AC-7 The browser is opened once with the printed link")
    void browserIsOpenedWithThePrintedLink(CapturedOutput output) {
        OPENED.clear();
        try (TestServer server = TestServer.start(new Class<?>[] {RecordingOpener.class},
                "--dfs.server.open-browser=true")) {
            String link = "http://127.0.0.1:" + server.port() + "/?token=" + server.token(output.getOut());

            assertEquals(List.of(link), OPENED);
        }
    }

    @Test
    @DisplayName("DFS-036.AC-7 With the switch off the browser is not opened")
    void switchKeepsTheBrowserClosed() {
        OPENED.clear();
        try (TestServer server = TestServer.start(new Class<?>[] {RecordingOpener.class})) {
            assertEquals(List.of(), OPENED);
        }
    }

    @Test
    @DisplayName("DFS-036.AC-7 A failing opener logs one warning and the server keeps serving")
    void failingOpenerDoesNotStopTheServer(CapturedOutput output) throws Exception {
        try (TestServer server = TestServer.start(new Class<?>[] {FailingOpener.class},
                "--dfs.server.open-browser=true")) {
            long warnings = output.getOut().lines()
                    .filter(l -> l.contains("WARN") && l.contains("no browser here")).count();

            assertEquals(1, warnings, "expected one warning naming the failure");
            assertEquals(200, server.get("/", null).statusCode());
        }
    }
}
