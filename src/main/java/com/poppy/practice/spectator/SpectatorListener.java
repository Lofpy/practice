package com.poppy.practice.spectator;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Locale;

/** Guards include stale SPECTATING profiles, not just currently registered sessions. */
public final class SpectatorListener implements Listener {
    private final SpectatorService spectators;

    public SpectatorListener(SpectatorService spectators) { this.spectators = spectators; }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) { spectators.handleJoin(event.getPlayer()); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) { spectators.handleQuit(event.getPlayer()); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageEarly(EntityDamageEvent event) { cancelDamage(event); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) { cancelDamage(event); }

    private void cancelDamage(EntityDamageEvent event) {
        if (spectating(event.getEntity())) event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) event).getDamager();
            if (spectating(damager) || damager instanceof Projectile
                    && spectatingSource(((Projectile) damager).getShooter())) event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombust(EntityCombustEvent event) {
        if (spectating(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTarget(EntityTargetEvent event) {
        if (spectating(event.getTarget())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectile(ProjectileLaunchEvent event) {
        if (spectatingSource(event.getEntity().getShooter())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSplash(PotionSplashEvent event) {
        if (spectatingSource(event.getPotion().getShooter())) {
            event.setCancelled(true);
            return;
        }
        for (LivingEntity entity : event.getAffectedEntities()) {
            if (spectating(entity)) event.setIntensity(entity, 0.0D);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!spectating(event.getPlayer())) return;
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (spectating(event.getPlayer()) || spectating(event.getRightClicked())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityInteractAt(PlayerInteractAtEntityEvent event) { onEntityInteract(event); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) { onEntityInteract(event); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (spectating(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (causedBySpectator(event.getRemover())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (spectating(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (spectating(event.getWhoClicked())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (spectating(event.getWhoClicked())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(PlayerPickupItemEvent event) {
        if (spectating(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExperience(PlayerExpChangeEvent event) {
        if (spectating(event.getPlayer())) event.setAmount(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (spectating(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (spectating(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        if (spectating(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        if (spectating(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSign(SignChangeEvent event) {
        if (spectating(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (spectating(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (spectating(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFish(PlayerFishEvent event) {
        if (spectating(event.getPlayer()) || spectating(event.getCaught())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicle(VehicleEnterEvent event) {
        if (spectating(event.getEntered())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (causedBySpectator(event.getAttacker())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (causedBySpectator(event.getAttacker())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFlight(PlayerToggleFlightEvent event) {
        if (spectating(event.getPlayer()) && !event.isFlying()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (spectating(event.getPlayer()) && event.getNewGameMode() != org.bukkit.GameMode.ADVENTURE) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || !spectating(event.getPlayer()) || event.getTo() == null) return;
        if (!spectators.isWithinBounds(event.getPlayer().getUniqueId(), event.getTo())) {
            Location safe = spectators.isWithinBounds(event.getPlayer().getUniqueId(), event.getFrom())
                    ? event.getFrom().clone() : spectators.returnLocation(event.getPlayer().getUniqueId());
            if (safe == null) event.setCancelled(true);
            else {
                safe.setYaw(event.getTo().getYaw());
                safe.setPitch(event.getTo().getPitch());
                event.setTo(safe);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent event) {
        if (spectating(event.getPlayer()) && (!spectators.permitsTeleport(event.getPlayer().getUniqueId())
                || !spectators.isWithinBounds(event.getPlayer().getUniqueId(), event.getTo()))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (spectating(event.getPlayer()) && !allowedCommand(event.getMessage())) {
            event.setCancelled(true);
            spectators.blockedCommand(event.getPlayer());
        }
    }

    static boolean allowedCommand(String input) {
        if (input == null) return false;
        String command = input.trim().toLowerCase(Locale.ROOT).split("\\s+", 2)[0];
        if (!command.startsWith("/")) return false;
        command = command.substring(1);
        int colon = command.indexOf(':');
        if (colon >= 0) {
            String namespace = command.substring(0, colon);
            if (!namespace.equals("poppypractice") && !namespace.equals("ascending")) return false;
            command = command.substring(colon + 1);
        }
        return command.equals("spec") || command.equals("spectate") || command.equals("spawn") || command.equals("ping");
    }

    private boolean spectatingSource(ProjectileSource source) {
        return source instanceof Player && spectating((Player) source);
    }
    private boolean causedBySpectator(Entity entity) {
        return spectating(entity) || entity instanceof Projectile && spectatingSource(((Projectile) entity).getShooter());
    }
    private boolean spectating(Entity entity) {
        return entity instanceof Player && spectators.isSpectating(entity.getUniqueId());
    }
}
