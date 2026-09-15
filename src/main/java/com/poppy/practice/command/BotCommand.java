package com.poppy.practice.command;

import com.poppy.practice.bot.BotService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class BotCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT_ARGUMENTS =
            Arrays.asList("start", "settings", "leave");
    private final BotService botService;

    public BotCommand(BotService botService) {
        this.botService = botService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0 || (args.length == 1
                && (args[0].equalsIgnoreCase("start")
                || args[0].equalsIgnoreCase("settings")))) {
            botService.openSettings(player);
            return true;
        }
        if (args.length == 1 && (args[0].equalsIgnoreCase("leave")
                || args[0].equalsIgnoreCase("stop"))) {
            if (!botService.forceStop(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "You are not in a bot match.");
            }
            return true;
        }
        player.sendMessage(ChatColor.RED + "Usage: /bot [start|settings|leave]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            return matching(args[0], ROOT_ARGUMENTS);
        }
        return Collections.emptyList();
    }

    private List<String> matching(String prefix, List<String> values) {
        List<String> matches = new ArrayList<String>();
        for (String value : values) {
            if (value.regionMatches(true, 0, prefix, 0, prefix.length())) {
                matches.add(value);
            }
        }
        return matches;
    }
}
