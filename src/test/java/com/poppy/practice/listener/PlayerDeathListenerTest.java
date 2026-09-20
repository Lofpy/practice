package com.poppy.practice.listener;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.bot.BotMatch;
import com.poppy.practice.bot.BotService;
import com.poppy.practice.match.MatchManager;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PlayerDeathListenerTest {
    @Test public void nativeDeathHasTimeToAnimateThenRespawnsWithoutDrops() throws Exception {
        try (Fixture f = new Fixture()) {
            PlayerDeathEvent death = f.death();
            f.listener.onDeath(death);
            assertNull(death.getDeathMessage());
            assertTrue(death.getKeepInventory());
            assertEquals(0, death.getDroppedExp());
            assertTrue(death.getDrops().isEmpty());
            ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
            verify(f.scheduler).runTaskLater(any(Plugin.class), task.capture(), eq(20L));
            verify(f.player.spigot(), never()).respawn();
            task.getValue().run();
            verify(f.player.spigot()).respawn();
        }
    }

    @Test public void fakePlayerDeathDoesNotTryToRespawnAnNpc() throws Exception {
        try (Fixture f = new Fixture()) {
            f.botMatches.put(f.player.getUniqueId(), new BotMatch(UUID.randomUUID(),
                    f.player.getUniqueId(), "nodebuff", "arena"));
            f.listener.onDeath(f.death());
            verify(f.scheduler, never()).runTaskLater(any(Plugin.class), any(Runnable.class), anyLong());
            verify(f.player.spigot(), never()).respawn();
        }
    }

    @Test public void endingRespawnRemainsInArenaForFreeMovement() throws Exception {
        try (Fixture f = new Fixture()) {
            f.profiles.getOrCreate(f.player.getUniqueId()).setState(PlayerState.ENDING);
            f.listener.onDeath(f.death());
            PlayerRespawnEvent respawn = new PlayerRespawnEvent(f.player,
                    new Location(f.world, 0, 70, 0), false);
            f.listener.onRespawn(respawn);
            assertEquals(f.location, respawn.getRespawnLocation());
            assertEquals(PlayerState.ENDING, f.profiles.get(f.player.getUniqueId()).getState());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Field serverField = Bukkit.class.getDeclaredField("server");
        final Object previousServer;
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final World world = mock(World.class);
        final Player player = mock(Player.class);
        final Location location = new Location(world, 1000, 66, 20);
        final ProfileManager profiles = new ProfileManager();
        final HashMap<UUID, BotMatch> botMatches = new HashMap<UUID, BotMatch>();
        final PlayerDeathListener listener;

        Fixture() throws Exception {
            serverField.setAccessible(true);
            previousServer = serverField.get(null);
            Server server = mock(Server.class);
            when(server.getScheduler()).thenReturn(scheduler);
            serverField.set(null, server);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getLocation()).thenReturn(location);
            when(player.isOnline()).thenReturn(true);
            when(player.isDead()).thenReturn(true);
            when(player.spigot()).thenReturn(mock(Player.Spigot.class));
            BotService bots = new ObjenesisStd().newInstance(BotService.class);
            field(bots, "matchesByBot", botMatches);
            field(bots, "matchesByPlayer", new HashMap<UUID, BotMatch>());
            listener = new PlayerDeathListener(new ObjenesisStd().newInstance(PracticePlugin.class),
                    new MatchManager(), null, profiles, null, bots);
        }

        PlayerDeathEvent death() {
            ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
            drops.add(new ItemStack(Material.DIAMOND_SWORD));
            return new PlayerDeathEvent(player, drops, 7, "death");
        }

        private static void field(Object instance, String name, Object value) throws Exception {
            Field field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(instance, value);
        }
        @Override public void close() throws Exception { serverField.set(null, previousServer); }
    }
}
