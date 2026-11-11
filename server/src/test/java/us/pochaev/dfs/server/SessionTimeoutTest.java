package us.pochaev.dfs.server;

import org.apache.catalina.Context;
import org.apache.catalina.Container;
import org.apache.catalina.Service;
import org.apache.catalina.Engine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTimeoutTest {

    @Test
    @DisplayName("DFS-066.AC-1 The running server's sessions never expire from idleness")
    void sessionsNeverExpireFromIdleness() {
        try (var context = SpringApplication.run(Main.class, "--dfs.server.open-browser=false")) {
            var tomcat = ((TomcatWebServer) ((WebServerApplicationContext) context).getWebServer()).getTomcat();
            Service service = tomcat.getService();
            Engine engine = service.getContainer();
            Context web = null;
            for (Container host : engine.findChildren()) {
                for (Container child : host.findChildren()) {
                    if (child instanceof Context c) {
                        web = c;
                    }
                }
            }
            assertNotNull(web, "no web application context in the running Tomcat");
            // Tomcat counts minutes; zero or negative means "never expires". Spring Boot's default is 30.
            assertTrue(web.getSessionTimeout() <= 0,
                    "session timeout is " + web.getSessionTimeout() + " minutes, expected zero or negative (never)");
        }
    }
}
