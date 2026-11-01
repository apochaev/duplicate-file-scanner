package us.pochaev.dfs.server;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The spec's header (DFS-038 decision 5). The server URL is empty, meaning the page's own origin: that keeps the random
 * port out of the committed {@code server/frontend/openapi.json}, and the generated client calls {@code /api/...}
 * ("/" would make it call {@code //api/...}). springdoc serves the spec only when
 * {@code springdoc.api-docs.enabled=true}, which only {@code OpenApiSpecTest} and {@code :server:updateOpenApi} set
 * (decision 3).
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    @Bean
    OpenAPI dfsOpenApi() {
        return new OpenAPI()
                .info(new Info().title("DFS server API").version("1"))
                .addServersItem(new Server().url(""));
    }
}
