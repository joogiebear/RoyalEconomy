package com.mystipixel.royaleconomy;

import com.mystipixel.royalcommon.storage.DbExecutor;
import com.mystipixel.royalcommon.storage.MainThread;
import com.mystipixel.royaleconomy.command.BalanceCommand;
import com.mystipixel.royaleconomy.command.BaltopCommand;
import com.mystipixel.royaleconomy.command.EcoCommand;
import com.mystipixel.royaleconomy.command.PayCommand;
import com.mystipixel.royaleconomy.core.MemoryEconomyService;
import com.mystipixel.royaleconomy.core.Money;
import com.mystipixel.royaleconomy.storage.Database;
import com.mystipixel.royaleconomy.storage.Flusher;
import com.mystipixel.royaleconomy.storage.JdbcAccountStore;
import com.mystipixel.royaleconomy.vault.VaultEconomy;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public final class RoyalEconomyPlugin extends JavaPlugin implements Listener {

    private static final Pattern TABLE_PREFIX_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

    private Messages messages;
    private Database database;
    private JdbcAccountStore store;
    private MemoryEconomyService service;
    private DbExecutor dbExecutor;
    private Flusher flusher;
    private VaultEconomy vaultEconomy;
    private int baltopTaskId = -1;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            messages = new Messages(this);

            String prefix = getConfig().getString("database.table-prefix", "royaleconomy_");
            if (!TABLE_PREFIX_PATTERN.matcher(prefix).matches()) {
                getLogger().severe("Invalid table-prefix '" + prefix + "': must match [A-Za-z0-9_]+. Disabling RoyalEconomy.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            database = new Database(this);
            if (!database.connect()) {
                getLogger().severe("Could not connect to the database. Disabling RoyalEconomy.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            store = new JdbcAccountStore(database.txRunner(), prefix);
            store.init();

            Map<UUID, Long> accounts = store.loadAll();

            int digits = getConfig().getInt("currency.fraction-digits", 2);
            long startingBalance = Money.fromDouble(getConfig().getDouble("currency.starting-balance", 0), digits);
            long maxBalanceRaw = Money.fromDouble(getConfig().getDouble("currency.max-balance", 0), digits);
            long maxBalance = maxBalanceRaw == 0 ? Long.MAX_VALUE : maxBalanceRaw;
            service = new MemoryEconomyService(startingBalance, maxBalance, System::currentTimeMillis);
            service.load(accounts);

            String symbol = getConfig().getString("currency.symbol", "$");
            String singular = getConfig().getString("currency.singular", "Coin");
            String plural = getConfig().getString("currency.plural", "Coins");
            vaultEconomy = new VaultEconomy(service, digits, symbol, singular, plural);
            getServer().getServicesManager().register(
                    Economy.class, vaultEconomy, this, ServicePriority.Highest);

            dbExecutor = new DbExecutor("RoyalEconomy", MainThread.bukkit(this), getLogger());
            int flushInterval = getConfig().getInt("persistence.flush-interval-ticks", 40);
            flusher = new Flusher(service, store, dbExecutor, flushInterval, getLogger());
            flusher.start(this);

            int baltopRefreshSeconds = getConfig().getInt("persistence.baltop-refresh-seconds", 60);
            int baltopPageSize = getConfig().getInt("persistence.baltop-page-size", 10);
            BaltopCommand baltopCommand = new BaltopCommand(service, messages, digits, baltopPageSize);
            baltopTaskId = getServer().getScheduler().runTaskTimer(this, () ->
                    dbExecutor.submit("baltop refresh", baltopCommand::refresh),
                    baltopRefreshSeconds * 20L, baltopRefreshSeconds * 20L).getTaskId();

            long minPay = Money.fromDouble(getConfig().getDouble("currency.min-pay", 0.01), digits);
            registerCommand("balance", new BalanceCommand(service, messages, digits));
            registerCommand("pay", new PayCommand(service, messages, digits, minPay));
            registerCommand("baltop", baltopCommand);
            registerCommand("eco", new EcoCommand(service, messages, digits, startingBalance, () -> {
                reloadConfig();
                messages.reload();
            }));

            getServer().getPluginManager().registerEvents(this, this);

            getLogger().info("RoyalEconomy enabled. " + accounts.size() + " account(s) loaded.");
        } catch (Exception e) {
            getLogger().severe("RoyalEconomy failed to enable: " + e.getMessage());
            e.printStackTrace();
            cleanup();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (vaultEconomy != null) {
            getServer().getServicesManager().unregister(Economy.class, vaultEconomy);
            vaultEconomy = null;
        }
        if (baltopTaskId != -1) {
            getServer().getScheduler().cancelTask(baltopTaskId);
            baltopTaskId = -1;
        }
        if (flusher != null) {
            flusher.stop();
            flusher.flushNow(Duration.ofSeconds(10));
        }
        if (dbExecutor != null) {
            dbExecutor.shutdown(Duration.ofSeconds(5));
        }
        if (database != null) {
            database.close();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!service.hasAccount(uuid)) {
            service.createAccount(uuid);
        }
    }

    private void registerCommand(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            if (executor instanceof TabCompleter completer) {
                command.setTabCompleter(completer);
            }
        }
    }

    private void cleanup() {
        if (vaultEconomy != null) {
            getServer().getServicesManager().unregister(Economy.class, vaultEconomy);
            vaultEconomy = null;
        }
        if (baltopTaskId != -1) {
            getServer().getScheduler().cancelTask(baltopTaskId);
            baltopTaskId = -1;
        }
        if (flusher != null) {
            flusher.stop();
        }
        if (dbExecutor != null) {
            dbExecutor.shutdown(Duration.ofSeconds(2));
            dbExecutor = null;
        }
        if (database != null) {
            database.close();
            database = null;
        }
    }
}
