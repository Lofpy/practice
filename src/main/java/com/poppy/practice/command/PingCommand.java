package com.poppy.practice.command;

import com.poppy.practice.network.PlayerPingService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public final class PingCommand implements CommandExecutor, Listener {
    private final PlayerPingService pingService;

    public PingCommand(PlayerPingService pingService) {
        this.pingService = pingService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        Player player = (Player) sender;
        sendPing(player);
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!isSelfPingCommand(event.getMessage())) {
            return;
        }
        event.setCancelled(true);
        sendPing(event.getPlayer());
    }

    private void sendPing(Player player) {
        player.sendMessage(ChatColor.AQUA + "Your ping: " + ChatColor.WHITE
                + pingService.getPing(player) + "ms");
    }

    static boolean isSelfPingCommand(String message) {
        return message != null && message.trim().equalsIgnoreCase("/ping");
    }
}
