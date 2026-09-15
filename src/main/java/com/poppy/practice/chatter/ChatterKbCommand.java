package com.poppy.practice.chatter;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ChatterKbCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "status", "debug", "mode", "reload", "reset", "export");
    private static final List<String> MODES = Arrays.asList(
            "OFF", "DETECT_ONLY", "CHATTER_ONLY", "STRICT_NORMALIZE");
    private static final List<String> TOGGLES = Arrays.asList("on", "off");
    private final ChatterKbService service;
    private final ReloadAction reloadAction;

    public ChatterKbCommand(ChatterKbService service, ReloadAction reloadAction) {
        this.service = service;
        this.reloadAction = reloadAction;
    }

    @Override
    public boolean onCommand(final CommandSender sender, Command command,
                             String label, String[] args) {
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (!sender.hasPermission("chatterkb.admin." + subcommand)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }
        if (subcommand.equals("status")) {
            Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1])
                    : sender instanceof Player ? (Player) sender : null;
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Usage: /chatterkb status [player]");
                return true;
            }
            ChatterKbService.StatusSnapshot status = service.status(target.getUniqueId());
            if (status == null) {
                sender.sendMessage(ChatColor.RED + "No state exists for that player.");
                return true;
            }
            sender.sendMessage(ChatColor.DARK_GRAY + "--- " + ChatColor.GOLD
                    + "ChatterKB: " + target.getName() + ChatColor.DARK_GRAY + " ---");
            sender.sendMessage(ChatColor.GRAY + "Mode: " + ChatColor.WHITE + status.mode
                    + ChatColor.GRAY + "  State: " + ChatColor.WHITE + status.state
                    + ChatColor.GRAY + "  Score: " + ChatColor.WHITE
                    + String.format(Locale.ROOT, "%.2f", status.score));
            sender.sendMessage(ChatColor.GRAY + "Ping: " + ChatColor.WHITE + status.pingMs
                    + "ms" + ChatColor.GRAY + "  Protocol: " + ChatColor.WHITE
                    + status.protocol + ChatColor.GRAY + "  KB window: " + ChatColor.WHITE
                    + (status.windowId == null ? "none" : status.windowId));
            sender.sendMessage(ChatColor.GRAY + "Last skip: " + ChatColor.WHITE
                    + status.lastSkipReason + ChatColor.GRAY + "  Debug: "
                    + ChatColor.WHITE + status.debug + ChatColor.GRAY + "  Buffered events: "
                    + ChatColor.WHITE + status.bufferedDebugEvents
                    + ChatColor.GRAY + "  Dropped writes: "
                    + ChatColor.WHITE + status.telemetryDropped);
            return true;
        }
        if (subcommand.equals("debug") && args.length == 3) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null || (!args[2].equalsIgnoreCase("on")
                    && !args[2].equalsIgnoreCase("off"))) {
                sender.sendMessage(ChatColor.RED + "Usage: /chatterkb debug <player> on|off");
                return true;
            }
            service.setDebug(target.getUniqueId(), args[2].equalsIgnoreCase("on"));
            sender.sendMessage(ChatColor.GREEN + "ChatterKB debug for " + target.getName()
                    + " is now " + args[2].toLowerCase(Locale.ROOT) + '.');
            return true;
        }
        if (subcommand.equals("mode") && args.length == 2) {
            try {
                ChatterKbMode mode = ChatterKbMode.parse(args[1]);
                service.setMode(mode);
                sender.sendMessage(ChatColor.GREEN + "ChatterKB runtime mode is now " + mode + '.');
            } catch (IllegalArgumentException exception) {
                sender.sendMessage(ChatColor.RED + exception.getMessage());
            }
            return true;
        }
        if (subcommand.equals("reload") && args.length == 1) {
            try {
                reloadAction.reload();
                sender.sendMessage(ChatColor.GREEN + "ChatterKB configuration reloaded.");
            } catch (IllegalArgumentException exception) {
                sender.sendMessage(ChatColor.RED + "Reload rejected: " + exception.getMessage());
            }
            return true;
        }
        if (subcommand.equals("reset") && args.length == 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "That player is not online.");
                return true;
            }
            service.reset(target.getUniqueId());
            sender.sendMessage(ChatColor.GREEN + "Reset ChatterKB state for " + target.getName() + '.');
            return true;
        }
        if (subcommand.equals("export") && (args.length == 2 || args.length == 3)) {
            final Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "That player is not online.");
                return true;
            }
            int seconds = 30;
            if (args.length == 3) {
                try {
                    seconds = Math.max(1, Integer.parseInt(args[2]));
                } catch (NumberFormatException exception) {
                    sender.sendMessage(ChatColor.RED + "Seconds must be a whole number.");
                    return true;
                }
            }
            boolean queued = service.export(target.getUniqueId(), target.getName(), seconds,
                    new TelemetryService.ExportCallback() {
                        @Override
                        public void complete(File file, String error) {
                            if (file == null) {
                                sender.sendMessage(ChatColor.RED + "Export failed: " + error);
                            } else {
                                sender.sendMessage(ChatColor.GREEN + "Exported ChatterKB data to "
                                        + file.getAbsolutePath());
                            }
                        }
                    });
            if (!queued) sender.sendMessage(ChatColor.RED + "No state exists for that player.");
            return true;
        }
        usage(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) return matching(args[0], SUBCOMMANDS);
        if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            return matching(args[1], MODES);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("status")
                || args[0].equalsIgnoreCase("debug")
                || args[0].equalsIgnoreCase("reset")
                || args[0].equalsIgnoreCase("export"))) {
            List<String> players = new ArrayList<String>();
            for (Player player : Bukkit.getOnlinePlayers()) players.add(player.getName());
            return matching(args[1], players);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("debug")) {
            return matching(args[2], TOGGLES);
        }
        return Collections.emptyList();
    }

    private List<String> matching(String input, List<String> values) {
        List<String> result = new ArrayList<String>();
        for (String value : values) {
            if (value.regionMatches(true, 0, input, 0, input.length())) result.add(value);
        }
        return result;
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED
                + "Usage: /chatterkb <status|debug|mode|reload|reset|export>");
    }

    public interface ReloadAction {
        void reload();
    }
}
