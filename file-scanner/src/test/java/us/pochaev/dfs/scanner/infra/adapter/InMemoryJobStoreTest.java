package us.pochaev.dfs.scanner.infra.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

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

    // ---- DFS-055: conditional create ----

    @Test
    @DisplayName("DFS-055.AC-2 createUnless stores the record when no stored record matches")
    void createUnlessStoresWhenNothingMatches() {
        InMemoryJobStore<String, Integer> store = new InMemoryJobStore<>();
        store.create("a", 1);

        Optional<String> blocker = store.createUnless("b", 2, n -> n > 5);

        assertEquals(Optional.empty(), blocker);
        assertEquals(Optional.of(2), store.find("b"));
        assertEquals(Optional.of(1), store.find("a"));
    }

    @Test
    @DisplayName("DFS-055.AC-2 createUnless returns the matching record's ID and stores nothing")
    void createUnlessNamesTheBlocker() {
        InMemoryJobStore<String, Integer> store = new InMemoryJobStore<>();
        store.create("a", 1);
        store.create("big", 10);

        Optional<String> blocker = store.createUnless("c", 3, n -> n > 5);

        assertEquals(Optional.of("big"), blocker);
        assertEquals(Optional.empty(), store.find("c"));
        // the predicate tells records apart: a record that stops matching no longer blocks
        store.update("big", n -> 0);
        assertEquals(Optional.empty(), store.createUnless("c", 3, n -> n > 5));
        assertEquals(Optional.of(3), store.find("c"));
    }

    @Test
    @DisplayName("DFS-055.AC-2 createUnless with no match refuses an ID already taken, and rejects nulls")
    void createUnlessDuplicateAndNulls() {
        InMemoryJobStore<String, Integer> store = new InMemoryJobStore<>();
        store.create("a", 1);
        try {
            store.createUnless("a", 2, n -> false);
            fail("duplicate id");
        } catch (IllegalStateException expected) {
            // expected
        }
        assertEquals(Optional.of(1), store.find("a"));
        try {
            store.createUnless(null, 2, n -> false);
            fail("null id");
        } catch (NullPointerException expected) {
            // expected
        }
        try {
            store.createUnless("z", null, n -> false);
            fail("null record");
        } catch (NullPointerException expected) {
            // expected
        }
        try {
            store.createUnless("z", 1, null);
            fail("null predicate");
        } catch (NullPointerException expected) {
            // expected
        }
    }

    @Test
    @DisplayName("DFS-055.AC-4 Of many simultaneous createUnless calls exactly one stores")
    void exactlyOneCreateUnlessWins() throws InterruptedException {
        InMemoryJobStore<String, Integer> store = new InMemoryJobStore<>();
        int threads = 16;
        CountDownLatch go = new CountDownLatch(1);
        AtomicReferenceArray<Optional<String>> results = new AtomicReferenceArray<>(threads);
        AtomicReferenceArray<Throwable> thrown = new AtomicReferenceArray<>(threads);
        List<Thread> started = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int n = i;
            started.add(Thread.ofVirtual().start(() -> {
                try {
                    go.await();
                    results.set(n, store.createUnless("job-" + n, n, r -> true));
                } catch (Throwable t) {
                    thrown.set(n, t);
                }
            }));
        }
        go.countDown();
        for (Thread th : started) {
            th.join(30_000);
            assertTrue(!th.isAlive());
        }
        for (int i = 0; i < threads; i++) {
            Throwable t = thrown.get(i);
            if (t instanceof RuntimeException r) {
                throw r;
            }
            if (t != null) {
                throw new AssertionError(t);
            }
        }

        int winners = 0;
        String winner = null;
        for (int i = 0; i < threads; i++) {
            if (results.get(i).isEmpty()) {
                winners++;
                winner = "job-" + i;
            }
        }
        assertEquals(1, winners);
        for (int i = 0; i < threads; i++) {
            if (results.get(i).isPresent()) {
                assertEquals(winner, results.get(i).get());
            }
        }
        int stored = 0;
        for (int i = 0; i < threads; i++) {
            if (store.find("job-" + i).isPresent()) {
                stored++;
            }
        }
        assertEquals(1, stored);
    }
}
