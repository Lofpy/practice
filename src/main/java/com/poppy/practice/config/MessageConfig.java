package com.poppy.practice.config;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.util.ItemBuilder;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MessageConfig {
    private final PracticePlugin plugin;

    public MessageConfig(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    public String get(String key) {
        return get(key, new LinkedHashMap<String, String>());
    }

    public String get(String key, String placeholder, String value) {
        Map<String, String> replacements = new LinkedHashMap<String, String>();
        replacements.put(placeholder, value);
        return get(key, replacements);
    }

    public String get(String key, Map<String, String> replacements) {
        String prefix = plugin.getConfig().getString("messages.prefix", "&8[&cPractice&8] &r");
        String message = plugin.getConfig().getString("messages." + key, "&cMissing message: " + key);
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            message = message.replace("{" + replacement.getKey() + "}", replacement.getValue());
        }
        return ItemBuilder.color(prefix + message);
    }

    public void send(Player player, String key) {
        player.sendMessage(get(key));
    }

    public void send(Player player, String key, String placeholder, String value) {
        player.sendMessage(get(key, placeholder, value));
    }
}
