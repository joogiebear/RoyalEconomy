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
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PayCommand implements CommandExecutor, TabCompleter {

    private final EconomyService service;
    private final Messages messages;
    private final int digits;
    private final long minPay;

    public PayCommand(EconomyService service, Messages messages, int digits, long minPay) {
        this.service = service;
        this.messages = messages;
        this.digits = digits;
        this.minPay = minPay;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (!player.hasPermission("royaleconomy.pay")) {
            messages.send(player, "no-permission", Map.of());
            return true;
        }
        if (args.length < 2) {
            messages.send(player, "invalid-amount", Map.of());
            return true;
        }

        String targetName = args[0];
        if (targetName.equalsIgnoreCase(player.getName())) {
            messages.send(player, "pay-self", Map.of());
            return true;
        }

        long amount;
        try {
            amount = Money.parse(args[1], digits);
        } catch (NumberFormatException e) {
            messages.send(player, "invalid-amount", Map.of());
            return true;
        }

        if (amount < minPay) {
            messages.send(player, "pay-too-small", Map.of("min", Money.plain(minPay, digits)));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(targetName);
        if (target == null || !service.hasAccount(target.getUniqueId())) {
            messages.send(player, "player-not-found", Map.of("player", targetName));
            return true;
        }

        Result result = service.transfer(player.getUniqueId(), target.getUniqueId(), amount, "pay");
        String amountStr = Money.plain(amount, digits);

        switch (result.status()) {
            case OK -> {
                messages.send(player, "pay-sent", Map.of("amount", amountStr, "player", targetName));
                Player onlineTarget = Bukkit.getPlayer(target.getUniqueId());
                if (onlineTarget != null) {
                    messages.send(onlineTarget, "pay-received", Map.of("amount", amountStr, "player", player.getName()));
                }
            }
            case INSUFFICIENT_FUNDS -> messages.send(player, "insufficient-funds", Map.of());
            case OVERFLOW -> messages.send(player, "amount-too-large", Map.of());
            default -> messages.send(player, "invalid-amount", Map.of());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> !n.equalsIgnoreCase(player.getName()))
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
