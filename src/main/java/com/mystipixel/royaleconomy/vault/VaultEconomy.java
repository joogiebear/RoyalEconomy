package com.mystipixel.royaleconomy.vault;

import com.mystipixel.royaleconomy.core.EconomyService;
import com.mystipixel.royaleconomy.core.Money;
import com.mystipixel.royaleconomy.core.Result;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Locale;

public final class VaultEconomy implements Economy {

    private final EconomyService service;
    private final int digits;
    private final String symbol;
    private final String currencySingular;
    private final String currencyPlural;

    public VaultEconomy(EconomyService service, int digits, String symbol, String currencySingular, String currencyPlural) {
        this.service = service;
        this.digits = digits;
        this.symbol = symbol;
        this.currencySingular = currencySingular;
        this.currencyPlural = currencyPlural;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "RoyalEconomy";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return digits;
    }

    @Override
    public String format(double balance) {
        long minor;
        try {
            minor = Money.fromDouble(balance, digits);
        } catch (NumberFormatException e) {
            return symbol + "0";
        }
        return symbol + Money.plain(minor, digits);
    }

    @Override
    public String currencyNamePlural() {
        return currencyPlural;
    }

    @Override
    public String currencyNameSingular() {
        return currencySingular;
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    // --- String-name player methods (resolve via Bukkit.getOfflinePlayer) ---

    @Override
    public boolean hasAccount(String player) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(player);
        return service.hasAccount(offline.getUniqueId());
    }

    @Override
    public boolean hasAccount(String player, String world) {
        return hasAccount(player);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return service.hasAccount(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String world) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(String player) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(player);
        return Money.toDouble(service.balance(offline.getUniqueId()), digits);
    }

    @Override
    public double getBalance(String player, String world) {
        return getBalance(player);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return Money.toDouble(service.balance(player.getUniqueId()), digits);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(String player, double amount) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(player);
        return has(offline, amount);
    }

    @Override
    public boolean has(String player, String world, double amount) {
        return has(player, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return false;
        }
        long minor;
        try {
            minor = Money.fromDouble(amount, digits);
        } catch (NumberFormatException e) {
            return false;
        }
        return service.balance(player.getUniqueId()) >= minor;
    }

    @Override
    public boolean has(OfflinePlayer player, String world, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String player, double amount) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(player);
        return withdrawPlayer(offline, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String player, String world, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "negative amount");
        }
        long minor;
        try {
            minor = Money.fromDouble(amount, digits);
        } catch (NumberFormatException e) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "invalid amount");
        }
        Result result = service.withdraw(player.getUniqueId(), minor, callingPluginName());
        return toResponse(result);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String world, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String player, double amount) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(player);
        return depositPlayer(offline, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String player, String world, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "negative amount");
        }
        long minor;
        try {
            minor = Money.fromDouble(amount, digits);
        } catch (NumberFormatException e) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "invalid amount");
        }
        Result result = service.deposit(player.getUniqueId(), minor, callingPluginName());
        return toResponse(result);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String world, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public boolean createPlayerAccount(String player) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(player);
        return service.createAccount(offline.getUniqueId());
    }

    @Override
    public boolean createPlayerAccount(String player, String world) {
        return createPlayerAccount(player);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return service.createAccount(player.getUniqueId());
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String world) {
        return createPlayerAccount(player);
    }

    // --- Bank methods (all NOT_IMPLEMENTED) ---

    @Override
    public EconomyResponse createBank(String name, String player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse deleteBank(String bank) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankBalance(String bank) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankHas(String bank, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankWithdraw(String bank, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankDeposit(String bank, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankOwner(String bank, String player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankOwner(String bank, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankMember(String bank, String player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankMember(String bank, OfflinePlayer player) {
        return notImplemented();
    }

    // --- helpers ---

    private EconomyResponse notImplemented() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "");
    }

    private EconomyResponse toResponse(Result result) {
        EconomyResponse.ResponseType type;
        switch (result.status()) {
            case OK:
                type = EconomyResponse.ResponseType.SUCCESS;
                break;
            case INSUFFICIENT_FUNDS:
                type = EconomyResponse.ResponseType.FAILURE;
                break;
            default:
                type = EconomyResponse.ResponseType.FAILURE;
                break;
        }
        return new EconomyResponse(Money.toDouble(result.balance(), digits), 0, type, "");
    }

    private String callingPluginName() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String cls = element.getClassName();
            if (cls.startsWith("com.mystipixel.royaleconomy.")
                    || cls.startsWith("org.bukkit.")
                    || cls.startsWith("java.")
                    || cls.startsWith("jdk.")
                    || cls.startsWith("sun.")
                    || cls.startsWith("net.milkbowl.")
                    || cls.startsWith("com.destroystoys.")
                    || cls.startsWith("io.papermc.")) {
                continue;
            }
            String[] parts = cls.split("\\.");
            if (parts.length >= 2) {
                return (parts[0] + "." + parts[1]).toLowerCase(Locale.ROOT);
            }
            return "vault";
        }
        return "vault";
    }
}
