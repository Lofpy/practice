package com.poppy.practice.command;

import com.poppy.practice.bot.BotService;
import com.poppy.practice.match.MatchManager;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.queue.MatchQueue;
import com.poppy.practice.queue.QueueManager;
import com.poppy.practice.rating.CertificationResetPlan;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

/** Main-thread Bukkit adapter; never performs an external username lookup. */
public final class TierResetAccess implements TierResetCommand.Access {
    private final Server server;
    private final ProfileManager profiles;
    private final MatchManager matches;
    private final BotService bots;
    private final QueueManager queues;

    public TierResetAccess(Server server, ProfileManager profiles, MatchManager matches,
                           BotService bots, QueueManager queues) {
        this.server = server;
        this.profiles = profiles;
        this.matches = matches;
        this.bots = bots;
        this.queues = queues;
    }

    @Override public UUID resolvePlayer(String name) {
        Player online = server.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        UUID found = null;
        for (OfflinePlayer known : server.getOfflinePlayers()) {
            if (known.getName() != null && known.getName().equalsIgnoreCase(name)) {
                if (found != null && !found.equals(known.getUniqueId())) return null;
                found = known.getUniqueId();
            }
        }
        return found;
    }

    @Override public Collection<String> onlineNames() {
        Collection<String> names = new ArrayList<String>();
        for (Player player : server.getOnlinePlayers()) names.add(player.getName());
        return names;
    }

    @Override public String busyReason(UUID playerOrAll) {
        if (playerOrAll == null ? matches.size() > 0 || bots.size() > 0
                : matches.getByPlayer(playerOrAll) != null || bots.getByPlayer(playerOrAll) != null) {
            return "A selected player is in a match. Wait until all selected players return to the lobby.";
        }
        for (MatchQueue queue : queues.all()) {
            if (playerOrAll == null ? queue.size() > 0 : queue.contains(playerOrAll)) {
                return "A selected player is queued. Leave Queue before resetting certification.";
            }
        }
        for (PlayerProfile profile : profiles.all()) {
            if (playerOrAll != null && !playerOrAll.equals(profile.getPlayerId())) continue;
            PlayerState state = profile.getState();
            if (state == PlayerState.QUEUE || state == PlayerState.STARTING
                    || state == PlayerState.FIGHTING || state == PlayerState.ENDING) {
                return "A selected player is queued or in a match. Return to the lobby before resetting.";
            }
        }
        return null;
    }

    @Override public void notifyReset(CertificationResetPlan plan) {
        for (UUID id : plan.getPlayerIds()) {
            Player player = server.getPlayer(id);
            if (player != null && player.isOnline()) {
                player.sendMessage(ChatColor.YELLOW + "An administrator reset your certification and ELO: "
                        + String.join(", ", plan.getKitIds(id)) + ". Complete 3 new placements per kit with /tier.");
            }
        }
    }
}
