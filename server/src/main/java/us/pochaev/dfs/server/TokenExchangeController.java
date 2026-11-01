package us.pochaev.dfs.server;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trades the printed token for a session cookie (DFS-036 decision 3): a valid token starts a new authenticated
 * session and redirects to "/", so the token leaves the address bar before any script runs; any other token gets 403.
 */
@RestController
class TokenExchangeController {

    private static final Resource PAGE = new ClassPathResource("static/index.html");

    private final AccessToken token;
    private final SecurityContextRepository contexts;

    TokenExchangeController(AccessToken token, SecurityContextRepository contexts) {
        this.token = token;
        this.contexts = contexts;
    }

    /**
     * Without a token, serves the public page (decision 2). A mapping restricted to {@code params = "token"} would
     * answer a plain {@code GET /} with 400 instead of falling through to the static page, so "/" is handled here.
     */
    @GetMapping("/")
    ResponseEntity<?> exchange(@RequestParam(name = "token", required = false) String candidate,
                               HttpServletRequest request, HttpServletResponse response) {
        if (candidate == null) {
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(PAGE);
        }
        if (!token.matches(candidate)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Open the link printed in the console.");
        }
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        request.getSession(true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "local-user", null, AuthorityUtils.createAuthorityList("ROLE_USER")));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, "/").build();
    }
}
