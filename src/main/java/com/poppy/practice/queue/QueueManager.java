package com.poppy.practice.queue;

import com.poppy.practice.arena.Arena;
import com.poppy.practice.arena.ArenaManager;
import com.poppy.practice.kit.Kit;
import com.poppy.practice.kit.KitManager;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.rating.EloCalculator;
import com.poppy.practice.rating.RatingService;
import com.poppy.practice.service.MatchService;
import com.poppy.practice.service.LobbyService;
import com.poppy.practice.service.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

public final class QueueManager {
    private final ProfileManager profileManager;
    private final KitManager kitManager;
    private final ArenaManager arenaManager;
    private final MessageService messageService;
    private final LobbyService lobbyService;
    private final Map<String, MatchQueue> queues = new LinkedHashMap<String, MatchQueue>();
    private final Set<String> matchingKits = new HashSet<String>();
    private MatchService matchService;
    private RatingService ratingService;
    private boolean shuttingDown;
    private boolean matchingAll;

    public QueueManager(ProfileManager profileManager, KitManager kitManager,
                        ArenaManager arenaManager, MessageService messageService,
                        LobbyService lobbyService) {
        this.profileManager = profileManager;
        this.kitManager = kitManager;
        this.arenaManager = arenaManager;
        this.messageService = messageService;
        this.lobbyService = lobbyService;
        for (Kit kit : kitManager.all()) {
            queues.put(kit.getId(), new MatchQueue(kit.getId()));
        }
    }

    public void setMatchService(MatchService matchService) {
        this.matchService = matchService;
    }

    public void setRatingService(RatingService ratingService) {
        if (ratingService == null) {
            throw new IllegalArgumentException("Rating service cannot be null");
        }
        this.ratingService = ratingService;
    }

    public boolean join(Player player) {
        PlayerProfile profile = profileManager.getOrCreate(player.getUniqueId());
        if (profile.getState() == PlayerState.QUEUE) {
            messageService.send(player, "already-queued");
            return false;
        }
        if (profile.getState() != PlayerState.LOBBY || shuttingDown) {
            messageService.send(player, "queue-not-allowed");
            return false;
        }
        Kit kit = kitManager.get(profile.getSelectedKitId());
        if (kit == null) {
            messageService.send(player, "no-kit-selected");
            return false;
        }
        if (ratingService != null && !ratingService.isQualified(player.getUniqueId(), kit.getId())) {
            player.sendMessage(org.bukkit.ChatColor.RED + "Ranked Queue: " + kit.getDisplayName()
                    + org.bukkit.ChatColor.RED + " の認定戦を3回完了してください。 ("
                    + ratingService.getPlacementCount(player.getUniqueId(), kit.getId()) + "/3)"
                    + org.bukkit.ChatColor.GRAY + " /tier " + kit.getId());
            return false;
        }

        MatchQueue queue = queues.get(kit.getId());
        if (queue == null) {
            queue = new MatchQueue(kit.getId());
            queues.put(kit.getId(), queue);
        }
        if (!queue.add(player.getUniqueId())) {
            messageService.send(player, "already-queued");
            return false;
        }
        profile.setQueuedKitId(kit.getId());
        profile.setState(PlayerState.QUEUE);
        lobbyService.updateQueueItem(player);
        messageService.send(player, "joined-queue", "kit", kit.getDisplayName());
        tryMatch(kit.getId());
        return true;
    }

    public boolean leave(Player player, boolean notify) {
        return leave(player.getUniqueId(), notify);
    }

    public boolean leave(UUID playerId, boolean notify) {
        PlayerProfile profile = profileManager.get(playerId);
        if (profile == null || profile.getState() != PlayerState.QUEUE) {
            removeFromEveryQueue(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (notify && player != null) {
                messageService.send(player, "not-queued");
            }
            return false;
        }
        removeFromEveryQueue(playerId);
        profile.leaveQueue();
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            lobbyService.updateQueueItem(player);
            if (notify) {
                messageService.send(player, "left-queue");
            }
        }
        return true;
    }

