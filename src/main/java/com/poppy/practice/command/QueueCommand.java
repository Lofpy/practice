package com.poppy.practice.command;

import com.poppy.practice.queue.QueueManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class QueueCommand implements CommandExecutor {
    private final QueueManager queueManager;

    public QueueCommand(QueueManager queueManager) {
        this.queueManager = queueManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        Player player = (Player) sender;
        if (args.length != 1) {
            player.sendMessage(ChatColor.RED + "Usage: /queue <join|leave>");
            return true;
        }
        if (args[0].equalsIgnoreCase("join")) {
            queueManager.join(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("leave")) {
            queueManager.leave(player, true);
            return true;
        }
        player.sendMessage(ChatColor.RED + "Usage: /queue <join|leave>");
        return true;
    }
}
