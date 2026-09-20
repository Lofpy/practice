package com.poppy.practice.service;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.arena.Arena;
import com.poppy.practice.arena.ArenaManager;
import com.poppy.practice.arena.ArenaState;
import com.poppy.practice.combat.ComboCombatService;
import com.poppy.practice.config.ComboConfig;
import com.poppy.practice.cosmetic.MatchFinishEffects;
import com.poppy.practice.cosmetic.PreferencesService;
import com.poppy.practice.kit.KitManager;
import com.poppy.practice.match.Match;
import com.poppy.practice.match.MatchEndReason;
import com.poppy.practice.match.MatchManager;
import com.poppy.practice.match.MatchState;
import com.poppy.practice.match.MatchType;
import com.poppy.practice.network.PlayerPingService;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.queue.QueueManager;
import com.poppy.practice.rating.RatingService;
import dev.cobblesword.nachospigot.knockback.KnockbackProfile;
import org.bukkit.Bukkit;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objenesis.ObjenesisStd;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RankedMatchLifecycleTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void equalRankedResultCommitsSixteenEachBeforeSixtyTickReservedEnding() throws Exception {
        try (Fixture f = new Fixture(temporary.newFolder())) {
            Match match = f.register(MatchType.RANKED, "combo", true);
            f.service.handleDeath(f.second);
            assertEquals(1666000, f.ratings.getRatingMilli(f.firstId, "combo"));
            assertEquals(1634000, f.ratings.getRatingMilli(f.secondId, "combo"));
            assertEquals(MatchState.ENDING, match.getState());
            assertEquals(PlayerState.ENDING, f.profiles.get(f.firstId).getState());
            assertEquals(PlayerState.ENDING, f.profiles.get(f.secondId).getState());
            assertSame(match, f.matches.getByArena(f.arena.getId()));
            assertEquals(ArenaState.IN_USE, f.arena.getState());
            assertTrue(f.combo.isApplied(f.firstId));
            assertTrue(f.combo.isApplied(f.secondId));
            verify(f.first, never()).closeInventory();
            verify(f.scheduler).runTaskLater(any(Plugin.class), any(Runnable.class), eq(60L));
            f.finishTasks.get(0).run();
            f.assertClean(match);
        }
    }

    @Test
    public void duelHasSameFinishDelayButNoEloMutation() throws Exception {
        try (Fixture f = new Fixture(temporary.newFolder())) {
            Match match = f.register(MatchType.DUEL, "nodebuff", true);
            f.service.handleDeath(f.second);
            assertEquals(MatchState.ENDING, match.getState());
            assertEquals(1650000, f.ratings.getRatingMilli(f.firstId, "nodebuff"));
            assertEquals(1650000, f.ratings.getRatingMilli(f.secondId, "nodebuff"));
            verify(f.first, never()).sendMessage(contains("ELO ["));
            verify(f.second, never()).sendMessage(contains("ELO ["));
            f.finishTasks.get(0).run();
            f.assertClean(match);
        }
    }

    @Test
    public void duplicateDeathThenQuitCannotReverseOrDoubleApplyResult() throws Exception {
        try (Fixture f = new Fixture(temporary.newFolder())) {
            Match match = f.register(MatchType.RANKED, "nodebuff", true);
            f.service.handleDeath(f.second);
            f.service.handleDeath(f.first);
            assertEquals(1, f.finishTasks.size());
            f.service.handleQuit(f.firstId);
            f.assertClean(match);
            f.finishTasks.get(0).run();
            assertEquals(1666000, f.ratings.getRatingMilli(f.firstId, "nodebuff"));
            assertEquals(1634000, f.ratings.getRatingMilli(f.secondId, "nodebuff"));
            verify(f.first, times(1)).closeInventory();
            verify(f.second, times(1)).closeInventory();
        }
    }

    @Test
    public void naturalActiveQuitIsAnImmediateRankedForfeit() throws Exception {
        try (Fixture f = new Fixture(temporary.newFolder())) {
            Match match = f.register(MatchType.RANKED, "nodebuff", true);
            f.service.handleQuit(f.secondId);
            f.assertClean(match);
            assertEquals(1666000, f.ratings.getRatingMilli(f.firstId, "nodebuff"));
            assertEquals(1634000, f.ratings.getRatingMilli(f.secondId, "nodebuff"));
            assertTrue(f.finishTasks.isEmpty());
        }
    }

    @Test
    public void countdownDeathOrQuitNeverChangesRatingAndDoesNotDelayCleanup() throws Exception {
        for (boolean death : new boolean[] {true, false}) {
            try (Fixture f = new Fixture(temporary.newFolder())) {
                Match match = f.register(MatchType.RANKED, "nodebuff", false);
                if (death) f.service.handleDeath(f.second); else f.service.handleQuit(f.secondId);
                f.assertClean(match);
                assertEquals(1650000, f.ratings.getRatingMilli(f.firstId, "nodebuff"));
                assertEquals(1650000, f.ratings.getRatingMilli(f.secondId, "nodebuff"));
                assertTrue(f.finishTasks.isEmpty());
                verify(f.worldSpigot, never()).strikeLightningEffect(any(Location.class), anyBoolean());
            }
        }
    }

    @Test
    public void adminForceStopAfterBoxingWinnerLockDoesNotMutateElo() throws Exception {
        try (Fixture f = new Fixture(temporary.newFolder())) {
            Match match = f.register(MatchType.RANKED, "boxing", true);
            for (int i = 0; i < 100; i++) match.getStats(f.firstId).recordMeleeHit(false);
            assertTrue(match.claimBoxingWinner(f.firstId));
            f.service.forceStop(f.firstId);
            f.assertClean(match);
            assertEquals(1650000, f.ratings.getRatingMilli(f.firstId, "boxing"));
            assertEquals(1650000, f.ratings.getRatingMilli(f.secondId, "boxing"));
            assertTrue(f.finishTasks.isEmpty());
        }
    }

    @Test
    public void firstResultChatFailureStillPlaysEffectAndReturnsBothPlayers() throws Exception {
        try (Fixture f = new Fixture(temporary.newFolder())) {
            Match match = f.register(MatchType.DUEL, "nodebuff", true);
            doThrow(new IllegalStateException("chat failure")).when(f.first).sendMessage(anyString());
            f.service.handleDeath(f.second);
            verify(f.worldSpigot).strikeLightningEffect(any(Location.class), eq(true));
            assertEquals(MatchState.ENDING, match.getState());
            f.finishTasks.get(0).run();
            f.assertClean(match);
        }
    }

    @Test
    public void countdownCancellationFailureStillMarksBothEndingAndUpdatesRating() throws Exception {
        try (Fixture f = new Fixture(temporary.newFolder())) {
            Match match = f.register(MatchType.RANKED, "nodebuff", true);
            match.setCountdownTaskId(12);
            doThrow(new IllegalStateException("timer failure")).when(f.scheduler).cancelTask(12);
            f.service.handleDeath(f.second);
            assertEquals(PlayerState.ENDING, f.profiles.get(f.firstId).getState());
            assertEquals(PlayerState.ENDING, f.profiles.get(f.secondId).getState());
            assertEquals(1666000, f.ratings.getRatingMilli(f.firstId, "nodebuff"));
            f.finishTasks.get(0).run();
            f.assertClean(match);
        }
    }

    @Test
    public void finishTaskCancellationFailureCannotStrandArenaOrParticipants() throws Exception {
        try (Fixture f = new Fixture(temporary.newFolder())) {
            Match match = f.register(MatchType.DUEL, "combo", true);
            f.service.handleDeath(f.second);
            doThrow(new IllegalStateException("cancel failed")).when(f.delayedTask).cancel();
            f.service.shutdown();
            f.assertClean(match);
            f.finishTasks.get(0).run();
            f.assertClean(match);
        }
    }

    @Test
    public void resultWithForeignParticipantCannotMutateAnyRatings() throws Exception {
        try (Fixture f = new Fixture(temporary.newFolder())) {
            Match match = f.register(MatchType.RANKED, "nodebuff", true);
            f.service.endMatch(match, UUID.randomUUID(), f.secondId, MatchEndReason.DEATH);
            f.assertClean(match);
            assertEquals(1650000, f.ratings.getRatingMilli(f.firstId, "nodebuff"));
            assertEquals(1650000, f.ratings.getRatingMilli(f.secondId, "nodebuff"));
            assertTrue(f.finishTasks.isEmpty());
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final Field serverField;
        private final Object previousServer;
        private final UUID firstId = UUID.randomUUID();
        private final UUID secondId = UUID.randomUUID();
        private final Player first = player(firstId, "First");
        private final Player second = player(secondId, "Second");
        private final ProfileManager profiles = new ProfileManager();
        private final MatchManager matches = new MatchManager();
        private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        private final BukkitTask delayedTask = mock(BukkitTask.class);
        private final List<Runnable> finishTasks = new ArrayList<Runnable>();
        private final World world = mock(World.class);
        private final World.Spigot worldSpigot = mock(World.Spigot.class);
        private final ComboCombatService combo = new ComboCombatService(ComboConfig.load(new YamlConfiguration()));
        private final Arena arena = new Arena("arena", Arrays.asList("nodebuff", "boxing", "combo"),
                new Location(world, 0, 4, 0), new Location(world, 0, 4, 10), ArenaState.AVAILABLE);
        private final RatingService ratings;
        private final MatchService service;

        private Fixture(File folder) throws Exception {
            serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            previousServer = serverField.get(null);
            Server server = mock(Server.class);
            Map<UUID, Player> players = new HashMap<UUID, Player>();
            players.put(firstId, first);
            players.put(secondId, second);
            when(server.getPlayer(any(UUID.class))).thenAnswer(i -> players.get(i.getArguments()[0]));
            when(server.getScheduler()).thenReturn(scheduler);
            when(server.getWorlds()).thenReturn(Collections.emptyList());
            when(server.getItemFactory()).thenReturn(CraftItemFactory.instance());
            when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                    .thenReturn(mock(BukkitTask.class));
            when(scheduler.runTaskLater(any(Plugin.class), any(Runnable.class), eq(60L))).thenAnswer(i -> {
                finishTasks.add((Runnable) i.getArguments()[1]);
                return delayedTask;
            });
            when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
            when(world.spigot()).thenReturn(worldSpigot);
            when(world.getPlayers()).thenReturn(Collections.emptyList());
            when(first.getLocation()).thenReturn(arena.getFirstSpawn());
            when(second.getLocation()).thenReturn(arena.getSecondSpawn());
            serverField.set(null, server);
            try {
                PracticePlugin plugin = new ObjenesisStd().newInstance(PracticePlugin.class);
                PluginLogger logger = mock(PluginLogger.class);
                setField(JavaPlugin.class, plugin, "newConfig", new YamlConfiguration());
                setField(JavaPlugin.class, plugin, "logger", logger);
                ArenaManager arenas = new ArenaManager(plugin);
                Field registryField = ArenaManager.class.getDeclaredField("arenas");
                registryField.setAccessible(true);
                Object registry = registryField.get(arenas);
                Method replace = registry.getClass().getDeclaredMethod("replaceDefinitions", Map.class);
                replace.setAccessible(true);
                replace.invoke(registry, Collections.singletonMap(arena.getId(), arena));
                PlayerResetService reset = new PlayerResetService(combo);
                MatchScoreboardService scoreboard = new MatchScoreboardService(plugin, new PlayerPingService(null));
                LobbyService lobby = new LobbyService(plugin, profiles, reset, scoreboard);
                KitManager kits = new KitManager();
                QueueManager queue = new QueueManager(profiles, kits, arenas, null, lobby);
                service = new MatchService(plugin, profiles, arenas, matches, kits, queue,
                        reset, lobby, null, scoreboard, new MatchResultService(profiles), combo);
                ratings = new RatingService(folder, logger);
                for (String kit : Arrays.asList("nodebuff", "boxing", "combo")) {
                    for (int i = 0; i < 3; i++) {
                        ratings.recordPlacement(firstId, kit, UUID.randomUUID(), 50);
                        ratings.recordPlacement(secondId, kit, UUID.randomUUID(), 50);
                    }
                }
                service.setRatingService(ratings);
                service.setMatchFinishEffects(new MatchFinishEffects(new PreferencesService(folder, logger)));
            } catch (Exception | Error failure) {
                serverField.set(null, previousServer);
                throw failure;
            }
        }

        private Match register(MatchType type, String kit, boolean fighting) {
            arena.setState(ArenaState.IN_USE);
            Match match = new Match(firstId, secondId, kit, arena.getId(), type);
            assertTrue(matches.register(match));
            if (fighting) assertTrue(match.markFighting());
            for (Player player : new Player[] {first, second}) {
                profiles.getOrCreate(player.getUniqueId()).setState(fighting ? PlayerState.FIGHTING : PlayerState.STARTING);
                if ("combo".equals(kit)) combo.apply(player);
            }
            return match;
        }

        private void assertClean(Match match) {
            assertEquals(MatchState.FINISHED, match.getState());
            assertEquals(ArenaState.AVAILABLE, arena.getState());
            assertEquals(0, matches.size());
            for (UUID id : new UUID[] {firstId, secondId}) {
                assertFalse(combo.isApplied(id));
                assertEquals(PlayerState.LOBBY, profiles.get(id).getState());
            }
        }

        @Override public void close() throws Exception {
            serverField.set(null, previousServer);
        }

        private static Player player(UUID id, String name) {
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(id);
            when(player.getName()).thenReturn(name);
            when(player.isOnline()).thenReturn(true);
            when(player.getHealth()).thenReturn(20.0);
            when(player.getMaxHealth()).thenReturn(20.0);
            when(player.getMaximumNoDamageTicks()).thenReturn(20);
            when(player.getKnockbackProfile()).thenReturn(mock(KnockbackProfile.class));
            when(player.getActivePotionEffects()).thenReturn(Collections.emptyList());
            when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
            when(player.spigot()).thenReturn(mock(Player.Spigot.class));
            return player;
        }

        private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        }
    }
}
