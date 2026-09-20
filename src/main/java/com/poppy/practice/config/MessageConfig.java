package com.poppy.practice.config;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.util.ItemBuilder;
import com.poppy.practice.language.LanguageService;
import com.poppy.practice.language.PlayerLanguage;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MessageConfig {
    private final PracticePlugin plugin;
    private LanguageService languages;

    public MessageConfig(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    public void setLanguageService(LanguageService languages) { this.languages = languages; }

    public void sendLocalized(Player player, String japanese, String english) {
        String selected = languages == null ? japanese : languages.text(player, japanese, english);
        player.sendMessage(ItemBuilder.color(selected));
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
        return get(null, key, replacements);
    }

    public String get(Player player, String key, Map<String, String> replacements) {
        String prefix = plugin.getConfig().getString("messages.prefix", "&8[&cAscendingMC&8] &r");
        String message = plugin.getConfig().getString("messages." + key, "&cMissing message: " + key);
        if (player != null && (languages == null || languages.language(player) == PlayerLanguage.JAPANESE)) {
            message = plugin.getConfig().getString("messages-ja." + key, japaneseDefault(key, message));
        }
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            message = message.replace("{" + replacement.getKey() + "}", replacement.getValue());
        }
        return ItemBuilder.color(prefix + message);
    }

    public void send(Player player, String key) {
        player.sendMessage(get(player, key, new LinkedHashMap<String, String>()));
    }

    public void send(Player player, String key, String placeholder, String value) {
        Map<String, String> replacements = new LinkedHashMap<String, String>();
        replacements.put(placeholder, value);
        player.sendMessage(get(player, key, replacements));
    }

    private static String japaneseDefault(String key, String fallback) {
        if ("no-kit-selected".equals(key)) return "&c先にキットを選択してください。";
        if ("kit-selected".equals(key)) return "&cキット &f{kit}&c を選択しました。";
        if ("joined-queue".equals(key)) return "&c&f{kit}&c のキューに参加しました。";
        if ("left-queue".equals(key)) return "&fキューから退出しました。";
        if ("not-queued".equals(key)) return "&cキューに参加していません。";
        if ("already-queued".equals(key)) return "&cすでにキューに参加しています。";
        if ("queue-not-allowed".equals(key)) return "&c今はキューに参加できません。";
        if ("no-arena".equals(key)) return "&f使用できるアリーナがありません。キューでお待ちください。";
        if ("spawn-not-allowed".equals(key)) return "&c試合中はスポーンに戻れません。";
        return fallback;
    }
}
