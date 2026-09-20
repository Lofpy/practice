package com.poppy.practice.language;

import com.poppy.practice.cosmetic.PreferencesService;
import com.poppy.practice.util.ItemBuilder;
import org.bukkit.entity.Player;
import java.util.UUID;

/** Explicit per-viewer translations, never global mutable locale or text rewriting. */
public final class LanguageService {
    private final PreferencesService preferences;

    public LanguageService(PreferencesService preferences) {
        if (preferences == null) throw new IllegalArgumentException("Missing preferences");
        this.preferences = preferences;
    }

    public PlayerLanguage language(UUID playerId) { return preferences.getLanguage(playerId); }
    public PlayerLanguage language(Player player) { return language(player == null ? null : player.getUniqueId()); }
    public String text(Player player, String japanese, String english) {
        return language(player).choose(japanese, english);
    }
    public void send(Player player, String japanese, String english) {
        player.sendMessage(ItemBuilder.color(text(player, japanese, english)));
    }
}
