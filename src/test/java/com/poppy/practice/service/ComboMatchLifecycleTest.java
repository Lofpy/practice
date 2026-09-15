package com.poppy.practice.service;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.arena.Arena;
import com.poppy.practice.arena.ArenaManager;
import com.poppy.practice.arena.ArenaState;
import com.poppy.practice.combat.ComboCombatService;
import com.poppy.practice.config.ComboConfig;
import com.poppy.practice.kit.KitManager;
import com.poppy.practice.match.Match;
import com.poppy.practice.match.MatchEndReason;
import com.poppy.practice.match.MatchManager;
import com.poppy.practice.match.MatchState;
import com.poppy.practice.network.PlayerPingService;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.queue.QueueManager;
import dev.cobblesword.nachospigot.knockback.KnockbackProfile;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemFactory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginLogger;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.Test;
import org.mockito.InOrder;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Exercises the real match completion and lobby cleanup, not just a service snapshot in isolation. */
public class ComboMatchLifecycleTest {
    @Test
    public void liveUpdatesReachBothFightingPlayersAndCleanupRestoresTheirOriginalSettings() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Match match = fixture.register("combo");
            ComboConfig firstUpdate = fixture.combo.getConfiguration().withSetting("horizontal", "0.45");

            fixture.combo.updateLive(firstUpdate);

            assertEquals(MatchState.FIGHTING, match.getState());
            assertSame(match, fixture.matches.getByPlayer(fixture.first.id));
            assertSame(match, fixture.matches.getByPlayer(fixture.second.id));
            for (Participant participant : new Participant[] { fixture.first, fixture.second }) {
                assertEquals(0.45D, participant.currentProfile.getHorizontal(), 0.0D);
                assertEquals(4, participant.currentMaximumNoDamageTicks);
                assertTrue(fixture.combo.isApplied(participant.id));
            }
            assertNotSame(fixture.first.currentProfile, fixture.second.currentProfile);

            ComboConfig secondUpdate = firstUpdate.withSetting("horizontal", "0.22")
                    .withSetting("no-damage-ticks", "1");
            fixture.combo.updateLive(secondUpdate);

