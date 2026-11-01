package us.pochaev.dfs.server;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Tells the page whether this browser holds a session (DFS-036.AC-4); reachable only with the cookie. */
@RestController
class SessionController {

    @GetMapping("/api/session")
    Map<String, Boolean> session() {
        return Map.of("authenticated", true);
    }
}
