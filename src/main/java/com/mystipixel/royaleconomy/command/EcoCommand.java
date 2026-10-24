package com.mystipixel.royaleconomy.command;

import com.mystipixel.royaleconomy.Messages;
import com.mystipixel.royaleconomy.core.EconomyService;
import com.mystipixel.royaleconomy.core.Money;
import com.mystipixel.royaleconomy.core.Result;
import com.mystipixel.royaleconomy.core.Status;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class EcoCommand implements CommandExecutor, TabCompleter {

    private static final Set<String> SUBCOMMANDS = Set.of("give", "take", "set", "reset", "reload");

    private final EconomyService service;
    private final Messages messages;
    private final int digits;
    private final long startingBalance;
    private final Runnable reloadAction;

    public EcoCommand(EconomyService service, Messages messages, int digits, long startingBalance, Runnable reloadAction) {
        this.service = service;
        this.messages = messages;
        this.digits = digits;
        this.startingBalance = startingBalance;
        this.reloadAction = reloadAction;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("royaleconomy.admin")) {
            messages.send(sender, "no-permission", Map.of());
            return true;
        }
        if (args.length == 0) {
            messages.send(sender, "eco-usage", Map.of());
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                reloadAction.run();
                messages.send(sender, "eco-reloaded", Map.of());
            }
            case "give", "take", "set" -> {
                if (args.length < 3) {
                    messages.send(sender, "eco-usage", Map.of());
                    return true;
                }
                handleAmountCommand(sender, sub, args[1], args[2]);
            }
            case "reset" -> {
                if (args.length < 2) {
                    messages.send(sender, "eco-usage", Map.of());
                    return true;
                }
                handleReset(sender, args[1]);
            }
            default -> messages.send(sender, "eco-usage", Map.of());
        }
        return true;
    }

    private void handleAmountCommand(CommandSender sender, String sub, String name, String amountText) {
        long amount;
        try {
            amount = Money.parse(amountText, digits);
        } catch (NumberFormatException e) {
            messages.send(sender, "invalid-amount", Map.of());
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(name);
        if (target == null || !service.hasAccount(target.getUniqueId())) {
            messages.send(sender, "player-not-found", Map.of("player", name));
            return;
        }

        String amountStr = Money.plain(amount, digits);
        switch (sub) {
            case "give" -> {
                Result r = service.deposit(target.getUniqueId(), amount, "eco-give");
                if (r.ok()) {
                    messages.send(sender, "eco-given", Map.of("amount", amountStr, "player", name));
                } else if (r.status() == Status.OVERFLOW) {
                    messages.send(sender, "amount-too-large", Map.of());
                } else {
                    messages.send(sender, "invalid-amount", Map.of());
                }
            }
            case "take" -> {
                Result r = service.withdraw(target.getUniqueId(), amount, "eco-take");
                if (r.ok()) {
                    messages.send(sender, "eco-taken", Map.of("amount", amountStr, "player", name));
                } else if (r.status() == Status.INSUFFICIENT_FUNDS) {
                    messages.send(sender, "insufficient-funds", Map.of());
                } else {
                    messages.send(sender, "invalid-amount", Map.of());
                }
            }
            case "set" -> {
                Result r = service.set(target.getUniqueId(), amount, "eco-set");
                if (r.ok()) {
                    messages.send(sender, "eco-set", Map.of("player", name, "amount", amountStr));
                } else if (r.status() == Status.OVERFLOW) {
                    messages.send(sender, "amount-too-large", Map.of());
                } else {
                    messages.send(sender, "invalid-amount", Map.of());
                }
            }
        }
    }

    private void handleReset(CommandSender sender, String name) {
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(name);
        if (target == null || !service.hasAccount(target.getUniqueId())) {
            messages.send(sender, "player-not-found", Map.of("player", name));
            return;
        }
        Result r = service.set(target.getUniqueId(), startingBalance, "eco-reset");
        if (r.ok()) {
            messages.send(sender, "eco-reset", Map.of("player", name, "amount", Money.plain(startingBalance, digits)));
        } else {
            messages.send(sender, "invalid-amount", Map.of());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("royaleconomy.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("reload")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(org.bukkit.entity.Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
