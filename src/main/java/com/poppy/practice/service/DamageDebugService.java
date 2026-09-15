package com.poppy.practice.service;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.bot.BotMatch;
import com.poppy.practice.match.Match;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class DamageDebugService {
    private static final String CONFIG_PATH = "debug.damage-chat";

    private final PracticePlugin plugin;
    private boolean enabled;

    public DamageDebugService(PracticePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean(CONFIG_PATH, false);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        plugin.getConfig().set(CONFIG_PATH, enabled);
        plugin.saveConfig();
    }

    public void report(Match match, Player victim, double finalDamage) {
        if (match == null) {
            return;
        }
        reportToParticipants(victim, finalDamage,
                Bukkit.getPlayer(match.getFirstPlayerId()),
                Bukkit.getPlayer(match.getSecondPlayerId()));
    }

    public void report(BotMatch match, Player victim, double finalDamage) {
        if (match == null) {
            return;
        }
        reportToParticipants(victim, finalDamage,
                Bukkit.getPlayer(match.getPlayerId()));
    }

    public void reportDebugRoom(Player victim, double finalDamage,
                                Player... recipients) {
        reportToParticipants(victim, finalDamage, recipients);
    }

    private void reportToParticipants(Player victim, double finalDamage,
                                      Player... recipients) {
        if (!enabled || victim == null) {
            return;
        }
        double healthBefore = Math.max(0.0D, victim.getHealth());
        double damage = actualDamage(finalDamage, healthBefore);
        if (damage <= 0.0D) {
            return;
        }
        double healthAfter = Math.max(0.0D, healthBefore - damage);
        String message = ChatColor.DARK_GRAY + "[" + ChatColor.RED + "Damage Debug"
                + ChatColor.DARK_GRAY + "] " + ChatColor.WHITE + victim.getName()
                + ChatColor.GRAY + " took " + ChatColor.RED + formatHealth(damage)
                + " HP " + ChatColor.DARK_GRAY + "(" + ChatColor.GRAY
                + formatHealth(healthBefore) + " -> " + formatHealth(healthAfter)
                + " HP" + ChatColor.DARK_GRAY + ")";

        Set<UUID> messaged = new HashSet<UUID>();
        for (Player recipient : recipients) {
            if (recipient != null && recipient.isOnline()
                    && messaged.add(recipient.getUniqueId())) {
                recipient.sendMessage(message);
            }
        }
    }

    static double actualDamage(double finalDamage, double currentHealth) {
        return Math.min(Math.max(0.0D, finalDamage), Math.max(0.0D, currentHealth));
    }

    static String formatHealth(double value) {
        String formatted = String.format(Locale.ROOT, "%.2f", Math.max(0.0D, value));
        if (formatted.endsWith("00")) {
            return formatted.substring(0, formatted.length() - 1);
        }
        if (formatted.endsWith("0")) {
            return formatted.substring(0, formatted.length() - 1);
        }
        return formatted;
    }
}
