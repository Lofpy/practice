package com.poppy.practice.command;

import com.poppy.practice.bot.HitDebugRoomService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class HitDebugCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = Arrays.asList("enter", "reset", "leave");
    private final HitDebugRoomService service;

    public HitDebugCommand(HitDebugRoomService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        Player player = (Player) sender;
        String action = args.length == 0 ? "enter" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("enter")) {
            service.enter(player);
            return true;
        }
        if (action.equals("reset")) {
            if (!service.resetBots(player)) {
                player.sendMessage(ChatColor.RED + "You are not in the hit debug room.");
            }
            return true;
        }
        if (action.equals("leave")) {
            if (!service.isParticipant(player)) {
                player.sendMessage(ChatColor.RED + "You are not in the hit debug room.");
            } else {
                service.leave(player);
            }
            return true;
        }
        player.sendMessage(ChatColor.RED + "Usage: /hitdebug [enter|reset|leave]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length != 1) return Collections.emptyList();
        String input = args[0].toLowerCase(Locale.ROOT);
        java.util.ArrayList<String> result = new java.util.ArrayList<String>();
        for (String value : SUBCOMMANDS) {
            if (value.startsWith(input)) result.add(value);
        }
        return result;
    }
}
