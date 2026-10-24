package com.mystipixel.royaleconomy.storage;

import com.mystipixel.royalcommon.storage.DbExecutor;
import com.mystipixel.royalcommon.storage.MainThread;
import com.mystipixel.royaleconomy.core.AccountStore;
import com.mystipixel.royaleconomy.core.Change;
import com.mystipixel.royaleconomy.core.EconomyService;
import com.mystipixel.royaleconomy.core.MemoryEconomyService;
import com.mystipixel.royaleconomy.core.Result;
import com.mystipixel.royaleconomy.core.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlusherTest {

    private DbExecutor db;
    private MemoryEconomyService economy;
    private FakeStore store;
    private Flusher flusher;

    @BeforeEach
    void setUp() {
        db = new DbExecutor("TestFlush", MainThread.direct(), Logger.getLogger("test"));
        AtomicLong clock = new AtomicLong(1000L);
        economy = new MemoryEconomyService(0L, 0L, clock::incrementAndGet);
        store = new FakeStore();
        flusher = new Flusher(economy, store, db, 40, Logger.getLogger("test"));
    }

    @AfterEach
    void tearDown() {
        db.shutdown(Duration.ofSeconds(5));
    }

    @Test
    void flushOnceDrainsAndApplies() throws Exception {
        UUID uuid = UUID.randomUUID();
        economy.createAccount(uuid);
        economy.deposit(uuid, 500L, "test");

        CompletableFuture<Void> future = flusher.flushOnce();
        future.get();

        assertEquals(1, store.applyCalls);
        assertEquals(1, store.lastBatch.size());
        assertEquals(500L, store.lastBatch.get(0).delta());
        // queue is now empty
        assertTrue(economy.drainChanges().isEmpty());
    }

    @Test
    void flushOnceDoesNothingWhenQueueIsEmpty() throws Exception {
        CompletableFuture<Void> future = flusher.flushOnce();
        assertTrue(future.isDone());
        assertEquals(0, store.applyCalls);
    }

    @Test
    void flushOnceRequeuesOnFailure() throws Exception {
        UUID uuid = UUID.randomUUID();
        economy.createAccount(uuid);
        economy.deposit(uuid, 100L, "test");

        store.failNext = true;
        CompletableFuture<Void> future = flusher.flushOnce();
        future.get();

        assertEquals(1, store.applyCalls);
        // changes were requeued
        List<Change> pending = economy.drainChanges();
        assertEquals(1, pending.size());
        assertEquals(100L, pending.get(0).delta());
    }

    @Test
    void flushNowDrainsUntilEmpty() throws Exception {
        UUID uuid = UUID.randomUUID();
        economy.createAccount(uuid);
        economy.deposit(uuid, 100L, "a");
        economy.deposit(uuid, 200L, "b");
        economy.withdraw(uuid, 50L, "c");

        flusher.flushNow(Duration.ofSeconds(5));

        assertEquals(1, store.applyCalls);
        assertEquals(3, store.lastBatch.size());
        assertTrue(economy.drainChanges().isEmpty());
    }

    @Test
    void flushNowRetriesAfterFailure() throws Exception {
        UUID uuid = UUID.randomUUID();
        economy.createAccount(uuid);
        economy.deposit(uuid, 100L, "test");

        // first call fails, second succeeds
        store.failNext = true;
        flusher.flushNow(Duration.ofSeconds(5));

        assertEquals(2, store.applyCalls);
        assertTrue(economy.drainChanges().isEmpty());
    }

    @Test
    void flushNowRespectsTimeout() throws Exception {
        UUID uuid = UUID.randomUUID();
        economy.createAccount(uuid);
        economy.deposit(uuid, 100L, "test");

        // always fail: flushNow should give up after timeout
        store.alwaysFail = true;
        long start = System.nanoTime();
        flusher.flushNow(Duration.ofMillis(200));
        long elapsed = System.nanoTime() - start;

        assertTrue(elapsed < 5_000_000_000L, "should not run for 5 seconds");
        // changes are still in the queue
        assertEquals(1, economy.drainChanges().size());
    }

    static class FakeStore implements AccountStore {
        int applyCalls;
        List<Change> lastBatch;
        boolean failNext;
        boolean alwaysFail;

        @Override
        public Map<UUID, Long> loadAll() {
            return Map.of();
        }

        @Override
        public void apply(List<Change> changes) throws SQLException {
            applyCalls++;
            lastBatch = changes;
            if (alwaysFail || failNext) {
                failNext = false;
                throw new SQLException("simulated failure");
            }
        }

        @Override
        public void createIfAbsent(UUID account, long startingBalance) {
        }
    }
}
