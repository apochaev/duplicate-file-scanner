package us.pochaev.dfs.server;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Records the exception a controller method throws, so {@link TestServer}'s request helpers can rethrow it when the
 * response is {@code 500} (CCW-019 decisions 3 and 5). A stubbed method's
 * {@code UnsupportedOperationException("not implemented: <ID>")} then fails the test itself, which is what the story
 * pipeline's red gate ({@code RedCheck}) looks for, while the request still goes through the real server and its
 * security. Every server started by {@link TestServer} loads this configuration.
 */
@TestConfiguration(proxyBeanMethods = false)
class StubSurfacingConfiguration {

    @Bean
    ServerExceptions serverExceptions() {
        return new ServerExceptions();
    }

    /** Runs first among the resolvers, records the exception and resolves nothing, so normal handling continues. */
    @Bean
    HandlerExceptionResolver recordingExceptionResolver(ServerExceptions exceptions) {
        return new RecordingResolver(exceptions);
    }

    /** The last exception a controller method threw, until a request helper takes it. */
    static final class ServerExceptions {

        private final AtomicReference<Throwable> last = new AtomicReference<>();

        void record(Throwable t) {
            last.set(t);
        }

        /** Returns the recorded exception, if any, and clears it. */
        Throwable take() {
            return last.getAndSet(null);
        }
    }

    private record RecordingResolver(ServerExceptions exceptions) implements HandlerExceptionResolver, Ordered {

        @Override
        public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler,
                                             Exception ex) {
            exceptions.record(ex);
            return null;
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }
}
