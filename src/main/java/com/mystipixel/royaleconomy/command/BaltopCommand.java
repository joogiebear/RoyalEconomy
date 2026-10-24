package com.mystipixel.royaleconomy.command;

import com.mystipixel.royaleconomy.Messages;
import com.mystipixel.royaleconomy.core.EconomyService;
import com.mystipixel.royaleconomy.core.Entry;
import com.mystipixel.royaleconomy.core.Money;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

public final class BaltopCommand implements CommandExecutor, TabCompleter {

    private final EconomyService service;
    private final Messages messages;
    private final int digits;
    private final int pageSize;
    private volatile List<Entry> snapshot = List.of();

    public BaltopCommand(EconomyService service, Messages messages, int digits, int pageSize) {
        this.service = service;
        this.messages = messages;
        this.digits = digits;
        this.pageSize = pageSize;
    }

    /** Called off the main thread (DB executor) to rebuild the snapshot. */
    public void refresh() {
        snapshot = service.top(Integer.MAX_VALUE, 0);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("royaleconomy.baltop")) {
            messages.send(sender, "no-permission", Map.of());
            return true;
        }

        int page = 1;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                page = 1;
            }
            if (page < 1) {
                page = 1;
            }
        }

        List<Entry> entries = snapshot;
        if (entries.isEmpty()) {
            messages.send(sender, "baltop-empty", Map.of());
            return true;
        }

        int total = entries.size();
        int pages = (total + pageSize - 1) / pageSize;
        if (page > pages) {
            page = pages;
        }

        int offset = (page - 1) * pageSize;
        List<Entry> pageEntries = entries.subList(offset, Math.min(offset + pageSize, total));

        messages.send(sender, "baltop-header", Map.of("page", String.valueOf(page), "pages", String.valueOf(pages)));
        int rank = offset + 1;
        for (Entry entry : pageEntries) {
            String name = resolveName(entry.account());
            messages.send(sender, "baltop-line", Map.of(
                    "rank", String.valueOf(rank),
                    "player", name,
                    "balance", Money.plain(entry.balance(), digits)
            ));
            rank++;
        }
        return true;
    }

    private String resolveName(java.util.UUID uuid) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name != null ? name : "Unknown";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            int total = snapshot.size();
            int pages = (total + pageSize - 1) / pageSize;
            if (pages <= 0) {
                return List.of();
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return IntStream.rangeClosed(1, Math.min(pages, 20))
                    .mapToObj(String::valueOf)
                    .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
