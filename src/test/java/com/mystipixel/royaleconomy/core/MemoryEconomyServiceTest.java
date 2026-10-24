package com.mystipixel.royaleconomy.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryEconomyServiceTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID CAROL = UUID.randomUUID();

    private MemoryEconomyService service;
    private AtomicLong clock;

    @BeforeEach
    void setUp() {
        clock = new AtomicLong(1000L);
        service = new MemoryEconomyService(1000L, 100_000L, clock::incrementAndGet);
    }

    // --- deposit ---

    @Test
    void depositSucceeds() {
        service.createAccount(ALICE);
        Result r = service.deposit(ALICE, 500L, "test");
        assertTrue(r.ok());
        assertEquals(1500L, r.balance());
        assertEquals(1500L, service.balance(ALICE));
    }

    @Test
    void depositFailsOnMissingAccount() {
        Result r = service.deposit(UUID.randomUUID(), 100L, "test");
        assertEquals(Status.NO_ACCOUNT, r.status());
    }

    @Test
    void depositFailsOnInvalidAmount() {
        service.createAccount(ALICE);
        assertEquals(Status.INVALID_AMOUNT, service.deposit(ALICE, 0L, "test").status());
        assertEquals(Status.INVALID_AMOUNT, service.deposit(ALICE, -5L, "test").status());
    }

    @Test
    void depositFailsOnOverflow() {
        service.createAccount(ALICE);
        service.deposit(ALICE, 99_000L, "test");
        Result r = service.deposit(ALICE, 1_000L, "test");
        assertEquals(Status.OVERFLOW, r.status());
        assertEquals(100_000L, service.balance(ALICE));
    }

    // --- withdraw ---

    @Test
    void withdrawSucceeds() {
        service.createAccount(ALICE);
        Result r = service.withdraw(ALICE, 300L, "test");
        assertTrue(r.ok());
        assertEquals(700L, r.balance());
    }

    @Test
    void withdrawFailsOnInsufficientFunds() {
        service.createAccount(ALICE);
        Result r = service.withdraw(ALICE, 2000L, "test");
        assertEquals(Status.INSUFFICIENT_FUNDS, r.status());
        assertEquals(1000L, service.balance(ALICE));
    }

    @Test
    void withdrawFailsOnInvalidAmount() {
        service.createAccount(ALICE);
        assertEquals(Status.INVALID_AMOUNT, service.withdraw(ALICE, 0L, "test").status());
        assertEquals(Status.INVALID_AMOUNT, service.withdraw(ALICE, -1L, "test").status());
    }

    @Test
    void withdrawFailsOnMissingAccount() {
        assertEquals(Status.NO_ACCOUNT, service.withdraw(UUID.randomUUID(), 100L, "test").status());
    }

    // --- set ---

    @Test
    void setSucceeds() {
        service.createAccount(ALICE);
        Result r = service.set(ALICE, 5000L, "test");
        assertTrue(r.ok());
        assertEquals(5000L, service.balance(ALICE));
    }

    @Test
    void setToZero() {
        service.createAccount(ALICE);
        Result r = service.set(ALICE, 0L, "test");
        assertTrue(r.ok());
        assertEquals(0L, service.balance(ALICE));
    }

    @Test
    void setFailsOnNegative() {
        service.createAccount(ALICE);
        assertEquals(Status.INVALID_AMOUNT, service.set(ALICE, -1L, "test").status());
    }

    @Test
    void setFailsOnOverflow() {
        service.createAccount(ALICE);
        assertEquals(Status.OVERFLOW, service.set(ALICE, 200_000L, "test").status());
    }

    @Test
    void setFailsOnMissingAccount() {
        assertEquals(Status.NO_ACCOUNT, service.set(UUID.randomUUID(), 100L, "test").status());
    }

    // --- transfer ---

    @Test
    void transferSucceeds() {
        service.createAccount(ALICE);
        service.createAccount(BOB);
        Result r = service.transfer(ALICE, BOB, 400L, "pay");
        assertTrue(r.ok());
        assertEquals(600L, service.balance(ALICE));
        assertEquals(1400L, service.balance(BOB));
    }

    @Test
    void transferFailsOnInsufficientFunds() {
        service.createAccount(ALICE);
        service.createAccount(BOB);
        Result r = service.transfer(ALICE, BOB, 2000L, "pay");
        assertEquals(Status.INSUFFICIENT_FUNDS, r.status());
        assertEquals(1000L, service.balance(ALICE));
        assertEquals(1000L, service.balance(BOB));
    }

    @Test
    void transferFailsOnMissingSource() {
        service.createAccount(BOB);
        Result r = service.transfer(UUID.randomUUID(), BOB, 100L, "pay");
        assertEquals(Status.NO_ACCOUNT, r.status());
    }

    @Test
    void transferFailsOnMissingTarget() {
        service.createAccount(ALICE);
        Result r = service.transfer(ALICE, UUID.randomUUID(), 100L, "pay");
        assertEquals(Status.NO_ACCOUNT, r.status());
    }

    @Test
    void transferFailsOnTargetOverflow() {
        service.createAccount(ALICE);
        service.createAccount(BOB);
        service.set(BOB, 99_900L, "setup");
        Result r = service.transfer(ALICE, BOB, 500L, "pay");
        assertEquals(Status.OVERFLOW, r.status());
        assertEquals(1000L, service.balance(ALICE));
        assertEquals(99_900L, service.balance(BOB));
    }

    @Test
    void transferFailsOnInvalidAmount() {
        service.createAccount(ALICE);
        service.createAccount(BOB);
        assertEquals(Status.INVALID_AMOUNT, service.transfer(ALICE, BOB, 0L, "pay").status());
        assertEquals(Status.INVALID_AMOUNT, service.transfer(ALICE, BOB, -1L, "pay").status());
        assertEquals(1000L, service.balance(ALICE));
        assertEquals(1000L, service.balance(BOB));
    }

    // --- createAccount ---

    @Test
    void createAccountSucceeds() {
        assertTrue(service.createAccount(ALICE));
        assertEquals(1000L, service.balance(ALICE));
        assertTrue(service.hasAccount(ALICE));
    }

    @Test
    void createAccountFailsIfExists() {
        service.createAccount(ALICE);
        assertFalse(service.createAccount(ALICE));
        assertEquals(1000L, service.balance(ALICE));
    }

    // --- top ---

    @Test
    void topReturnsSorted() {
        service.createAccount(ALICE);
        service.createAccount(BOB);
        service.createAccount(CAROL);
        service.set(ALICE, 500L, "setup");
        service.set(BOB, 3000L, "setup");
        service.set(CAROL, 2000L, "setup");

        List<Entry> top = service.top(3, 0);
        assertEquals(3, top.size());
        assertEquals(BOB, top.get(0).account());
        assertEquals(CAROL, top.get(1).account());
        assertEquals(ALICE, top.get(2).account());
    }

    @Test
    void topRespectsLimitAndOffset() {
        service.createAccount(ALICE);
        service.createAccount(BOB);
        service.createAccount(CAROL);
        service.set(ALICE, 100L, "setup");
        service.set(BOB, 300L, "setup");
        service.set(CAROL, 200L, "setup");

        List<Entry> page = service.top(1, 1);
        assertEquals(1, page.size());
        assertEquals(CAROL, page.get(0).account());
    }

    // --- drain and requeue ---

    @Test
    void drainAndRequeuePreservesOrder() {
        service.createAccount(ALICE);
        service.deposit(ALICE, 100L, "a");
        service.deposit(ALICE, 200L, "b");
        service.withdraw(ALICE, 50L, "c");

        List<Change> changes = service.drainChanges();
        assertEquals(3, changes.size());
        assertEquals("a", changes.get(0).reason());
        assertEquals("b", changes.get(1).reason());
        assertEquals("c", changes.get(2).reason());

        // queue is now empty
        assertTrue(service.drainChanges().isEmpty());

        // requeue and verify order is preserved
        service.requeue(changes);
        List<Change> again = service.drainChanges();
        assertEquals(3, again.size());
        assertEquals("a", again.get(0).reason());
        assertEquals("b", again.get(1).reason());
        assertEquals("c", again.get(2).reason());
    }

    @Test
    void drainReturnsEmptyWhenNoChanges() {
        assertTrue(service.drainChanges().isEmpty());
    }

    // --- load ---

    @Test
    void loadSeedsAccounts() {
        service.load(Map.of(ALICE, 5000L, BOB, 7000L));
        assertEquals(2, service.accountCount());
        assertEquals(5000L, service.balance(ALICE));
        assertEquals(7000L, service.balance(BOB));
    }

    // --- concurrency ---

    @Test
    void concurrentOperationsConserveTotal() throws Exception {
        int accountCount = 8;
        UUID[] accounts = new UUID[accountCount];
        for (int i = 0; i < accountCount; i++) {
            accounts[i] = UUID.randomUUID();
            service.createAccount(accounts[i]);
        }

        long initialTotal = 0;
        for (UUID acc : accounts) {
            initialTotal += service.balance(acc);
        }

        int threads = 8;
        int opsPerThread = 500;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicLong totalDeposited = new AtomicLong();
        AtomicLong totalWithdrawn = new AtomicLong();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < opsPerThread; i++) {
                        UUID a = accounts[threadId % accountCount];
                        UUID b = accounts[(threadId + 1 + i) % accountCount];
                        int op = (threadId + i) % 3;
                        if (op == 0) {
                            Result r = service.deposit(a, 1L, "dep");
                            if (r.ok()) totalDeposited.incrementAndGet();
                        } else if (op == 1) {
                            Result r = service.withdraw(a, 1L, "wd");
                            if (r.ok()) totalWithdrawn.incrementAndGet();
                        } else {
                            service.transfer(a, b, 1L, "pay");
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        boolean finished = done.await(15, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertTrue(finished, "deadlock detected: operations did not finish within 15s");

        long finalTotal = 0;
        for (UUID acc : accounts) {
            finalTotal += service.balance(acc);
        }
        assertEquals(initialTotal + totalDeposited.get() - totalWithdrawn.get(), finalTotal,
                "total not conserved");
    }

    @Test
    void oppositeTransfersDoNotDeadlock() throws Exception {
        service.createAccount(ALICE);
        service.createAccount(BOB);
        service.set(ALICE, 10000L, "setup");
        service.set(BOB, 10000L, "setup");

        int iterations = 200;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(() -> {
            try {
                start.await();
                for (int i = 0; i < iterations; i++) {
                    service.transfer(ALICE, BOB, 1L, "a2b");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });
        pool.submit(() -> {
            try {
                start.await();
                for (int i = 0; i < iterations; i++) {
                    service.transfer(BOB, ALICE, 1L, "b2a");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });

        start.countDown();
        boolean finished = done.await(15, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertTrue(finished, "deadlock: opposite transfers did not finish within 15s");

        assertEquals(10000L, service.balance(ALICE));
        assertEquals(10000L, service.balance(BOB));
    }
}
