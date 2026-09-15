package com.poppy.practice.reach;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.bot.BotService;
import com.poppy.practice.bot.HitDebugRoomService;
import com.poppy.practice.reach.packet.PacketBridge;
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
import org.bukkit.event.player.PlayerVelocityEvent;

/** Bukkit lifecycle and the secondary AttackPermit damage guard. */
public final class ReachGuardListener implements Listener {
    private final PracticePlugin plugin;
    private final ReachGuardService service;
    private final PacketBridge packetBridge;
    private final BotService botService;
    private final HitDebugRoomService hitDebugRoomService;

    public ReachGuardListener(PracticePlugin plugin, ReachGuardService service,
                              PacketBridge packetBridge, BotService botService,
                              HitDebugRoomService hitDebugRoomService) {
        this.plugin = plugin;
        this.service = service;
        this.packetBridge = packetBridge;
        this.botService = botService;
        this.hitDebugRoomService = hitDebugRoomService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        service.handleJoin(player);
        // Install immediately so initial player spawns are observed. Reinstall
        // once next tick after the other network handlers have settled; each
        // bridge uses explicit anchors to preserve Chatter -> Latency -> Reach.
        packetBridge.install(player);
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) packetBridge.install(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        packetBridge.remove(event.getPlayer());
        service.handleQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        service.resetPlayer(event.getPlayer(), ReachGuardService.ResetReason.TELEPORT);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        service.resetPlayer(event.getPlayer(), ReachGuardService.ResetReason.RESPAWN);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        service.resetPlayer(event.getPlayer(), ReachGuardService.ResetReason.WORLD_CHANGE);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        service.resetPlayer(event.getEntity(), ReachGuardService.ResetReason.DEATH);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        service.markKnockback(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMeleeDamageGuard(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)
                || !(event.getEntity() instanceof Player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        Player attacker = (Player) event.getDamager();
        Player target = (Player) event.getEntity();
        if (attacker.getUniqueId().equals(target.getUniqueId())) return;

        // Server-driven fake-player attacks have no inbound ATTACK packet.
        if (botService != null && botService.isBotEntity(attacker)) return;
        if (hitDebugRoomService != null && hitDebugRoomService.isDebugBot(attacker)) return;

        if (!service.allowMeleeDamage(attacker.getUniqueId(), target.getUniqueId(),
                System.nanoTime())) {
            event.setCancelled(true);
            event.setDamage(0.0D);
        }
    }
}
