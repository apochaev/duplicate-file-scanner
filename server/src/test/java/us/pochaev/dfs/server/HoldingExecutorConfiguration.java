package us.pochaev.dfs.server;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Replaces the {@code scanExecutor} bean (DFS-031: overridable by name) with one that never runs a task, so every
 * started scan stays QUEUED (DFS-040 decision 7).
 */
@TestConfiguration(proxyBeanMethods = false)
class HoldingExecutorConfiguration {

    @Bean(name = "scanExecutor", destroyMethod = "shutdownNow")
    ExecutorService scanExecutor() {
        return new HoldingExecutor();
    }

    static final class HoldingExecutor extends AbstractExecutorService {

        private final List<Runnable> held = new ArrayList<>();
        private volatile boolean shutdown;

        @Override
        public synchronized void execute(Runnable command) {
            held.add(command);
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public synchronized List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> tasks = List.copyOf(held);
            held.clear();
            return tasks;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
