package com.mystipixel.royaleconomy.storage;

import com.mystipixel.royalcommon.storage.Dialect;
import com.mystipixel.royalcommon.storage.SchemaVersion;
import com.mystipixel.royalcommon.storage.TxRunner;
import com.mystipixel.royaleconomy.core.AccountStore;
import com.mystipixel.royaleconomy.core.Change;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class JdbcAccountStore implements AccountStore {

    static final int SCHEMA_VERSION = 1;

    private final TxRunner db;
    private final Dialect dialect;
    private final String accountsTable;
    private final String ledgerTable;

    public JdbcAccountStore(TxRunner db, String tablePrefix) {
        this.db = db;
        this.dialect = db.dialect();
        this.accountsTable = tablePrefix + "accounts";
        this.ledgerTable = tablePrefix + "ledger";
    }

    public void init() throws SQLException {
        new SchemaVersion(db, tablePrefixOf(accountsTable)).ensure(SCHEMA_VERSION, (connection, d, from, to) -> {
            if (from < 1) {
                createTables(connection);
            }
        });
    }

    private static String tablePrefixOf(String tableName) {
        int idx = tableName.lastIndexOf('_');
        return idx > 0 ? tableName.substring(0, idx) : tableName;
    }

    private void createTables(Connection connection) throws SQLException {
        String accountsDdl = dialect == Dialect.MYSQL
                ? "CREATE TABLE IF NOT EXISTS " + accountsTable + " ("
                    + "uuid CHAR(36) PRIMARY KEY, "
                    + "balance BIGINT NOT NULL, "
                    + "created_at BIGINT NOT NULL, "
                    + "updated_at BIGINT NOT NULL)"
                : "CREATE TABLE IF NOT EXISTS " + accountsTable + " ("
                    + "uuid TEXT PRIMARY KEY, "
                    + "balance INTEGER NOT NULL, "
                    + "created_at INTEGER NOT NULL, "
                    + "updated_at INTEGER NOT NULL)";

        String ledgerDdl = dialect == Dialect.MYSQL
                ? "CREATE TABLE IF NOT EXISTS " + ledgerTable + " ("
                    + "id BIGINT PRIMARY KEY AUTO_INCREMENT, "
                    + "uuid CHAR(36) NOT NULL, "
                    + "delta BIGINT NOT NULL, "
                    + "balance_after BIGINT NOT NULL, "
                    + "reason VARCHAR(64) NOT NULL, "
                    + "at BIGINT NOT NULL, "
                    + "INDEX idx_ledger_uuid_at (uuid, at))"
                : "CREATE TABLE IF NOT EXISTS " + ledgerTable + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "uuid TEXT NOT NULL, "
                    + "delta INTEGER NOT NULL, "
                    + "balance_after INTEGER NOT NULL, "
                    + "reason TEXT NOT NULL, "
                    + "at INTEGER NOT NULL)";

        String indexDdl = "CREATE INDEX IF NOT EXISTS idx_" + ledgerTable + "_uuid_at ON " + ledgerTable + "(uuid, at)";

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(accountsDdl);
            statement.executeUpdate(ledgerDdl);
            if (dialect == Dialect.SQLITE) {
                statement.executeUpdate(indexDdl);
            }
        }
    }

    @Override
    public Map<UUID, Long> loadAll() throws SQLException {
        String sql = "SELECT uuid, balance FROM " + accountsTable;
        Map<UUID, Long> result = new HashMap<>();
        try (Connection connection = db.dataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                result.put(UUID.fromString(rows.getString(1)), rows.getLong(2));
            }
        }
        return result;
    }

    @Override
    public void apply(List<Change> changes) throws SQLException {
        if (changes.isEmpty()) {
            return;
        }
        db.inTransaction(tx -> {
            Connection connection = tx.connection();
            String insertIgnore = tx.dialect().insertIgnore();
            String insertAccount = insertIgnore + " INTO " + accountsTable
                    + " (uuid, balance, created_at, updated_at) VALUES (?, 0, ?, ?)";
            String updateBalance = "UPDATE " + accountsTable
                    + " SET balance = balance + ?, updated_at = ? WHERE uuid = ?";
            String insertLedger = "INSERT INTO " + ledgerTable
                    + " (uuid, delta, balance_after, reason, at) VALUES (?, ?, ?, ?, ?)";

            try (PreparedStatement accountStmt = connection.prepareStatement(insertAccount);
                 PreparedStatement balanceStmt = connection.prepareStatement(updateBalance);
                 PreparedStatement ledgerStmt = connection.prepareStatement(insertLedger)) {
                for (Change change : changes) {
                    String uuid = change.account().toString();
                    long at = change.atMillis();

                    accountStmt.setString(1, uuid);
                    accountStmt.setLong(2, at);
                    accountStmt.setLong(3, at);
                    accountStmt.executeUpdate();

                    balanceStmt.setLong(1, change.delta());
                    balanceStmt.setLong(2, at);
                    balanceStmt.setString(3, uuid);
                    balanceStmt.executeUpdate();

                    ledgerStmt.setString(1, uuid);
                    ledgerStmt.setLong(2, change.delta());
                    ledgerStmt.setLong(3, change.balanceAfter());
                    ledgerStmt.setString(4, change.reason());
                    ledgerStmt.setLong(5, at);
                    ledgerStmt.executeUpdate();
                }
            }
            return null;
        });
    }

    @Override
    public void createIfAbsent(UUID account, long startingBalance) throws SQLException {
        db.inTransaction(tx -> {
            String sql = tx.dialect().insertIgnore() + " INTO " + accountsTable
                    + " (uuid, balance, created_at, updated_at) VALUES (?, ?, ?, ?)";
            try (PreparedStatement statement = tx.connection().prepareStatement(sql)) {
                statement.setString(1, account.toString());
                statement.setLong(2, startingBalance);
                statement.setLong(3, System.currentTimeMillis());
                statement.setLong(4, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return null;
        });
    }
}
