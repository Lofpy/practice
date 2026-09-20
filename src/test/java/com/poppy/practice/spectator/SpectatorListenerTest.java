package com.poppy.practice.spectator;

import com.poppy.practice.player.PlayerState;
import com.poppy.practice.spectator.SpectatorFixture.PlayerStub;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class SpectatorListenerTest {
    @Test public void commandWhitelistPreventsQueueDuelAndNamespaceBypasses() {
        assertTrue(SpectatorListener.allowedCommand("/spec"));
        assertTrue(SpectatorListener.allowedCommand(" /SpEc leave "));
        assertTrue(SpectatorListener.allowedCommand("/spectate Alice"));
        assertTrue(SpectatorListener.allowedCommand("/poppypractice:spec leave"));
        assertTrue(SpectatorListener.allowedCommand("/ascending:spawn"));
        assertTrue(SpectatorListener.allowedCommand("/ping"));
        assertFalse(SpectatorListener.allowedCommand("/queue join"));
        assertFalse(SpectatorListener.allowedCommand("/duel accept Alice"));
        assertFalse(SpectatorListener.allowedCommand("/bot"));
        assertFalse(SpectatorListener.allowedCommand("/tier"));
        assertFalse(SpectatorListener.allowedCommand("/other:spawn"));
        assertFalse(SpectatorListener.allowedCommand("/minecraft:tp Alice"));
        assertFalse(SpectatorListener.allowedCommand("/spectatorbypass"));
        assertFalse(SpectatorListener.allowedCommand(""));
        assertFalse(SpectatorListener.allowedCommand(null));
    }

    @Test public void staleSpectatingStateStillBlocksCommandsAndInteraction() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer");
        f.profiles.get(viewer.id).setState(PlayerState.SPECTATING);
        SpectatorListener listener = new SpectatorListener(f.service);
        PlayerCommandPreprocessEvent command = new PlayerCommandPreprocessEvent(viewer.player, "/duel accept Alice",
                java.util.Collections.emptySet());
        listener.onCommand(command);
        assertTrue(command.isCancelled());
        PlayerInteractEvent event = new PlayerInteractEvent(viewer.player, Action.RIGHT_CLICK_AIR,
                new ItemStack(Material.ENDER_PEARL), null, BlockFace.SELF);
        listener.onInteract(event);
        assertTrue(event.isCancelled());
        assertEquals(Event.Result.DENY, event.useItemInHand());
        assertEquals(Event.Result.DENY, event.useInteractedBlock());
        assertTrue(f.service.leave(viewer.player, false));
        assertEquals(PlayerState.LOBBY, f.profiles.get(viewer.id).getState());
    }

    @Test public void damageIsBlockedInBothDirectionsIncludingProjectilesAndVoid() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer"), fighter = f.player("Fighter");
        f.profiles.get(viewer.id).setState(PlayerState.SPECTATING);
        SpectatorListener listener = new SpectatorListener(f.service);
        EntityDamageEvent incoming = new EntityDamageEvent(viewer.player, EntityDamageEvent.DamageCause.VOID, 20.0D);
        listener.onDamageEarly(incoming);
        assertTrue(incoming.isCancelled());
        EntityDamageByEntityEvent outgoing = new EntityDamageByEntityEvent(viewer.player, fighter.player,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 5.0D);
        listener.onDamage(outgoing);
        assertTrue(outgoing.isCancelled());
        Projectile projectile = projectile(Projectile.class, viewer.player);
        EntityDamageByEntityEvent thrown = new EntityDamageByEntityEvent(projectile, fighter.player,
                EntityDamageEvent.DamageCause.PROJECTILE, 5.0D);
        listener.onDamage(thrown);
        assertTrue(thrown.isCancelled());
        ProjectileLaunchEvent launch = new ProjectileLaunchEvent(projectile);
        listener.onProjectile(launch);
        assertTrue(launch.isCancelled());
        EntityDamageEvent unrelated = new EntityDamageEvent(fighter.player, EntityDamageEvent.DamageCause.FALL, 1.0D);
        listener.onDamage(unrelated);
        assertFalse(unrelated.isCancelled());
    }

    @Test public void potionEffectsDoNotReachViewersAndSpectatorPotionHasNoEffect() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer"), fighter = f.player("Fighter");
        f.profiles.get(viewer.id).setState(PlayerState.SPECTATING);
        SpectatorListener listener = new SpectatorListener(f.service);
        Map<LivingEntity, Double> affected = new HashMap<LivingEntity, Double>();
        affected.put(viewer.player, 1.0D);
        affected.put(fighter.player, 1.0D);
        PotionSplashEvent splash = new PotionSplashEvent(projectile(ThrownPotion.class, fighter.player), affected);
        listener.onSplash(splash);
        assertEquals(0.0D, splash.getIntensity(viewer.player), 0.0D);
        assertEquals(1.0D, splash.getIntensity(fighter.player), 0.0D);
        assertFalse(splash.isCancelled());
        PotionSplashEvent thrown = new PotionSplashEvent(projectile(ThrownPotion.class, viewer.player), affected);
        listener.onSplash(thrown);
        assertTrue(thrown.isCancelled());
    }

    @Test public void pickupDropConsumeAndEntityInteractionsAreCancelled() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer"), fighter = f.player("Fighter");
        f.profiles.get(viewer.id).setState(PlayerState.SPECTATING);
        SpectatorListener listener = new SpectatorListener(f.service);
        Item item = (Item) Proxy.newProxyInstance(Item.class.getClassLoader(), new Class<?>[]{Item.class},
                (proxy, method, args) -> SpectatorFixture.defaultValue(method.getReturnType()));
        PlayerPickupItemEvent pickup = new PlayerPickupItemEvent(viewer.player, item, 0);
        listener.onPickup(pickup);
        assertTrue(pickup.isCancelled());
        PlayerDropItemEvent drop = new PlayerDropItemEvent(viewer.player, item);
        listener.onDrop(drop);
        assertTrue(drop.isCancelled());
        PlayerItemConsumeEvent consume = new PlayerItemConsumeEvent(viewer.player, new ItemStack(Material.GOLDEN_APPLE));
        listener.onConsume(consume);
        assertTrue(consume.isCancelled());
        PlayerInteractEntityEvent entity = new PlayerInteractEntityEvent(viewer.player, fighter.player);
        listener.onEntityInteract(entity);
        assertTrue(entity.isCancelled());
        PlayerInteractEntityEvent interactWithViewer = new PlayerInteractEntityEvent(fighter.player, viewer.player);
        listener.onEntityInteract(interactWithViewer);
        assertTrue(interactWithViewer.isCancelled());
    }

    @Test public void flightCannotBeDisabledOrEscalatedToCreativeMode() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer");
        f.profiles.get(viewer.id).setState(PlayerState.SPECTATING);
        SpectatorListener listener = new SpectatorListener(f.service);
        PlayerToggleFlightEvent flight = new PlayerToggleFlightEvent(viewer.player, false);
        listener.onFlight(flight);
        assertTrue(flight.isCancelled());
        PlayerGameModeChangeEvent mode = new PlayerGameModeChangeEvent(viewer.player, GameMode.CREATIVE);
        listener.onGameMode(mode);
        assertTrue(mode.isCancelled());
        PlayerGameModeChangeEvent adventure = new PlayerGameModeChangeEvent(viewer.player, GameMode.ADVENTURE);
        listener.onGameMode(adventure);
        assertFalse(adventure.isCancelled());
    }

    @Test public void arenaEscapeIsClampedAndUnownedTeleportsAreRejected() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer"), first = f.player("First"), second = f.player("Second");
        f.match(first, second);
        SpectatorListener listener = new SpectatorListener(f.service);
        viewer.teleportCallback = () -> {
            PlayerTeleportEvent own = new PlayerTeleportEvent(viewer.player, viewer.location,
                    new Location(f.world, 0, 68, 0), PlayerTeleportEvent.TeleportCause.PLUGIN);
            listener.onTeleport(own);
            assertFalse(own.isCancelled());
        };
        assertTrue(f.service.spectate(viewer.player, first.player));
        PlayerMoveEvent move = new PlayerMoveEvent(viewer.player, viewer.location,
                new Location(f.world, 1000, 68, 0));
        listener.onMove(move);
        assertEquals(viewer.location.getX(), move.getTo().getX(), 0.0D);
        PlayerTeleportEvent escape = new PlayerTeleportEvent(viewer.player, viewer.location,
                new Location(f.world, 10, 68, 0), PlayerTeleportEvent.TeleportCause.ENDER_PEARL);
        listener.onTeleport(escape);
        assertTrue(escape.isCancelled());
        f.service.leave(viewer.player, false);
        PlayerTeleportEvent lobby = new PlayerTeleportEvent(viewer.player, viewer.location,
                new Location(f.world, 1000, 68, 0), PlayerTeleportEvent.TeleportCause.PLUGIN);
        listener.onTeleport(lobby);
        assertFalse(lobby.isCancelled());
    }

    @SuppressWarnings("unchecked") private static <T extends Projectile> T projectile(Class<T> type, Entity shooter) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("getShooter")) return shooter;
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == args[0];
            return SpectatorFixture.defaultValue(method.getReturnType());
        });
    }
}
