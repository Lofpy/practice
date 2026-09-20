package com.poppy.practice.listener;

import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class PlayerMoveListener implements Listener {
    private final ProfileManager profileManager;
    private com.poppy.practice.language.LanguageService languages;

    public void setLanguageService(com.poppy.practice.language.LanguageService languages) {
        this.languages = languages;
    }

    public PlayerMoveListener(ProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || event.getTo() == null) {
            return;
        }
        PlayerProfile profile = profileManager.get(event.getPlayer().getUniqueId());
        if (profile == null || profile.getState() != PlayerState.STARTING) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location frozen = from.clone();
            frozen.setYaw(to.getYaw());
            frozen.setPitch(to.getPitch());
            event.setTo(frozen);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        PlayerProfile profile = profileManager.get(event.getPlayer().getUniqueId());
        if (profile == null || profile.getState() != PlayerState.STARTING) {
            return;
        }
        String command = event.getMessage().toLowerCase();
        if (isAllowedDuringCountdown(command)) {
            return;
        }
        event.setCancelled(true);
        String japanese = "カウントダウン中はコマンドを使用できません。";
        event.getPlayer().sendMessage(ChatColor.RED + (languages == null ? japanese : languages.text(
                event.getPlayer(), japanese, "Commands are disabled during the countdown.")));
    }

    static boolean isAllowedDuringCountdown(String command) {
        return command != null && (command.equals("/practice") || command.startsWith("/practice ")
                || command.equals("/bot") || command.startsWith("/bot ")
                || command.equals("/ping") || command.startsWith("/ping ")
                || command.equals("/combokb") || command.startsWith("/combokb ")
                || command.equals("/poppypractice:combokb")
                || command.startsWith("/poppypractice:combokb "));
    }
}
