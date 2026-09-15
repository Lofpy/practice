package com.poppy.practice.listener;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.bot.BotService;
import com.poppy.practice.bot.HitDebugRoomService;
import com.poppy.practice.match.MatchManager;
import com.poppy.practice.network.PlayerLatencyService;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.queue.QueueManager;
import com.poppy.practice.service.LobbyService;
import com.poppy.practice.service.MatchService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerConnectionListener implements Listener {
    private final PracticePlugin plugin;
    private final ProfileManager profileManager;
    private final QueueManager queueManager;
    private final MatchManager matchManager;
    private final MatchService matchService;
    private final LobbyService lobbyService;
    private final BotService botService;
    private final HitDebugRoomService hitDebugRoomService;
    private final PlayerLatencyService latencyService;

    public PlayerConnectionListener(PracticePlugin plugin, ProfileManager profileManager,
                                    QueueManager queueManager, MatchManager matchManager,
                                    MatchService matchService, LobbyService lobbyService,
                                    BotService botService,
                                    HitDebugRoomService hitDebugRoomService,
                                    PlayerLatencyService latencyService) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        this.queueManager = queueManager;
        this.matchManager = matchManager;
        this.matchService = matchService;
        this.lobbyService = lobbyService;
        this.botService = botService;
        this.hitDebugRoomService = hitDebugRoomService;
        this.latencyService = latencyService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(final PlayerJoinEvent event) {
        profileManager.create(event.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                Player player = event.getPlayer();
                if (player.isOnline()) {
                    lobbyService.sendToLobby(player);
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        latencyService.handleQuit(player);
        PlayerProfile profile = profileManager.get(player.getUniqueId());
        if (profile != null && profile.getState() == PlayerState.QUEUE) {
            queueManager.leave(player.getUniqueId(), false);
        }
        if (matchManager.getByPlayer(player.getUniqueId()) != null) {
            matchService.handleQuit(player.getUniqueId());
        }
        botService.handleQuit(player.getUniqueId());
        hitDebugRoomService.handleQuit(player);
        profileManager.remove(player.getUniqueId());
    }
}
