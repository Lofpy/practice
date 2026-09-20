package com.poppy.practice.listener;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.bot.BotService;
import com.poppy.practice.match.MatchManager;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.service.LobbyService;
import com.poppy.practice.service.MatchService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerDeathListener implements Listener {
    static final long DEATH_ANIMATION_TICKS = 20L;
    private final java.util.Map<java.util.UUID, Location> deathLocations =
            new java.util.HashMap<java.util.UUID, Location>();
    private final PracticePlugin plugin;
    private final MatchManager matchManager;
    private final MatchService matchService;
    private final ProfileManager profileManager;
    private final LobbyService lobbyService;
    private final BotService botService;

    public PlayerDeathListener(PracticePlugin plugin, MatchManager matchManager, MatchService matchService,
                               ProfileManager profileManager, LobbyService lobbyService,
                               BotService botService) {
        this.plugin = plugin;
        this.matchManager = matchManager;
        this.matchService = matchService;
        this.profileManager = profileManager;
        this.lobbyService = lobbyService;
        this.botService = botService;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.setDeathMessage(null);
        event.setKeepInventory(true);
        event.setDroppedExp(0);
        event.getDrops().clear();
        // Fake players have no login/respawn lifecycle. BotService removes them.
        if (botService.isBotEntity(event.getEntity())) return;
        deathLocations.put(event.getEntity().getUniqueId(), event.getEntity().getLocation().clone());
        if (matchManager.getByPlayer(event.getEntity().getUniqueId()) != null) {
            matchService.handleDeath(event.getEntity());
        } else if (botService.getByPlayer(event.getEntity().getUniqueId()) != null) {
            botService.handlePlayerDefeat(event.getEntity().getUniqueId());
        }
        Player player = event.getEntity();
        // Allow native zero-health metadata and the falling animation to reach observers.
        // The result presentation still lasts three seconds; movement is unrestricted
        // after respawn for the remainder of that presentation.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.isDead()) player.spigot().respawn();
        }, DEATH_ANIMATION_TICKS);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        deathLocations.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        final PlayerProfile profile = profileManager.get(player.getUniqueId());
        Location deathLocation = deathLocations.remove(player.getUniqueId());
        if (profile != null && profile.getState() == PlayerState.ENDING && deathLocation != null) {
            event.setRespawnLocation(deathLocation);
            return;
        }
        if (profile == null || profile.getState() == PlayerState.LOBBY) {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        lobbyService.sendToLobby(player);
                    }
                }
            });
        }
    }
}
