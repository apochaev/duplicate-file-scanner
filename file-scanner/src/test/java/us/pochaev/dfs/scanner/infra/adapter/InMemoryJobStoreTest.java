package us.pochaev.dfs.scanner.infra.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** The in-memory job store (DFS-042 decision 4): create, find, atomic update, several threads at once. */
class InMemoryJobStoreTest {

    @Test
    @DisplayName("DFS-042.AC-12 Records of different IDs are kept apart, an unknown ID is empty")
    void recordsAreKeptApart() {
        InMemoryJobStore<String, Integer> store = new InMemoryJobStore<>();
        store.create("a", 1);
        store.create("b", 10);

        assertEquals(Optional.of(1), store.find("a"));
        assertEquals(Optional.of(2), store.update("a", n -> n + 1));
        assertEquals(Optional.of(2), store.find("a"));
        assertEquals(Optional.of(10), store.find("b"));
        assertEquals(Optional.empty(), store.find("c"));
    }

    @Test
    @DisplayName("DFS-042.AC-6 Update of an unknown ID applies nothing; creating an ID twice is refused")
    void unknownUpdateAndDuplicateCreate() {
        InMemoryJobStore<String, Integer> store = new InMemoryJobStore<>();
        int[] applied = {0};

        assertEquals(Optional.empty(), store.update("missing", n -> {
            applied[0]++;
            return n;
        }));
        assertEquals(0, applied[0]);
        store.create("a", 1);
        try {
            store.create("a", 2);
            fail("duplicate create");
        } catch (IllegalStateException expected) {
            // expected
        }
        assertEquals(Optional.of(1), store.find("a"));
    }

    @Test
    @DisplayName("DFS-042.AC-6 Null arguments and a null change result throw NullPointerException")
    void nullsAreRejected() {
        InMemoryJobStore<String, Integer> store = new InMemoryJobStore<>();
        try {
            store.create(null, 1);
            fail("null id");
        } catch (NullPointerException expected) {
            // expected
        }
        try {
            store.create("a", null);
            fail("null record");
        } catch (NullPointerException expected) {
            // expected
        }
        store.create("b", 1);
        try {
            store.update("b", n -> null);
            fail("null result");
        } catch (NullPointerException expected) {
            // expected
        }
        try {
            store.find(null);
            fail("null find");
        } catch (NullPointerException expected) {
            // expected
        }
    }

    @Test
    @DisplayName("DFS-042.AC-6 No update is lost when many threads update one record")
    void noUpdateIsLost() throws InterruptedException {
        InMemoryJobStore<String, Integer> store = new InMemoryJobStore<>();
        store.create("a", 0);
        int threads = 8;
        int each = 2000;
        List<Thread> started = new ArrayList<>();
        AtomicInteger failures = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            started.add(Thread.ofVirtual().start(() -> {
                try {
                    for (int j = 0; j < each; j++) {
                        store.update("a", n -> n + 1);
                    }
                } catch (RuntimeException e) {
                    failures.incrementAndGet();
                }
            }));
        }
        for (Thread th : started) {
            th.join(30_000);
            assertTrue(!th.isAlive());
        }
        assertEquals(0, failures.get());
        assertEquals(Optional.of(threads * each), store.find("a"));
    }
}
