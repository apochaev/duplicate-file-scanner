package us.pochaev.dfs.server.scan;

import jakarta.annotation.PreDestroy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The progress streams (DFS-040 decision 3). A stream sends the current progress when it opens, then, on a shared
 * tick, the progress again whenever it changed and at least 250 ms passed since the last event, and finally the
 * terminal progress, after which it ends. Reconnecting simply opens a new stream: there is nothing to replay.
 */
@Component
class ScanEventStreams {

    static final long INTERVAL_MILLIS = 250;

    private final ScanRegistry registry;
    private final Set<Subscription> subscriptions = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "dfs-scan-events");
        t.setDaemon(true);
        return t;
    });

    ScanEventStreams(ScanRegistry registry) {
        this.registry = registry;
        ticker.scheduleAtFixedRate(this::tick, INTERVAL_MILLIS / 5, INTERVAL_MILLIS / 5, TimeUnit.MILLISECONDS);
    }

    /** Opens a stream for a known scan; the first event is sent before this returns. */
    SseEmitter open(String id, ScanProgress current) {
        SseEmitter emitter = new SseEmitter(0L);
        Subscription s = new Subscription(id, emitter);
        if (!s.send(current)) {
            return emitter;
        }
        if (ScanRegistry.terminal(current)) {
            emitter.complete();
            return emitter;
        }
        emitter.onCompletion(() -> subscriptions.remove(s));
        emitter.onTimeout(() -> subscriptions.remove(s));
        emitter.onError(e -> subscriptions.remove(s));
        subscriptions.add(s);
        return emitter;
    }

    private void tick() {
        long now = System.nanoTime();
        for (Subscription s : subscriptions) {
            try {
                if (now - s.lastSentAt < TimeUnit.MILLISECONDS.toNanos(INTERVAL_MILLIS)) {
                    continue;
                }
                ScanProgress p = registry.progress(s.id).orElse(null);
                if (p == null || p.equals(s.lastSent)) {
                    continue;
                }
                if (s.send(p) && ScanRegistry.terminal(p)) {
                    subscriptions.remove(s);
                    s.emitter.complete();
                }
            } catch (RuntimeException e) {
                subscriptions.remove(s);
                s.emitter.completeWithError(e);
            }
        }
    }

    @PreDestroy
    void close() {
        ticker.shutdownNow();
        subscriptions.forEach(s -> s.emitter.complete());
        subscriptions.clear();
    }

    private final class Subscription {

        final String id;
        final SseEmitter emitter;
        volatile ScanProgress lastSent;
        volatile long lastSentAt;

        Subscription(String id, SseEmitter emitter) {
            this.id = Objects.requireNonNull(id);
            this.emitter = emitter;
        }

        /** Sends one progress event; on a broken connection drops the subscription and returns false. */
        boolean send(ScanProgress p) {
            try {
                emitter.send(SseEmitter.event().name("progress").data(p, MediaType.APPLICATION_JSON));
                lastSent = p;
                lastSentAt = System.nanoTime();
                return true;
            } catch (IOException | IllegalStateException e) {
                subscriptions.remove(this);
                emitter.completeWithError(e);
                return false;
            }
        }
    }
}
