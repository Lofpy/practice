package com.poppy.practice.reach;

import com.poppy.practice.PracticePlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ReachGuardCommand implements CommandExecutor, TabCompleter {
    private static final DateTimeFormatter INSPECT_TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss");
    private final PracticePlugin plugin;
    private final ReachGuardService service;

    public ReachGuardCommand(PracticePlugin plugin, ReachGuardService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("status".equals(sub)) return status(sender, args);
        if ("inspect".equals(sub)) return inspect(sender, args);
        if ("alerts".equals(sub)) return alerts(sender, args);
        if ("mode".equals(sub)) return mode(sender, args);
        if ("exempt".equals(sub)) return exempt(sender, args);
        if ("unexempt".equals(sub)) return unexempt(sender, args);
        if ("resetvl".equals(sub)) return resetViolation(sender, args);
        if ("reload".equals(sub)) return reload(sender);
        sendUsage(sender);
        return true;
    }

    private boolean status(CommandSender sender, String[] args) {
        if (!allowed(sender, "reachguard.status")) return denied(sender);
        if (args.length >= 2) {
            Player player = online(args[1]);
            if (player == null) return notOnline(sender, args[1]);
            ReachGuardService.PlayerStatus status =
                    service.getPlayerStatus(player.getUniqueId());
            if (status == null) {
                sender.sendMessage(ChatColor.RED + "No ReachGuard session is available.");
                return true;
            }
            sender.sendMessage(ChatColor.DARK_RED + "ReachGuard: "
                    + ChatColor.WHITE + status.getName());
            sender.sendMessage(ChatColor.GRAY + "Protocol: " + status.getProtocol()
                    + " (" + protocolProtectionStatus(service.getConfiguration(),
                    status.getProtocol()) + ")  Ping: " + status.getPing() + "ms  RTT: "
                    + status.getTransactionRttMs() + "ms  Jitter: "
                    + format(status.getJitterMs()) + "ms");
            sender.sendMessage(ChatColor.GRAY + "Tracked targets: "
                    + status.getTrackedEntities() + "  Packet active: "
                    + status.isPacketActive() + "  Exempt: "
                    + status.isTemporarilyExempt());
            ViolationSnapshot vl = status.getViolation();
            sender.sendMessage(ChatColor.GRAY + "VL reach/stale/invalid: "
                    + format(vl.getReachVl()) + "/" + format(vl.getStaleVl())
                    + "/" + format(vl.getInvalidEntityVl()));
            ReachResult last = status.getLastResult();
            if (last != null) {
                sender.sendMessage(ChatColor.GRAY + "Last: " + last.getDecision()
                        + " reason=" + last.getReason() + " reach="
                        + format(last.getMeasuredReach()) + "/"
                        + format(last.getAllowedReach()));
            }
            return true;
        }

        ServerHealthSnapshot health = service.getServerHealth();
        sender.sendMessage(ChatColor.DARK_RED + "ReachGuard " + ChatColor.WHITE
                + service.getConfiguration().getMode() + ChatColor.GRAY
                + " / " + service.getConfiguration().getPacketLibrary());
        sender.sendMessage(ChatColor.GRAY + "TPS: " + format(health.getTps())
                + "  Tick: " + health.getLastTickDurationMs() + "ms  Sessions: "
                + service.getSessionCount());
        sender.sendMessage(ChatColor.GRAY + "Attacks: " + service.getProcessedAttacks()
                + "  Cancelled: " + service.getCancelledAttacks()
                + "  Unverified: " + service.getUnverifiedAttacks()
                + "  No-permit: " + service.getDamageWithoutPermitCount());
        sender.sendMessage(ChatColor.GRAY + "Evidence processed/queued/dropped/errors: "
                + service.getProcessedLogCount() + "/" + service.getQueuedLogCount()
                + "/" + service.getDroppedLogCount() + "/"
                + service.getEvidenceErrorCount() + "  Packet errors: "
                + service.getPacketErrors());
        return true;
    }

    private boolean inspect(CommandSender sender, String[] args) {
        if (!allowed(sender, "reachguard.inspect")) return denied(sender);
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /reachguard inspect <player>");
            return true;
        }
        Player player = online(args[1]);
        if (player == null) return notOnline(sender, args[1]);
        List<EvidenceRecord> history = service.getInspectionHistory(player.getUniqueId());
        sender.sendMessage(ChatColor.DARK_RED + "ReachGuard evidence: "
                + ChatColor.WHITE + player.getName() + ChatColor.GRAY + " ("
                + history.size() + ")");
        if (history.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No stored evidence in this runtime.");
            return true;
        }
        int shown = 0;
        for (EvidenceRecord record : history) {
            if (shown++ >= 8) break;
            sender.sendMessage(ChatColor.GRAY + inspectionSummary(record));
        }
        return true;
    }

    static String inspectionSummary(EvidenceRecord record) {
        return record.getTimestamp().format(INSPECT_TIME)
                + " " + record.getDecision() + " " + record.getReason()
                + " reach=" + format(record.getMeasuredReach()) + "/"
                + format(record.getAllowedReach()) + " VL(R/S/I)="
                + formatViolationLevel(record.getViolationLevel()) + "/"
                + formatViolationLevel(record.getStaleViolationLevel()) + "/"
                + formatViolationLevel(record.getInvalidEntityViolationLevel()) + " "
                + record.getReliability();
    }

    static String protocolProtectionStatus(ReachGuardConfig config, int protocol) {
        if (config == null || !config.supportsProtocol(protocol)) {
            return "UNSUPPORTED/FAIL_OPEN";
        }
        return "SUPPORTED/" + config.getMode();
    }

    private boolean alerts(CommandSender sender, String[] args) {
        if (!allowed(sender, "reachguard.alerts")) return denied(sender);
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This subcommand is player-only.");
            return true;
        }
        Player player = (Player) sender;
        boolean enabled;
        if (args.length < 2) {
            enabled = !service.hasAlerts(player.getUniqueId());
        } else if ("on".equalsIgnoreCase(args[1])) {
            enabled = true;
        } else if ("off".equalsIgnoreCase(args[1])) {
            enabled = false;
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /reachguard alerts <on|off>");
            return true;
        }
        service.setAlerts(player.getUniqueId(), enabled);
        sender.sendMessage((enabled ? ChatColor.GREEN : ChatColor.YELLOW)
                + "ReachGuard alerts " + (enabled ? "enabled." : "disabled."));
        return true;
    }

    private boolean mode(CommandSender sender, String[] args) {
        if (!allowed(sender, "reachguard.mode")) return denied(sender);
        if (args.length < 2) {
            sender.sendMessage(ChatColor.GRAY + "Current mode: "
                    + service.getConfiguration().getMode());
            return true;
        }
        ReachGuardMode requested = ReachGuardMode.parse(args[1], null);
        if (requested == null) {
            sender.sendMessage(ChatColor.RED
                    + "Mode must be observe, protect, or strict.");
            return true;
        }
        Object previous = plugin.getConfig().get("reachguard.mode");
        plugin.getConfig().set("reachguard.mode", requested.name());
        try {
            // Validate the complete candidate configuration before committing
            // it to disk or swapping the live service configuration.
            ReachGuardConfig.load(plugin.getConfig());
            plugin.saveConfig();
            service.reloadConfiguration();
            sender.sendMessage(ChatColor.GREEN + "ReachGuard mode set to "
                    + requested + '.');
        } catch (RuntimeException exception) {
            plugin.getConfig().set("reachguard.mode", previous);
            try {
                plugin.saveConfig();
                service.reloadConfiguration();
            } catch (RuntimeException restoreFailure) {
                plugin.getLogger().severe("Could not restore ReachGuard mode after failed "
                        + "change: " + restoreFailure.getMessage());
            }
            sender.sendMessage(ChatColor.RED + "Mode change failed: "
                    + exception.getMessage());
        }
        return true;
    }

    private boolean exempt(CommandSender sender, String[] args) {
        if (!allowed(sender, "reachguard.exempt")) return denied(sender);
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED
                    + "Usage: /reachguard exempt <player> <seconds>");
            return true;
        }
        Player player = online(args[1]);
        if (player == null) return notOnline(sender, args[1]);
        long seconds;
        try {
            seconds = Long.parseLong(args[2]);
        } catch (NumberFormatException exception) {
            seconds = -1L;
        }
        if (seconds < 1L || seconds > 86400L) {
            sender.sendMessage(ChatColor.RED + "Seconds must be 1..86400.");
            return true;
        }
        service.exempt(player.getUniqueId(), seconds);
        sender.sendMessage(ChatColor.GREEN + player.getName()
                + " is exempt for " + seconds + " seconds.");
        return true;
    }

    private boolean unexempt(CommandSender sender, String[] args) {
        if (!allowed(sender, "reachguard.exempt")) return denied(sender);
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED
                    + "Usage: /reachguard unexempt <player>");
            return true;
        }
        Player player = online(args[1]);
        if (player == null) return notOnline(sender, args[1]);
        service.unexempt(player.getUniqueId());
        sender.sendMessage(ChatColor.GREEN + player.getName() + " is no longer exempt.");
        return true;
    }

    private boolean resetViolation(CommandSender sender, String[] args) {
        if (!allowed(sender, "reachguard.resetvl")) return denied(sender);
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED
                    + "Usage: /reachguard resetvl <player>");
            return true;
        }
        Player player = online(args[1]);
        if (player == null) return notOnline(sender, args[1]);
        service.resetViolation(player.getUniqueId());
        sender.sendMessage(ChatColor.GREEN + "Reset ReachGuard VL for "
                + player.getName() + '.');
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!allowed(sender, "reachguard.reload")) return denied(sender);
        plugin.reloadConfig();
        try {
            service.reloadConfiguration();
            sender.sendMessage(ChatColor.GREEN + "ReachGuard configuration reloaded.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            sender.sendMessage(ChatColor.RED + "ReachGuard kept its previous settings: "
                    + exception.getMessage());
        }
        return true;
    }

    private static boolean allowed(CommandSender sender, String permission) {
        return sender.hasPermission("reachguard.admin")
                || sender.hasPermission(permission);
    }

    private static boolean denied(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "You do not have permission.");
        return true;
    }

    private static boolean notOnline(CommandSender sender, String name) {
        sender.sendMessage(ChatColor.RED + "Player is not online: " + name);
        return true;
    }

    private static Player online(String name) {
        return name == null ? null : Bukkit.getPlayer(name);
    }

    private static String format(double value) {
        return Double.isNaN(value) || Double.isInfinite(value)
                ? "n/a" : String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatViolationLevel(double value) {
        return Double.isNaN(value) || Double.isInfinite(value)
                ? "n/a" : String.format(Locale.ROOT, "%.3f", value);
    }

    private static void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED
                + "/reachguard <status [player]|inspect <player>|alerts <on|off>"
                + "|mode <observe|protect|strict>|exempt <player> <seconds>"
                + "|unexempt <player>|resetvl <player>|reload>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length == 1) {
            return matches(args[0], Arrays.asList("status", "inspect", "alerts",
                    "mode", "exempt", "unexempt", "resetvl", "reload"));
        }
        if (args.length == 2 && "mode".equalsIgnoreCase(args[0])) {
            return matches(args[1], Arrays.asList("observe", "protect", "strict"));
        }
        if (args.length == 2 && "alerts".equalsIgnoreCase(args[0])) {
            return matches(args[1], Arrays.asList("on", "off"));
        }
        if (args.length == 2 && ("status".equalsIgnoreCase(args[0])
                || "inspect".equalsIgnoreCase(args[0])
                || "exempt".equalsIgnoreCase(args[0])
                || "unexempt".equalsIgnoreCase(args[0])
                || "resetvl".equalsIgnoreCase(args[0]))) {
            List<String> names = new ArrayList<String>();
            for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
            return matches(args[1], names);
        }
        return Collections.emptyList();
    }

    private static List<String> matches(String prefix, List<String> candidates) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<String>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
