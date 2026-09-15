package com.poppy.practice.command;

import com.poppy.practice.rating.CertificationResetPlan;
import com.poppy.practice.rating.CertificationResetResult;
import com.poppy.practice.rating.RatingService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Explicit, administrator-only reset previews; no operation runs on the first command. */
public final class TierResetCommand implements CommandExecutor, TabCompleter {
    private static final long CONFIRMATION_MILLIS = 60000L;
    private static final List<String> KITS = Arrays.asList("nodebuff", "boxing", "combo", "all");
    private final RatingService ratings;
    private final Access access;
    private final Logger logger;
    private final LongSupplier clock;
    private final Map<String, Pending> pending = new HashMap<String, Pending>();

    public TierResetCommand(RatingService ratings, Access access, Logger logger) {
        this(ratings, access, logger, System::currentTimeMillis);
    }

    TierResetCommand(RatingService ratings, Access access, Logger logger, LongSupplier clock) {
        if (ratings == null || access == null || logger == null || clock == null) {
            throw new IllegalArgumentException("Reset command dependencies cannot be null");
        }
        this.ratings = ratings;
        this.access = access;
        this.logger = logger;
        this.clock = clock;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("practice.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }
        removeExpired();
        if (args.length == 1 && args[0].equalsIgnoreCase("cancel")) {
            pending.remove(senderKey(sender));
            sender.sendMessage(ChatColor.YELLOW + "Certification reset cancelled. No results were changed.");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("confirm")) {
            confirm(sender, args[1]);
        } else if (args.length >= 1 && args.length <= 2
                && !args[0].equalsIgnoreCase("confirm") && !args[0].equalsIgnoreCase("cancel")) {
            preview(sender, args);
        } else {
            usage(sender);
        }
        return true;
    }

    private void preview(CommandSender sender, String[] args) {
        // A new selection invalidates the previous confirmation, even on a typo.
        pending.remove(senderKey(sender));
        String kit = args.length == 2 ? args[1].toLowerCase(Locale.ROOT) : "all";
        if (!KITS.contains(kit)) {
            usage(sender);
            return;
        }
        UUID target = null;
        if (!args[0].equalsIgnoreCase("all")) {
            target = parseUuid(args[0]);
            if (target == null) {
                target = access.resolvePlayer(args[0]);
            }
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Unknown or ambiguous player. Use a known name or full UUID.");
                return;
            }
        }
        String blocked = access.busyReason(target);
        if (blocked != null) {
            sender.sendMessage(ChatColor.RED + blocked);
            return;
        }
        try {
            CertificationResetPlan plan = ratings.previewCertificationReset(target,
                    kit.equals("all") ? null : kit);
            if (plan.getRecordCount() == 0) {
                sender.sendMessage(ChatColor.YELLOW + "No certification results match that selection.");
                return;
            }
            String token = UUID.randomUUID().toString().substring(0, 8);
            String selection = (target == null ? "ALL PLAYERS" : args[0] + " (" + target + ")")
                    + " / " + kit;
            pending.put(senderKey(sender), new Pending(plan, target, selection, token,
                    clock.getAsLong() + CONFIRMATION_MILLIS));
            sender.sendMessage(ChatColor.GOLD + "Reset preview: " + selection);
            sender.sendMessage(ChatColor.YELLOW + "Players: " + plan.getPlayerIds().size()
                    + " | Player/kit records: " + plan.getRecordCount()
                    + " | Placements: " + plan.getPlacementCount());
            sender.sendMessage(ChatColor.RED + "This clears certification scores and ELO for the selected kits"
                    + " (including ranked ELO). Qualified ratings: " + plan.getQualifiedCount() + ".");
            sender.sendMessage(ChatColor.GRAY + "Results will be backed up first. Selected kits return to 0/3."
                    + " Other players/kits keep their ratings.");
            sender.sendMessage(ChatColor.YELLOW + "Within 60 seconds: " + ChatColor.WHITE
                    + "/tierreset confirm " + token);
            sender.sendMessage(ChatColor.GRAY + "Cancel: /tierreset cancel");
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "Could not preview certification reset", failure);
            sender.sendMessage(ChatColor.RED + "Reset preview failed. No reset was performed; see the server log.");
        }
    }

    private void confirm(CommandSender sender, String token) {
        Pending request = pending.get(senderKey(sender));
        if (request == null || !request.token.equals(token)) {
            sender.sendMessage(ChatColor.RED + "No matching confirmation, or it expired. Preview the reset again.");
            return;
        }
        pending.remove(senderKey(sender));
        // A player may have joined a queue or started a match since the preview.
        String blocked = access.busyReason(request.player);
        if (blocked != null) {
            sender.sendMessage(ChatColor.RED + blocked + " Preview the reset again after it finishes.");
            return;
        }
        CertificationResetResult result;
        try {
            result = ratings.resetCertifications(request.plan, senderKey(sender));
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "Certification reset did not complete: " + request.selection, failure);
            sender.sendMessage(ChatColor.RED + "Reset did not complete: " + failure.getMessage());
            sender.sendMessage(ChatColor.GRAY + "See the server log. If data changed since the preview, preview again.");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Certification reset complete: " + result.getRecordCount()
                + " player/kit records, " + result.getPlacementCount() + " placements. Progress is now 0/3.");
        sender.sendMessage(ChatColor.GRAY + "Backup: " + result.getBackupDirectory());
        logger.info("Certification reset by " + senderKey(sender) + ": " + request.selection
                + "; backup=" + result.getBackupDirectory());
        try {
            access.notifyReset(request.plan);
        } catch (RuntimeException failure) {
            // Notifications cannot turn an already committed reset into a reported failure.
            logger.log(Level.WARNING, "Reset succeeded but an online notification failed", failure);
        }
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("practice.admin")) return Collections.emptyList();
        removeExpired();
        List<String> choices = new ArrayList<String>();
        if (args.length == 1) {
            choices.addAll(Arrays.asList("all", "confirm", "cancel"));
            choices.addAll(access.onlineNames());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("confirm")) {
            Pending request = pending.get(senderKey(sender));
            if (request != null) choices.add(request.token);
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("cancel")) {
            choices.addAll(KITS);
        }
        if (args.length == 0) return choices;
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        choices.removeIf(value -> !value.toLowerCase(Locale.ROOT).startsWith(prefix));
        return choices;
    }

    private void removeExpired() {
        long now = clock.getAsLong();
        pending.values().removeIf(value -> value.expiresAt <= now);
    }

    private static UUID parseUuid(String value) {
        try {
            UUID id = UUID.fromString(value);
            return id.toString().equalsIgnoreCase(value) ? id : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static String senderKey(CommandSender sender) {
        return sender instanceof Player ? "player:" + ((Player) sender).getUniqueId()
                : sender.getClass().getName() + ":" + sender.getName();
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "/tierreset <player|UUID|all> [nodebuff|boxing|combo|all]");
        sender.sendMessage(ChatColor.GRAY + "/tierreset confirm <token> | /tierreset cancel");
    }

    public interface Access {
        UUID resolvePlayer(String knownName);
        Collection<String> onlineNames();
        /** Null player means all players. Return null only when reset can safely run now. */
        String busyReason(UUID playerOrAll);
        void notifyReset(CertificationResetPlan plan);
    }

    private static final class Pending {
        final CertificationResetPlan plan;
        final UUID player;
        final String selection;
        final String token;
        final long expiresAt;

        Pending(CertificationResetPlan plan, UUID player, String selection, String token, long expiresAt) {
            this.plan = plan;
            this.player = player;
            this.selection = selection;
            this.token = token;
            this.expiresAt = expiresAt;
        }
    }
}
