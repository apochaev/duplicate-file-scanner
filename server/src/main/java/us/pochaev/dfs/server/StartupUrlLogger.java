package us.pochaev.dfs.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/** Logs the URL to open once the web server has bound its port (DFS-035.AC-3). */
@Component
class StartupUrlLogger implements ApplicationListener<WebServerInitializedEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(StartupUrlLogger.class);

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        LOG.info("DFS server listening on http://127.0.0.1:{}/", event.getWebServer().getPort());
    }
}
