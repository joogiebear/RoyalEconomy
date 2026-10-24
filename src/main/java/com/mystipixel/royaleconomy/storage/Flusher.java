package com.mystipixel.royaleconomy.storage;

import com.mystipixel.royalcommon.storage.DbExecutor;
import com.mystipixel.royaleconomy.core.AccountStore;
import com.mystipixel.royaleconomy.core.Change;
import com.mystipixel.royaleconomy.core.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

public final class Flusher {

    private final EconomyService economy;
    private final AccountStore store;
    private final DbExecutor db;
    private final int intervalTicks;
    private final Logger logger;
    private int taskId = -1;

    public Flusher(EconomyService economy, AccountStore store, DbExecutor db, int intervalTicks, Logger logger) {
        this.economy = economy;
        this.store = store;
        this.db = db;
        this.intervalTicks = intervalTicks;
        this.logger = logger;
    }

    public CompletableFuture<Void> flushOnce() {
        List<Change> changes = economy.drainChanges();
        if (changes.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return db.submit("flush " + changes.size() + " changes", () -> {
            try {
                store.apply(changes);
            } catch (SQLException e) {
                logger.warning("Failed to flush " + changes.size() + " changes: " + e.getMessage());
                economy.requeue(changes);
            }
        });
    }

    public void start(Plugin plugin) {
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::flushOnce, intervalTicks, intervalTicks).getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public void flushNow(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            CompletableFuture<Void> future = flushOnce();
            if (future.isDone()) {
                return;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return;
            }
            try {
                future.get(remaining, TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException e) {
                // the callback swallows SQLException; this path is unreachable in normal operation
            }
        }
    }
}
