package com.mystipixel.royaleconomy;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;

public final class Messages {

    private final File file;
    private FileConfiguration config;

    public Messages(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String raw = config.getString(key, key);
        String prefix = config.getString("prefix", "");
        raw = raw.replace("{prefix}", prefix);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        String colored = ChatColor.translateAlternateColorCodes('&', raw);
        for (String line : colored.split("\n")) {
            sender.sendMessage(line);
        }
    }
}
