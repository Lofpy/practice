package com.poppy.practice.command;

import com.poppy.practice.language.LanguageService;
import com.poppy.practice.spectator.SpectatorService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class SpecCommand implements CommandExecutor, TabCompleter {
    private final SpectatorService spectators;
    private final LanguageService language;

    public SpecCommand(SpectatorService spectators, LanguageService language) {
        this.spectators = spectators;
        this.language = language;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }
        Player player = (Player) sender;
        // Leaving must remain possible even if permission was revoked during the session.
        if (args.length == 0 || args.length == 1 && args[0].equalsIgnoreCase("leave")) {
            if (!spectators.leave(player, true)) usage(player);
            return true;
        }
        if (args.length != 1) {
            usage(player);
            return true;
        }
        if (!player.hasPermission(SpectatorService.PERMISSION)) {
            language.send(player, "&c観戦する権限がありません。", "&cYou do not have permission to spectate.");
            return true;
        }
        spectators.spectate(player, Bukkit.getPlayerExact(args[0]));
        return true;
    }

    private void usage(Player player) {
        language.send(player, "&e使い方: /spec <プレイヤー名> &7| /spec leave", "&eUsage: /spec <player> &7| /spec leave");
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || args.length != 1) return Collections.emptyList();
        Player player = (Player) sender;
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<String>();
        if ("leave".startsWith(prefix)) result.add("leave");
        if (player.hasPermission(SpectatorService.PERMISSION) && !spectators.isSpectating(player.getUniqueId())) {
            for (Player candidate : Bukkit.getOnlinePlayers()) {
                if (!candidate.getUniqueId().equals(player.getUniqueId()) && player.canSee(candidate)
                        && candidate.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) result.add(candidate.getName());
            }
        }
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return result;
    }
}
