package us.pochaev.dfs.server;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the page whether this browser holds a session (DFS-036.AC-4); reachable only with the cookie. The tag and
 * operation ID name the generated client's {@code SessionService.getSession()} (DFS-038 decision 5).
 */
@RestController
@Tag(name = "session")
class SessionController {

    @GetMapping(path = "/api/session", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getSession", summary = "Whether this browser holds a session")
    SessionStatus session() {
        return new SessionStatus(true);
    }
}
