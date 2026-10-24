package com.mystipixel.royaleconomy.command;

import com.mystipixel.royaleconomy.Messages;
import com.mystipixel.royaleconomy.core.EconomyService;
import com.mystipixel.royaleconomy.core.Money;
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

public final class BalanceCommand implements CommandExecutor, TabCompleter {

    private final EconomyService service;
    private final Messages messages;
    private final int digits;

    public BalanceCommand(EconomyService service, Messages messages, int digits) {
        this.service = service;
        this.messages = messages;
        this.digits = digits;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                return true;
            }
            if (!player.hasPermission("royaleconomy.balance")) {
                messages.send(player, "no-permission", Map.of());
                return true;
            }
            long balance = service.balance(player.getUniqueId());
            messages.send(player, "balance-self", Map.of("balance", Money.plain(balance, digits)));
            return true;
        }

        if (!sender.hasPermission("royaleconomy.balance.others")) {
            messages.send(sender, "no-permission", Map.of());
            return true;
        }

        String name = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(name);
        if (target == null || !service.hasAccount(target.getUniqueId())) {
            messages.send(sender, "player-not-found", Map.of("player", name));
            return true;
        }

        long balance = service.balance(target.getUniqueId());
        messages.send(sender, "balance-other", Map.of("player", name, "balance", Money.plain(balance, digits)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