    /** Shared arenas becoming free can unblock a different kit's queue. */
    public void tryMatchAll() {
        tryMatchAll(this::tryMatch);
    }

    void tryMatchAll(Consumer<String> retry) {
        if (shuttingDown || matchingAll) {
            return;
        }
        matchingAll = true;
        try {
            for (String kitId : new ArrayList<String>(queues.keySet())) {
                retry.accept(kitId);
            }
        } finally {
            matchingAll = false;
        }
    }

    public void tryMatch(String kitId) {
        if (shuttingDown || matchService == null) {
            return;
        }
        MatchQueue queue = queues.get(kitId);
        if (queue == null || !matchingKits.add(kitId)) {
            return;
        }
        try {
            while (queue.size() >= 2) {
                List<UUID> eligible = eligiblePlayers(queue, kitId);
                UUID[] pair = selectPair(eligible, ratingService == null ? null
                        : playerId -> ratingService.getRatingMilli(playerId, kitId));
                if (pair == null) {
                    return;
                }

                Arena arena = arenaManager.acquireAvailable(kitId);
                if (arena == null) {
                    notifyNoArena(queue);
                    return;
                }
                queue.remove(pair[0]);
                queue.remove(pair[1]);
                // startMatch owns this reservation, including its failure cleanup.
                matchService.startMatch(pair[0], pair[1], kitId, arena);
            }
        } finally {
            matchingKits.remove(kitId);
        }
    }

    private List<UUID> eligiblePlayers(MatchQueue queue, String kitId) {
        List<UUID> eligible = new ArrayList<UUID>();
        for (UUID playerId : queue.snapshot()) {
            Player player = Bukkit.getPlayer(playerId);
            PlayerProfile profile = profileManager.get(playerId);
            if (player != null && player.isOnline() && profile != null
                    && profile.getState() == PlayerState.QUEUE
                    && kitId.equals(profile.getQueuedKitId())
                    && (ratingService == null || ratingService.isQualified(playerId, kitId))) {
                eligible.add(playerId);
                continue;
            }
            queue.remove(playerId);
            if (profile != null && profile.getState() == PlayerState.QUEUE
                    && kitId.equals(profile.getQueuedKitId())) {
                profile.leaveQueue();
                if (player != null && player.isOnline()) {
                    lobbyService.updateQueueItem(player);
                }
            }
        }
        return eligible;
    }

    /** Select the oldest player with any compatible opponent, without blocking later eligible pairs. */
    static UUID[] selectPair(List<UUID> eligible, ToLongFunction<UUID> ratings) {
        for (int first = 0; first < eligible.size() - 1; first++) {
            for (int second = first + 1; second < eligible.size(); second++) {
                if (ratings == null || EloCalculator.inMatchRange(
                        ratings.applyAsLong(eligible.get(first)), ratings.applyAsLong(eligible.get(second)))) {
                    return new UUID[] { eligible.get(first), eligible.get(second) };
                }
            }
        }
        return null;
    }

    private void notifyNoArena(MatchQueue queue) {
        for (UUID playerId : queue.snapshot()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                messageService.send(player, "no-arena");
            }
        }
    }

    private void removeFromEveryQueue(UUID playerId) {
        for (MatchQueue queue : queues.values()) {
            queue.remove(playerId);
        }
    }

    public int size(String kitId) {
        MatchQueue queue = queues.get(kitId);
        return queue == null ? 0 : queue.size();
    }

    public Collection<MatchQueue> all() {
        return new ArrayList<MatchQueue>(queues.values());
    }

    public void clear() {
        shuttingDown = true;
        for (MatchQueue queue : queues.values()) {
            queue.clear();
        }
        for (PlayerProfile profile : profileManager.all()) {
            if (profile.getState() == PlayerState.QUEUE) {
                profile.leaveQueue();
            }
        }
    }
}
