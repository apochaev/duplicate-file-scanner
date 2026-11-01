package us.pochaev.dfs.server;

import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;
import java.util.List;

/** Opens the link with java.awt.Desktop where supported, otherwise with the operating system's command (DFS-036 decision 5). */
@Component
class DesktopBrowserOpener implements BrowserOpener {

    @Override
    public void open(String url) throws Exception {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI.create(url));
            return;
        }
        String os = System.getProperty("os.name").toLowerCase();
        List<String> command = os.startsWith("windows") ? List.of("rundll32", "url.dll,FileProtocolHandler", url)
                : os.startsWith("mac") ? List.of("open", url)
                : List.of("xdg-open", url);
        new ProcessBuilder(command).inheritIO().start();
    }
}
