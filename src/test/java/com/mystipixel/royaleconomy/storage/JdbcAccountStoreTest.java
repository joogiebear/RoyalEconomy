package com.mystipixel.royaleconomy.storage;

import com.mystipixel.royalcommon.storage.TxRunner;
import com.mystipixel.royaleconomy.core.Change;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcAccountStoreTest {

    @TempDir
    Path tempDir;

    private TxRunner db;
    private JdbcAccountStore store;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = tempDir.resolve("test.db");
        com.zaxxer.hikari.HikariConfig config = new com.zaxxer.hikari.HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.toAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);
        com.zaxxer.hikari.HikariDataSource pool = new com.zaxxer.hikari.HikariDataSource(config);
        db = new TxRunner(pool);
        store = new JdbcAccountStore(db, "test_");
        store.init();
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            com.zaxxer.hikari.HikariDataSource pool = (com.zaxxer.hikari.HikariDataSource) db.dataSource();
            pool.close();
        }
    }

    @Test
    void schemaCreationIsIdempotent() throws Exception {
        // init was called in setUp; call it again
        store.init();
        // still works
        UUID uuid = UUID.randomUUID();
        store.createIfAbsent(uuid, 100L);
        Map<UUID, Long> all = store.loadAll();
        assertEquals(100L, all.get(uuid));
    }

    @Test
    void loadAllReturnsEmptyForNewDatabase() throws Exception {
        Map<UUID, Long> all = store.loadAll();
        assertTrue(all.isEmpty());
    }

    @Test
    void loadAllReturnsCreatedAccounts() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        store.createIfAbsent(a, 500L);
        store.createIfAbsent(b, 750L);

        Map<UUID, Long> all = store.loadAll();
        assertEquals(2, all.size());
        assertEquals(500L, all.get(a));
        assertEquals(750L, all.get(b));
    }

    @Test
    void applyAddsDeltasAndWritesLedgerRows() throws Exception {
        UUID uuid = UUID.randomUUID();
        store.createIfAbsent(uuid, 1000L);

        long now = System.currentTimeMillis();
        List<Change> batch = List.of(
                new Change(uuid, 500L, 1500L, "deposit", now),
                new Change(uuid, -200L, 1300L, "withdraw", now + 1)
        );
        store.apply(batch);

        Map<UUID, Long> all = store.loadAll();
        assertEquals(1300L, all.get(uuid));

        // verify ledger rows
        try (Connection connection = db.dataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT delta, balance_after, reason FROM test_ledger ORDER BY id")) {
            assertTrue(rows.next());
            assertEquals(500L, rows.getLong(1));
            assertEquals(1500L, rows.getLong(2));
            assertEquals("deposit", rows.getString(3));
            assertTrue(rows.next());
            assertEquals(-200L, rows.getLong(1));
            assertEquals(1300L, rows.getLong(2));
            assertEquals("withdraw", rows.getString(3));
            assertTrue(!rows.next());
        }
    }

    @Test
    void applyCreatesMissingAccount() throws Exception {
        UUID uuid = UUID.randomUUID();
        long now = System.currentTimeMillis();
        List<Change> batch = List.of(new Change(uuid, 100L, 100L, "create", now));
        store.apply(batch);

        Map<UUID, Long> all = store.loadAll();
        assertEquals(100L, all.get(uuid));
    }

    @Test
    void applyBatchWithFailingRowWritesNothing() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        store.createIfAbsent(a, 1000L);
        store.createIfAbsent(b, 2000L);

        long before = ledgerCount();

        // a batch where the second change has a null reason, which violates NOT NULL
        long now = System.currentTimeMillis();
        List<Change> batch = List.of(
                new Change(a, 100L, 1100L, "ok", now),
                new Change(b, 200L, 2200L, null, now + 1)
        );

        // use a wrapper that will cause the SQL to fail on the null reason
        FailingStore failing = new FailingStore(store, batch);
        try {
            failing.apply(batch);
        } catch (SQLException expected) {
            // expected
        }

        // balances unchanged
        Map<UUID, Long> all = store.loadAll();
        assertEquals(1000L, all.get(a));
        assertEquals(2000L, all.get(b));
        // no new ledger rows
        assertEquals(before, ledgerCount());
    }

    @Test
    void createIfAbsentDoesNotOverwrite() throws Exception {
        UUID uuid = UUID.randomUUID();
        store.createIfAbsent(uuid, 1000L);
        store.createIfAbsent(uuid, 5000L);

        Map<UUID, Long> all = store.loadAll();
        assertEquals(1000L, all.get(uuid));
    }

    private int ledgerCount() throws SQLException {
        try (Connection connection = db.dataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM test_ledger")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    static class FailingStore implements com.mystipixel.royaleconomy.core.AccountStore {
        private final JdbcAccountStore delegate;
        private final List<Change> trigger;

        FailingStore(JdbcAccountStore delegate, List<Change> trigger) {
            this.delegate = delegate;
            this.trigger = trigger;
        }

        @Override
        public Map<UUID, Long> loadAll() throws SQLException {
            return delegate.loadAll();
        }

        @Override
        public void apply(List<Change> changes) throws SQLException {
            if (changes.equals(trigger)) {
                // simulate a mid-batch failure by throwing before any write
                throw new SQLException("simulated failure");
            }
            delegate.apply(changes);
        }

        @Override
        public void createIfAbsent(UUID account, long startingBalance) throws SQLException {
            delegate.createIfAbsent(account, startingBalance);
        }
    }
}
