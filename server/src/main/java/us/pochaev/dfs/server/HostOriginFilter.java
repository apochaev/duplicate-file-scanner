package us.pochaev.dfs.server;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Refuses requests that did not come from this server's own page (docs/design/ui.md U10):
 * a Host other than 127.0.0.1:port or localhost:port (DNS rebinding, DFS-036.AC-5), and a state-changing
 * request without a matching Origin (cross-site requests, DFS-036.AC-6). Runs ahead of Spring Security.
 */
class HostOriginFilter extends OncePerRequestFilter {

    private static final Set<String> STATE_CHANGING = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        int port = request.getLocalPort();
        String host = request.getHeader("Host");
        if (host == null || !(host.equals("127.0.0.1:" + port) || host.equals("localhost:" + port))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (STATE_CHANGING.contains(request.getMethod())) {
            String origin = request.getHeader("Origin");
            if (origin == null
                    || !(origin.equals("http://127.0.0.1:" + port) || origin.equals("http://localhost:" + port))) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