            for (Participant participant : new Participant[] { fixture.first, fixture.second }) {
                assertEquals(0.22D, participant.currentProfile.getHorizontal(), 0.0D);
                assertEquals(2, participant.currentMaximumNoDamageTicks);
            }
            assertTrue(fixture.service.forceStop(fixture.first.id));
            fixture.assertFinishedAndRestored(match);
            assertRestoredBeforeInventoryCleanup(fixture.first);
            assertRestoredBeforeInventoryCleanup(fixture.second);
            assertSame(secondUpdate, fixture.combo.getConfiguration());
        }
    }

    @Test
    public void endMatchRestoresBothPlayersBeforeLobbyInventoryCleanup() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Match match = fixture.register("combo");

            fixture.service.endMatch(match, null, null, MatchEndReason.INTERNAL_ERROR);

            fixture.assertFinishedAndRestored(match);
            assertRestoredBeforeInventoryCleanup(fixture.first);
            assertRestoredBeforeInventoryCleanup(fixture.second);
            verify(fixture.first.player).setGameMode(GameMode.ADVENTURE);
            verify(fixture.second.player).setGameMode(GameMode.ADVENTURE);
        }
    }

    @Test
    public void resultFailureAndFirstLobbyFailureStillRestoreBothParticipants() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Match match = fixture.register("combo");
            doThrow(new IllegalStateException("result presentation failed"))
                    .when(fixture.first.player).sendMessage(anyString());
            doThrow(new IllegalStateException("first inventory failed"))
                    .when(fixture.first.player).closeInventory();

            assertTrue(fixture.service.forceStop(fixture.first.id));

            fixture.assertFinishedAndRestored(match);
            assertRestoredBeforeInventoryCleanup(fixture.first);
            assertRestoredBeforeInventoryCleanup(fixture.second);
            verify(fixture.second.player).setGameMode(GameMode.ADVENTURE);
            verify(fixture.logger, times(2)).log(eq(Level.WARNING), anyString(), any(RuntimeException.class));
        }
    }

    @Test
    public void forceStopRestoresOnceAndCannotEndTheSameMatchTwice() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Match match = fixture.register("combo");

            assertTrue(fixture.service.forceStop(fixture.first.id));
            assertFalse(fixture.service.forceStop(fixture.second.id));

            fixture.assertFinishedAndRestored(match);
            verify(fixture.first.player).setKnockbackProfile(fixture.first.originalProfile);
            verify(fixture.second.player).setKnockbackProfile(fixture.second.originalProfile);
            verify(fixture.first.player).closeInventory();
            verify(fixture.second.player).closeInventory();
        }
    }

    @Test
    public void quitRestoresCapturedPlayerEvenAfterBukkitStopsFindingThem() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Match match = fixture.register("combo");
            fixture.onlinePlayers.remove(fixture.first.id);
            when(fixture.first.player.isOnline()).thenReturn(false);

            fixture.service.handleQuit(fixture.first.id);

            fixture.assertFinishedAndRestored(match);
            verify(fixture.first.player, never()).closeInventory();
            assertRestoredBeforeInventoryCleanup(fixture.second);
        }
    }

    @Test
    public void shutdownRestoresBothDisconnectedPlayersAndReleasesArena() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Match match = fixture.register("combo");
            fixture.onlinePlayers.clear();
            when(fixture.first.player.isOnline()).thenReturn(false);
            when(fixture.second.player.isOnline()).thenReturn(false);

            fixture.service.shutdown();

            fixture.assertFinishedAndRestored(match);
            verify(fixture.first.player, never()).closeInventory();
            verify(fixture.second.player, never()).closeInventory();
        }
    }

    @Test
    public void nextNoDebuffMatchKeepsTheRestoredProfilesAndInvulnerabilityLimits() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.service.endMatch(fixture.register("combo"), null, null, MatchEndReason.INTERNAL_ERROR);
            int firstMaximumWrites = fixture.first.maximumNoDamageWrites;
            int secondMaximumWrites = fixture.second.maximumNoDamageWrites;
            Match next = fixture.register("nodebuff");

            fixture.assertRestored(fixture.first);
            fixture.assertRestored(fixture.second);
            fixture.service.endMatch(next, null, null, MatchEndReason.INTERNAL_ERROR);

            fixture.assertFinishedAndRestored(next);
            // The next kit must not add any combat-setting writes, including Combo's post-teleport safeguard.
            verify(fixture.first.player, times(2)).setKnockbackProfile(any(KnockbackProfile.class));
            verify(fixture.second.player, times(2)).setKnockbackProfile(any(KnockbackProfile.class));
            assertEquals(firstMaximumWrites, fixture.first.maximumNoDamageWrites);
            assertEquals(secondMaximumWrites, fixture.second.maximumNoDamageWrites);
        }
    }

    @Test
    public void lobbyWorldTransferCannotOverwriteTheRestoredCustomInvulnerabilityLimit() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Match match = fixture.register("combo");
            fixture.teleportLobbyWithGlobalInvulnerability(30);

            fixture.service.endMatch(match, null, null, MatchEndReason.INTERNAL_ERROR);

            fixture.assertFinishedAndRestored(match);
            for (Participant participant : new Participant[] { fixture.first, fixture.second }) {
                InOrder order = inOrder(participant.player);
                order.verify(participant.player).setMaximumNoDamageTicks(participant.originalMaximumNoDamageTicks);
                order.verify(participant.player).teleport(any(Location.class));
                order.verify(participant.player).setMaximumNoDamageTicks(30);
                order.verify(participant.player).setMaximumNoDamageTicks(participant.originalMaximumNoDamageTicks);
            }
        }
    }

    @Test
    public void directPlayerResetRestoresComboBeforeAnInventoryException() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.combo.apply(fixture.first.player);
            doThrow(new IllegalStateException("inventory failed"))
                    .when(fixture.first.player).closeInventory();

            try {
                fixture.reset.reset(fixture.first.player, GameMode.SURVIVAL);
                fail("The inventory failure should propagate after combat state has been restored");
            } catch (IllegalStateException expected) {
                assertEquals("inventory failed", expected.getMessage());
            }

            fixture.assertRestored(fixture.first);
            assertRestoredBeforeInventoryCleanup(fixture.first);
        }
    }

    private static void assertRestoredBeforeInventoryCleanup(Participant participant) {
        InOrder order = inOrder(participant.player);
        order.verify(participant.player).setKnockbackProfile(participant.originalProfile);
        order.verify(participant.player).setMaximumNoDamageTicks(participant.originalMaximumNoDamageTicks);
        order.verify(participant.player).setNoDamageTicks(0);
        order.verify(participant.player).closeInventory();
    }

    private static final class Fixture implements AutoCloseable {
        private final Field serverField;
        private final Object previousServer;
        private final Participant first = new Participant("First", 20);
        private final Participant second = new Participant("Second", 14);
        private final Map<UUID, Player> onlinePlayers = new HashMap<UUID, Player>();
        private final ProfileManager profiles = new ProfileManager();
        private final MatchManager matches = new MatchManager();
        private final PluginLogger logger = mock(PluginLogger.class);
        private final ComboCombatService combo = new ComboCombatService(ComboConfig.load(new YamlConfiguration()));
        private final PlayerResetService reset = new PlayerResetService(combo);
        private final Arena arena = new Arena("shared", Arrays.asList("nodebuff", "boxing", "combo"),
                new Location(null, 1000, 4, -55.5), new Location(null, 1000, 4, 55.5), ArenaState.AVAILABLE);
        private final LobbyService lobby;
        private final MatchService service;

        private Fixture() throws Exception {
            serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            previousServer = serverField.get(null);
            Server server = mock(Server.class);
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            when(server.getScheduler()).thenReturn(scheduler);
            when(server.getItemFactory()).thenReturn(CraftItemFactory.instance());
            when(server.getWorlds()).thenReturn(Collections.emptyList());
            when(server.getPlayer(any(UUID.class)))
                    .thenAnswer(invocation -> onlinePlayers.get(invocation.getArguments()[0]));
            when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                    .thenReturn(mock(BukkitTask.class));
            onlinePlayers.put(first.id, first.player);
            onlinePlayers.put(second.id, second.player);
            serverField.set(null, server);
            try {
                PracticePlugin plugin = new ObjenesisStd().newInstance(PracticePlugin.class);
                Field configField = JavaPlugin.class.getDeclaredField("newConfig");
                configField.setAccessible(true);
                configField.set(plugin, new YamlConfiguration());
                Field loggerField = JavaPlugin.class.getDeclaredField("logger");
                loggerField.setAccessible(true);
                loggerField.set(plugin, logger);
                ArenaManager arenas = new ArenaManager(plugin);
                Field registryField = ArenaManager.class.getDeclaredField("arenas");
                registryField.setAccessible(true);
                Object registry = registryField.get(arenas);
                Method definitions = registry.getClass().getDeclaredMethod("replaceDefinitions", Map.class);
                definitions.setAccessible(true);
                definitions.invoke(registry, Collections.singletonMap(arena.getId(), arena));
                MatchScoreboardService scoreboard = new MatchScoreboardService(plugin, new PlayerPingService(null));
                lobby = new LobbyService(plugin, profiles, reset, scoreboard);
                KitManager kits = new KitManager();
                QueueManager queue = new QueueManager(profiles, kits, arenas, null, lobby);
                service = new MatchService(plugin, profiles, arenas, matches, kits, queue,
                        reset, lobby, null, scoreboard, null, combo);
            } catch (Exception | Error failure) {
                serverField.set(null, previousServer);
                throw failure;
            }
        }

        private void teleportLobbyWithGlobalInvulnerability(int globalMaximum) throws Exception {
            Field location = LobbyService.class.getDeclaredField("lobbyLocation");
            location.setAccessible(true);
            location.set(lobby, new Location(mock(World.class), 0, 4, 0));
            for (Participant participant : new Participant[] { first, second }) {
                doAnswer(invocation -> {
                    // WindSpigot World.addEntity reapplies its server-wide player hit delay.
                    participant.player.setMaximumNoDamageTicks(globalMaximum);
                    return true;
                }).when(participant.player).teleport(any(Location.class));
            }
        }

        private Match register(String kitId) {
            arena.setState(ArenaState.IN_USE);
            Match match = new Match(first.id, second.id, kitId, arena.getId());
            assertTrue(matches.register(match));
            assertTrue(match.markFighting());
            for (Participant participant : new Participant[] { first, second }) {
                PlayerProfile profile = profiles.getOrCreate(participant.id);
                profile.setState(PlayerState.FIGHTING);
                profile.setQueuedKitId(kitId);
                if ("combo".equals(kitId)) {
                    combo.apply(participant.player);
                    assertTrue(combo.isApplied(participant.id));
                    assertNotSame(participant.originalProfile, participant.currentProfile);
                }
            }
            return match;
        }

        private void assertFinishedAndRestored(Match match) {
            assertEquals(MatchState.FINISHED, match.getState());
            assertNull(matches.getByPlayer(first.id));
            assertNull(matches.getByPlayer(second.id));
            assertNull(matches.getByArena(arena.getId()));
            assertEquals(ArenaState.AVAILABLE, arena.getState());
            for (Participant participant : new Participant[] { first, second }) {
                assertRestored(participant);
                assertEquals(PlayerState.LOBBY, profiles.get(participant.id).getState());
                assertNull(profiles.get(participant.id).getQueuedKitId());
            }
        }

        private void assertRestored(Participant participant) {
            assertSame(participant.originalProfile, participant.currentProfile);
            assertEquals(participant.originalMaximumNoDamageTicks, participant.currentMaximumNoDamageTicks);
            assertEquals(0, participant.noDamageTicks);
            assertFalse(combo.isApplied(participant.id));
        }

        @Override
        public void close() throws Exception {
            serverField.set(null, previousServer);
        }
    }

    private static final class Participant {
        private final Player player = mock(Player.class);
        private final UUID id = UUID.randomUUID();
        private final KnockbackProfile originalProfile = mock(KnockbackProfile.class);
        private final int originalMaximumNoDamageTicks;
        private KnockbackProfile currentProfile = originalProfile;
        private int currentMaximumNoDamageTicks;
        private int maximumNoDamageWrites;
        private int noDamageTicks = 7;

        private Participant(String name, int maximumNoDamageTicks) {
            originalMaximumNoDamageTicks = maximumNoDamageTicks;
            currentMaximumNoDamageTicks = maximumNoDamageTicks;
            when(player.getUniqueId()).thenReturn(id);
            when(player.getName()).thenReturn(name);
            when(player.isOnline()).thenReturn(true);
            when(player.getHealth()).thenReturn(20.0D);
            when(player.getMaxHealth()).thenReturn(20.0D);
            when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
            when(player.getActivePotionEffects()).thenReturn(Collections.emptyList());
            when(player.getKnockbackProfile()).thenAnswer(invocation -> currentProfile);
            when(player.getMaximumNoDamageTicks()).thenAnswer(invocation -> currentMaximumNoDamageTicks);
            doAnswer(invocation -> {
                currentProfile = (KnockbackProfile) invocation.getArguments()[0];
                return null;
            }).when(player).setKnockbackProfile(any(KnockbackProfile.class));
            doAnswer(invocation -> {
                currentMaximumNoDamageTicks = (Integer) invocation.getArguments()[0];
                maximumNoDamageWrites++;
                return null;
            }).when(player).setMaximumNoDamageTicks(anyInt());
            doAnswer(invocation -> {
                noDamageTicks = (Integer) invocation.getArguments()[0];
                return null;
            }).when(player).setNoDamageTicks(anyInt());
        }
    }
}
