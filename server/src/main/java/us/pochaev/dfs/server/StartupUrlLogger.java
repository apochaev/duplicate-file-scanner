package us.pochaev.dfs.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Once the web server has bound its port: logs the listening line (DFS-035.AC-3) and the link with the token
 * (DFS-036.AC-1), and opens that link in the browser unless dfs.server.open-browser is false (DFS-036.AC-7).
 */
@Component
class StartupUrlLogger implements ApplicationListener<WebServerInitializedEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(StartupUrlLogger.class);

    private final AccessToken token;
    private final BrowserOpener opener;
    private final boolean openBrowser;

    StartupUrlLogger(AccessToken token, BrowserOpener opener,
                     @Value("${dfs.server.open-browser:true}") boolean openBrowser) {
        this.token = token;
        this.opener = opener;
        this.openBrowser = openBrowser;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        int port = event.getWebServer().getPort();
        String link = "http://127.0.0.1:" + port + "/?token=" + token.value();
        LOG.info("DFS server listening on http://127.0.0.1:{}/", port);
        LOG.info("Open {}", link);
        if (openBrowser) {
            try {
                opener.open(link);
            } catch (Exception e) {
                LOG.warn("Could not open the browser ({}); open the link above by hand", e.toString());
            }
        }
    }
}
