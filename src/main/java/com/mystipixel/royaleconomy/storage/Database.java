package com.mystipixel.royaleconomy.storage;

import com.mystipixel.royalcommon.storage.Dialect;
import com.mystipixel.royalcommon.storage.TxRunner;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Properties;

public final class Database {

    public enum Type { SQLITE, MYSQL }

    private final JavaPlugin plugin;
    private Type type;
    private HikariDataSource pool;
    private TxRunner txRunner;
    private String tablePrefix;

    public Database(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        FileConfiguration config = plugin.getConfig();
        String rawType = config.getString("database.type", "sqlite").toUpperCase(Locale.ROOT);
        this.type = "MYSQL".equals(rawType) ? Type.MYSQL : Type.SQLITE;
        this.tablePrefix = config.getString("database.table-prefix", "royaleconomy_");

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("RoyalEconomy");

        if (type == Type.MYSQL) {
            String host = config.getString("database.mysql.host", "localhost");
            int port = config.getInt("database.mysql.port", 3306);
            String name = config.getString("database.mysql.name", "minecraft");
            String user = config.getString("database.mysql.user", "root");
            String password = config.getString("database.mysql.password", "");
            int poolSize = Math.max(1, config.getInt("database.mysql.pool-size", 6));

            hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + name + "?useSSL=false");
            hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikari.setUsername(user);
            hikari.setPassword(password);
            hikari.setMaximumPoolSize(poolSize);
        } else {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                plugin.getLogger().severe("Could not create plugin data folder: " + dataFolder.getAbsolutePath());
                return false;
            }
            String fileName = config.getString("database.sqlite.file", "economy.db");
            File dbFile = new File(dataFolder, fileName);
            hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setMaximumPoolSize(1);
            Properties props = new Properties();
            props.setProperty("journal_mode", "WAL");
            hikari.setDataSourceProperties(props);
        }

        try {
            this.pool = new HikariDataSource(hikari);
            this.txRunner = new TxRunner(pool);
            plugin.getLogger().info("RoyalEconomy connected to " + type + " storage.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("RoyalEconomy storage init failed: " + e.getMessage());
            if (pool != null) {
                pool.close();
                pool = null;
            }
            return false;
        }
    }

    public TxRunner txRunner() {
        return txRunner;
    }

    public Dialect dialect() {
        return txRunner.dialect();
    }

    public String tablePrefix() {
        return tablePrefix;
    }

    public void close() {
        if (pool == null) {
            return;
        }
        if (type == Type.SQLITE) {
            try (Connection connection = pool.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            } catch (SQLException ignored) {
            }
        }
        pool.close();
        pool = null;
        txRunner = null;
    }
}
