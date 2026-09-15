package com.poppy.practice.command;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.arena.Arena;
import com.poppy.practice.arena.ArenaManager;
import com.poppy.practice.bot.BotService;
import com.poppy.practice.config.SplashPotionConfig;
import com.poppy.practice.config.SplashPotionConfig.Tuning;
import com.poppy.practice.match.MatchManager;
import com.poppy.practice.network.PlayerLatencyService;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.queue.MatchQueue;
import com.poppy.practice.queue.QueueManager;
import com.poppy.practice.service.DamageDebugService;
import com.poppy.practice.service.MatchService;
import org.bukkit.Bukkit;
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

public final class PracticeCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "reload", "forcestop", "debug", "potion", "ping");
    private static final List<String> PING_VALUES = Arrays.asList("off", "50", "100", "150", "200");
    private static final List<String> POTION_PROFILES = Arrays.asList("without-speed", "speed-ii");
    private static final List<String> POTION_SETTINGS = Arrays.asList(
            "hitbox-size", "x-speed", "y-speed", "spawn-forward-offset", "spawn-height-offset",
            "self-collision-delay-ticks");
    private static final List<String> DEBUG_SETTINGS = Arrays.asList("on", "off", "damage");
    private static final List<String> TOGGLE_VALUES = Arrays.asList("on", "off");
    private final PracticePlugin plugin;
    private final ProfileManager profileManager;
    private final QueueManager queueManager;
    private final MatchManager matchManager;
    private final ArenaManager arenaManager;
    private final MatchService matchService;
    private final BotService botService;
    private final PlayerLatencyService latencyService;
    private final DamageDebugService damageDebugService;

    public PracticeCommand(PracticePlugin plugin, ProfileManager profileManager, QueueManager queueManager,
                           MatchManager matchManager, ArenaManager arenaManager, MatchService matchService,
                           BotService botService, PlayerLatencyService latencyService,
                           DamageDebugService damageDebugService) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        this.queueManager = queueManager;
        this.matchManager = matchManager;
        this.arenaManager = arenaManager;
        this.matchService = matchService;
        this.botService = botService;
        this.latencyService = latencyService;
        this.damageDebugService = damageDebugService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("practice.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (matchManager.size() > 0 || botService.size() > 0) {
                sender.sendMessage(ChatColor.RED + "Cannot reload while matches are running.");
                return true;
            }
            plugin.reloadPracticeConfiguration();
            sender.sendMessage(ChatColor.GREEN + "PoppyPractice configuration reloaded.");
            return true;
        }
        if (args[0].equalsIgnoreCase("forcestop")) {
            forceStop(sender, args);
            return true;
        }
        if (args[0].equalsIgnoreCase("debug")) {
            debug(sender, args);
            return true;
        }
        if (args[0].equalsIgnoreCase("potion")) {
            potion(sender, args);
            return true;
        }
        if (args[0].equalsIgnoreCase("ping")) {
            ping(sender, args);
            return true;
        }
        sendUsage(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("practice.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return matching(args[0], SUBCOMMANDS);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("potion")) {
            return matching(args[1], POTION_PROFILES);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            return matching(args[1], DEBUG_SETTINGS);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("debug")
                && args[1].equalsIgnoreCase("damage")) {
            return matching(args[2], TOGGLE_VALUES);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("potion")
                && canonicalPotionProfile(args[1]) != null) {
            return matching(args[2], POTION_SETTINGS);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("ping")) {
            List<String> playerNames = new ArrayList<String>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                playerNames.add(player.getName());
            }
            return matching(args[1], playerNames);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("ping")) {
            return matching(args[2], PING_VALUES);
        }
        return Collections.emptyList();
    }

    private void ping(CommandSender sender, String[] args) {
        if (args.length != 2 && args.length != 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /practice ping <player> <milliseconds|off>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "That player is not online.");
            return;
        }
        if (args.length == 2) {
            sender.sendMessage(ChatColor.GRAY + target.getName() + " has " + ChatColor.WHITE
                    + latencyService.getAdditionalPing(target) + "ms" + ChatColor.GRAY
                    + " of additional ping.");
            return;
        }

        int milliseconds;
        if (args[2].equalsIgnoreCase("off")) {
            milliseconds = 0;
        } else {
            try {
                milliseconds = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                sender.sendMessage(ChatColor.RED + "Ping must be a whole number or off.");
                return;
            }
        }
        if (!PlayerLatencyService.isValidAdditionalPing(milliseconds)) {
            sender.sendMessage(ChatColor.RED + "Additional ping must be between 0 and "
                    + PlayerLatencyService.MAX_ADDITIONAL_PING_MS + "ms.");
            return;
        }
        if (!latencyService.setAdditionalPing(target, milliseconds)) {
            sender.sendMessage(ChatColor.RED + "Could not access that player's connection.");
            return;
        }
        if (milliseconds == 0) {
            sender.sendMessage(ChatColor.GREEN + "Removed artificial ping from " + target.getName() + ".");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Added approximately " + milliseconds
                    + "ms of round-trip ping to " + target.getName() + ".");
        }
    }

    private void forceStop(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /practice forcestop <player>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || (!matchService.forceStop(target.getUniqueId())
                && !botService.forceStop(target.getUniqueId()))) {
            sender.sendMessage(ChatColor.RED + "That player is not in a match.");
            return;
        }
        sender.sendMessage(ChatColor.YELLOW + "Stopped the match containing " + target.getName() + ".");
    }

    private void debug(CommandSender sender, String[] args) {
        if (args.length > 1) {
            if (args.length == 2 && isToggleValue(args[1])) {
                setDamageDebug(sender, args[1]);
                return;
            }
            if (!args[1].equalsIgnoreCase("damage") || args.length > 3) {
                sendDebugUsage(sender);
                return;
            }
            if (args.length == 3) {
                if (!isToggleValue(args[2])) {
                    sendDebugUsage(sender);
                    return;
                }
                setDamageDebug(sender, args[2]);
                return;
            }
            sender.sendMessage(ChatColor.GRAY + "Damage chat: "
                    + (damageDebugService.isEnabled()
                    ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled"));
            return;
        }
        sender.sendMessage(ChatColor.DARK_GRAY + "--- " + ChatColor.RED + "PoppyPractice Debug" + ChatColor.DARK_GRAY + " ---");
        sender.sendMessage(ChatColor.GRAY + "Profiles: " + ChatColor.WHITE + profileManager.size());
        for (MatchQueue queue : queueManager.all()) {
            sender.sendMessage(ChatColor.GRAY + "Queue " + queue.getKitId() + ": "
                    + ChatColor.WHITE + queue.size());
        }
        sender.sendMessage(ChatColor.GRAY + "Matches: " + ChatColor.WHITE + matchManager.size());
        sender.sendMessage(ChatColor.GRAY + "Bot matches: " + ChatColor.WHITE + botService.size());
        sender.sendMessage(ChatColor.GRAY + "Damage chat: "
                + (damageDebugService.isEnabled()
                ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled"));
        for (Arena arena : arenaManager.all()) {
            sender.sendMessage(ChatColor.GRAY + "Arena " + arena.getId() + ": "
                    + ChatColor.WHITE + arena.getState());
        }
    }

    private boolean isToggleValue(String value) {
        return value.equalsIgnoreCase("on") || value.equalsIgnoreCase("off");
    }

    private void setDamageDebug(CommandSender sender, String value) {
        damageDebugService.setEnabled(value.equalsIgnoreCase("on"));
        sender.sendMessage(ChatColor.GREEN + "Damage chat debug mode is now "
                + (damageDebugService.isEnabled() ? "enabled." : "disabled."));
    }

    private void potion(CommandSender sender, String[] args) {
        if (args.length == 1) {
            SplashPotionConfig config = plugin.getSplashPotionConfiguration();
            sender.sendMessage(ChatColor.DARK_GRAY + "--- " + ChatColor.LIGHT_PURPLE
                    + "Splash Potion" + ChatColor.DARK_GRAY + " ---");
            sendPotionProfile(sender, "without-speed", config.getWithoutSpeed());
            sendPotionProfile(sender, "speed-ii", config.getWithSpeedTwo());
            return;
        }
        if (args.length != 4) {
            sendPotionUsage(sender);
            return;
        }

        String profile = canonicalPotionProfile(args[1]);
        String setting = canonicalPotionSetting(args[2]);
        if (profile == null || setting == null) {
            sendPotionUsage(sender);
            return;
        }

        double value;
        try {
            value = Double.parseDouble(args[3]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(ChatColor.RED + "The value must be a number.");
            return;
        }

        boolean valid;
        if (setting.equals("hitbox-size")) {
            valid = SplashPotionConfig.isValidHitboxSize(value);
        } else if (setting.equals("x-speed") || setting.equals("y-speed")) {
            valid = SplashPotionConfig.isValidSpeed(value);
        } else if (setting.equals("spawn-forward-offset") || setting.equals("spawn-height-offset")) {
            valid = SplashPotionConfig.isValidSpawnOffset(value);
        } else {
            valid = SplashPotionConfig.isValidSelfCollisionDelay(value);
        }
        if (!valid) {
            if (setting.equals("hitbox-size")) {
                sender.sendMessage(ChatColor.RED + "hitbox-size must be greater than 0.");
            } else if (setting.equals("x-speed") || setting.equals("y-speed")) {
                sender.sendMessage(ChatColor.RED + setting + " must be greater than or equal to 0.");
            } else if (setting.equals("spawn-forward-offset") || setting.equals("spawn-height-offset")) {
                sender.sendMessage(ChatColor.RED + setting + " must be between -2.0 and 2.0.");
            } else {
                sender.sendMessage(ChatColor.RED + setting + " must be a whole number between 0 and 4.");
            }
            return;
        }

        String path = profile + "." + setting;
        if (setting.equals("self-collision-delay-ticks")) {
            plugin.updateSplashPotionConfiguration(path, (int) value);
            sender.sendMessage(ChatColor.GREEN + "Set splash-potion." + path + " to " + (int) value + ".");
        } else {
            plugin.updateSplashPotionConfiguration(path, value);
            sender.sendMessage(ChatColor.GREEN + "Set splash-potion." + path + " to " + value + ".");
        }
    }

    private void sendPotionProfile(CommandSender sender, String name, Tuning tuning) {
        sender.sendMessage(ChatColor.LIGHT_PURPLE + name + ChatColor.DARK_GRAY + ":");
        sender.sendMessage(ChatColor.GRAY + "  hitbox-size: " + ChatColor.WHITE + tuning.getHitboxSize());
        sender.sendMessage(ChatColor.GRAY + "  x-speed: " + ChatColor.WHITE + tuning.getXSpeed());
        sender.sendMessage(ChatColor.GRAY + "  y-speed: " + ChatColor.WHITE + tuning.getYSpeed());
        sender.sendMessage(ChatColor.GRAY + "  spawn-forward-offset: " + ChatColor.WHITE
                + tuning.getSpawnForwardOffset());
        sender.sendMessage(ChatColor.GRAY + "  spawn-height-offset: " + ChatColor.WHITE
                + tuning.getSpawnHeightOffset());
        sender.sendMessage(ChatColor.GRAY + "  self-collision-delay-ticks: " + ChatColor.WHITE
                + tuning.getSelfCollisionDelayTicks());
    }

    private String canonicalPotionProfile(String profile) {
        if (profile.equalsIgnoreCase("without-speed") || profile.equalsIgnoreCase("normal")
                || profile.equalsIgnoreCase("no-speed")) {
            return "without-speed";
        }
        if (profile.equalsIgnoreCase("speed-ii") || profile.equalsIgnoreCase("with-speed-ii")
                || profile.equalsIgnoreCase("speed2")) {
            return "with-speed-ii";
        }
        return null;
    }

    private String canonicalPotionSetting(String setting) {
        if (setting.equalsIgnoreCase("hitbox-size") || setting.equalsIgnoreCase("hitbox")) {
            return "hitbox-size";
        }
        if (setting.equalsIgnoreCase("x-speed") || setting.equalsIgnoreCase("x")) {
            return "x-speed";
        }
        if (setting.equalsIgnoreCase("y-speed") || setting.equalsIgnoreCase("y")) {
            return "y-speed";
        }
        if (setting.equalsIgnoreCase("spawn-forward-offset")
                || setting.equalsIgnoreCase("spawn-forward") || setting.equalsIgnoreCase("forward")) {
            return "spawn-forward-offset";
        }
        if (setting.equalsIgnoreCase("spawn-height-offset")
                || setting.equalsIgnoreCase("spawn-height") || setting.equalsIgnoreCase("height")) {
            return "spawn-height-offset";
        }
        if (setting.equalsIgnoreCase("self-collision-delay-ticks")
                || setting.equalsIgnoreCase("self-collision-delay")
                || setting.equalsIgnoreCase("self-delay")) {
            return "self-collision-delay-ticks";
        }
        return null;
    }

    private List<String> matching(String input, List<String> candidates) {
        List<String> matches = new ArrayList<String>();
        for (String candidate : candidates) {
            if (candidate.regionMatches(true, 0, input, 0, input.length())) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    private void sendPotionUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED
                + "Usage: /practice potion <without-speed|speed-ii> <setting> <value>");
    }

    private void sendDebugUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED
                + "Usage: /practice debug <on|off> or /practice debug damage <on|off>");
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Usage: /practice <reload|forcestop|debug|potion|ping>");
    }
}
