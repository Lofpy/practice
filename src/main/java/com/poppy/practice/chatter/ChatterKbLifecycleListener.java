package com.poppy.practice.chatter;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.bot.BotMatch;
import com.poppy.practice.bot.BotService;
import com.poppy.practice.match.BoxingRules;
import com.poppy.practice.match.Match;
import com.poppy.practice.match.MatchManager;
import com.poppy.practice.match.MatchState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.UUID;

public final class ChatterKbLifecycleListener implements Listener {
    private final ChatterKbService service;
    private final PracticePlugin plugin;
    private final ProtocolAdapter47 protocolAdapter;
    private final MatchManager matchManager;
    private final BotService botService;

    public ChatterKbLifecycleListener(PracticePlugin plugin, ChatterKbService service,
                                      ProtocolAdapter47 protocolAdapter) {
        this(plugin, service, protocolAdapter, null);
    }

    public ChatterKbLifecycleListener(PracticePlugin plugin, ChatterKbService service,
                                      ProtocolAdapter47 protocolAdapter, MatchManager matchManager) {
        this(plugin, service, protocolAdapter, matchManager, null);
    }

    public ChatterKbLifecycleListener(PracticePlugin plugin, ChatterKbService service,
                                      ProtocolAdapter47 protocolAdapter, MatchManager matchManager,
                                      BotService botService) {
        this.plugin = plugin;
        this.service = service;
        this.protocolAdapter = protocolAdapter;
        this.matchManager = matchManager;
        this.botService = botService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        service.handleJoin(player.getUniqueId(), player.getEntityId());
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) protocolAdapter.install(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        protocolAdapter.remove(event.getPlayer());
        service.handleQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        service.reset(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        service.reset(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        service.reset(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        service.reset(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMeleeDamage(EntityDamageByEntityEvent event) {
        if (!shouldRecordCombatDamage(event, matchManager, botService)) {
            return;
        }
        service.recordCombatDamage(event.getEntity().getUniqueId(),
                event.getDamager().getUniqueId(), System.nanoTime());
    }

    static boolean shouldRecordCombatDamage(EntityDamageByEntityEvent event,
                                            MatchManager matchManager) {
        return shouldRecordCombatDamage(event, matchManager, null);
    }

    static boolean shouldRecordCombatDamage(EntityDamageByEntityEvent event,
                                            MatchManager matchManager, BotService botService) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player)
                || !(event.getDamager() instanceof Player)) {
            return false;
        }
        if (event.getFinalDamage() > 0.0D) {
            return true;
        }
        if (event.getFinalDamage() != 0.0D
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return false;
        }
        Match match = matchManager == null ? null
                : matchManager.getByPlayer(event.getEntity().getUniqueId());
        // Boxing suppresses health damage, not the normal hit/velocity sequence.
        // Include its winning hit, whose result may already be locked at MONITOR.
        if (match != null && BoxingRules.isBoxing(match.getKitId())
                && match.getState() == MatchState.FIGHTING
                && match == matchManager.getByPlayer(event.getDamager().getUniqueId())
                && event.getDamager().getUniqueId().equals(
                        match.getOpponent(event.getEntity().getUniqueId()))) {
            return true;
        }
        return shouldRecordBotBoxingDamage(event,
                getBotMatch(botService, event.getEntity().getUniqueId()),
                getBotMatch(botService, event.getDamager().getUniqueId()));
    }

    static boolean shouldRecordBotBoxingDamage(EntityDamageByEntityEvent event,
                                               BotMatch victimMatch, BotMatch attackerMatch) {
        // Do not use canScoreBoxingHit: the accepted 100th hit already has a winner.
        return !event.isCancelled() && event.getEntity() instanceof Player
                && event.getDamager() instanceof Player
                && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && event.getFinalDamage() == 0.0D
                && victimMatch != null && victimMatch == attackerMatch
                && victimMatch.isBoxing() && victimMatch.getState() == MatchState.FIGHTING
                && event.getDamager().getUniqueId().equals(
                        victimMatch.getOpponent(event.getEntity().getUniqueId()));
    }

    private static BotMatch getBotMatch(BotService botService, UUID participantId) {
        if (botService == null) {
            return null;
        }
        BotMatch match = botService.getByPlayer(participantId);
        return match != null ? match : botService.getByBot(participantId);
    }
}
