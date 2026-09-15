package com.poppy.practice.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Administrates the independent Combo profile for current and future matches. */
public final class ComboKbCommand implements CommandExecutor, TabCompleter {
    private static final String PERMISSION = "practice.admin";
    private static final List<String> ACTIONS = Arrays.asList("view", "set");
    private final Settings settings;

    public ComboKbCommand(Settings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("Combo settings are required.");
        }
        this.settings = settings;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to manage Combo knockback.");
            return true;
        }
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("view"))) {
            showSettings(sender, false);
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("view")) {
            if (args[1].equalsIgnoreCase("all")) {
                showSettings(sender, true);
            } else {
                String property = normalize(args[1]);
                Map<String, Object> values = settings.getSettings();
                if (values.containsKey(property)) {
                    showSetting(sender, property, values.get(property));
                } else {
                    unknownProperty(sender, property);
                }
            }
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            String property = normalize(args[1]);
            if (!settings.getSettings().containsKey(property)) {
                unknownProperty(sender, property);
                return true;
            }
            try {
                settings.set(property, args[2]);
                sender.sendMessage(ChatColor.GREEN + "Saved Combo " + property + " = "
                        + settings.getSettings().get(property) + ".");
                sender.sendMessage(ChatColor.GRAY
                        + "Applied immediately to Combo PvP / Bot matches, including countdowns. Other kits are unchanged.");
            } catch (IllegalArgumentException exception) {
                sender.sendMessage(ChatColor.RED + "Invalid Combo setting: " + exception.getMessage());
            } catch (IOException exception) {
                sender.sendMessage(ChatColor.RED
                        + "Could not update Combo settings. Check the server log.");
            } catch (RuntimeException exception) {
                sender.sendMessage(ChatColor.RED
                        + "Could not update Combo settings. Check the server log.");
            }
            return true;
        }
        usage(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return matching(args[0], ACTIONS);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("view")
                || args[0].equalsIgnoreCase("set"))) {
            List<String> properties = new ArrayList<String>(settings.getSettings().keySet());
            if (args[0].equalsIgnoreCase("view")) {
                properties.add(0, "all");
            }
            return matching(args[1], properties);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            Object value = settings.getSettings().get(normalize(args[1]));
            if (value instanceof Boolean) {
                return matching(args[2], Arrays.asList("true", "false"));
            }
            if (value != null) {
                return matching(args[2], Collections.singletonList(String.valueOf(value)));
            }
        }
        return Collections.emptyList();
    }

    private void showSettings(CommandSender sender, boolean includeProjectiles) {
        sender.sendMessage(ChatColor.AQUA + "Combo knockback (current and future PvP / Bot matches)");
        for (Map.Entry<String, Object> entry : settings.getSettings().entrySet()) {
            if (includeProjectiles || !entry.getKey().startsWith("projectiles.")) {
                showSetting(sender, entry.getKey(), entry.getValue());
            }
        }
        usage(sender);
    }

    private void showSetting(CommandSender sender, String property, Object value) {
        sender.sendMessage(ChatColor.GRAY + property + ": " + ChatColor.WHITE + value);
    }

    private void unknownProperty(CommandSender sender, String property) {
        sender.sendMessage(ChatColor.RED + "Unknown Combo property: " + property
                + ". Use /combokb view all.");
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Usage: /combokb view [all|property]");
        sender.sendMessage(ChatColor.YELLOW + "       /combokb set <property> <value>");
    }

    private static String normalize(String property) {
        return property.toLowerCase(Locale.ROOT);
    }

    private static List<String> matching(String prefix, List<String> values) {
        List<String> matches = new ArrayList<String>();
        String normalizedPrefix = normalize(prefix);
        for (String value : values) {
            if (normalize(value).startsWith(normalizedPrefix)) {
                matches.add(value);
            }
        }
        return matches;
    }

    public interface Settings {
        Map<String, Object> getSettings();

        /** Validates, persists, and applies the change to current and future Combo matches. */
        void set(String property, String value) throws IOException;
    }
}
