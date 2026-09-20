package com.poppy.practice.bot;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.arena.Arena;
import com.poppy.practice.arena.ArenaManager;
import com.poppy.practice.arena.ArenaState;
import com.poppy.practice.combat.ComboCombatService;
import com.poppy.practice.config.ComboConfig;
import com.poppy.practice.kit.KitManager;
import com.poppy.practice.match.MatchState;
import com.poppy.practice.network.PlayerPingService;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.queue.QueueManager;
import com.poppy.practice.rating.RatingService;
import com.poppy.practice.tier.TierTestService;
import com.poppy.practice.service.LobbyService;
import com.poppy.practice.service.MatchScoreboardService;
import com.poppy.practice.service.PlayerResetService;
import dev.cobblesword.nachospigot.knockback.KnockbackProfile;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemFactory;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginLogger;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.mockito.InOrder;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Exercises actual bot cleanup while leaving native entity creation to runtime smoke tests. */
public class ComboBotLifecycleTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void completedMatchReservesArenaAndCombatSettingsForSixtyTicks() throws Exception {
        try (Fixture fixture = new Fixture()) {
            BotMatch match = fixture.register("combo");
            fixture.service.handleBotDefeat(fixture.bot.id);
            assertEquals(MatchState.ENDING, match.getState());
            assertEquals(PlayerState.ENDING, fixture.profiles.get(fixture.human.id).getState());
            assertSame(match, fixture.service.getByPlayer(fixture.human.id));
            assertSame(match, fixture.service.getByBot(fixture.bot.id));
            assertEquals(ArenaState.IN_USE, fixture.arena.getState());
            assertTrue(fixture.combo.isApplied(fixture.human.id));
            assertTrue(fixture.combo.isApplied(fixture.bot.id));
            verify(fixture.human.player, never()).closeInventory();
            verify(fixture.scheduler).runTaskLater(any(Plugin.class), any(Runnable.class), eq(60L));
            assertEquals(1, fixture.finishTasks.size());
            fixture.finishTasks.get(0).run();
            fixture.assertFinishedAndRestored(match);
        }
    }

    @Test
    public void disconnectDuringEndingCleansUpImmediatelyAndStaleFinishIsHarmless() throws Exception {
        try (Fixture fixture = new Fixture()) {
            BotMatch match = fixture.register("combo");
            fixture.service.handlePlayerDefeat(fixture.human.id);
            assertEquals(MatchState.ENDING, match.getState());
            fixture.service.handleQuit(fixture.human.id);
            fixture.assertFinishedAndRestored(match);
            fixture.finishTasks.get(0).run();
            fixture.assertFinishedAndRestored(match);
            verify(fixture.human.player, times(1)).closeInventory();
            verify(fixture.scheduler).cancelTask(99);
        }
    }

    @Test
    public void shutdownDuringEndingRestoresBothWithoutLobbyTeleport() throws Exception {
        try (Fixture fixture = new Fixture()) {
            BotMatch match = fixture.register("combo");
            fixture.service.handleBotDefeat(fixture.bot.id);
            fixture.service.shutdown();
            fixture.assertFinishedAndRestored(match, false);
            fixture.finishTasks.get(0).run();
            verify(fixture.human.player, never()).teleport(any(Location.class));
        }
    }

    @Test
    public void placementNaturalFinishCountsOnceBeforeDelayAndOrdinaryMatchesDoNot() throws Exception {
        try (Fixture fixture = new Fixture()) {
            java.io.File folder = temporary.newFolder();
            RatingService ratings = new RatingService(folder, fixture.logger);
            fixture.service.setTierTestService(new TierTestService(folder, ratings, fixture.logger));
            BotMatch placement = fixture.register("combo", true, true);
            placement.getStats(fixture.human.id).recordMeleeHit(false);
            fixture.service.handleBotDefeat(fixture.bot.id);
            assertEquals(1, ratings.getPlacementCount(fixture.human.id, "combo"));
            assertEquals(MatchState.ENDING, placement.getState());
            fixture.service.handleBotDefeat(fixture.bot.id);
            assertEquals(1, ratings.getPlacementCount(fixture.human.id, "combo"));
            fixture.finishTasks.get(0).run();
            fixture.register("combo", true, false);
            fixture.service.handleBotDefeat(fixture.bot.id);
            fixture.finishTasks.get(1).run();
            assertEquals(1, ratings.getPlacementCount(fixture.human.id, "combo"));
        }
    }

    @Test
    public void quitForceStopShutdownAndCountdownNeverCountPlacements() throws Exception {
        for (int scenario = 0; scenario < 4; scenario++) {
            try (Fixture fixture = new Fixture()) {
                java.io.File folder = temporary.newFolder();
                RatingService ratings = new RatingService(folder, fixture.logger);
                fixture.service.setTierTestService(new TierTestService(folder, ratings, fixture.logger));
                fixture.register("combo", scenario != 3, true);
                if (scenario == 0) fixture.service.handleQuit(fixture.human.id);
                if (scenario == 1) fixture.service.forceStop(fixture.human.id);
                if (scenario == 2) fixture.service.shutdown();
                if (scenario == 3) fixture.service.handleBotDefeat(fixture.bot.id);
                assertEquals(0, ratings.getPlacementCount(fixture.human.id, "combo"));
                assertEquals(0, fixture.finishTasks.size());
                assertEquals(0, fixture.service.size());
            }
        }
    }

    @Test
    public void cancelledLethalBotDamageCannotProducePlacementOrMatchResult() throws Exception {
        try (Fixture fixture = new Fixture()) {
            BotMatch match = fixture.register("combo", true, true);
            BotListener listener = new BotListener(fixture.service, null);
            EntityDamageByEntityEvent attack = new EntityDamageByEntityEvent(fixture.human.player,
                    fixture.bot.player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 40);
            attack.setCancelled(true);
            listener.onBotDamage(attack);
            EntityDamageEvent fall = new EntityDamageEvent(fixture.bot.player,
                    EntityDamageEvent.DamageCause.FALL, 40);
            fall.setCancelled(true);
            listener.onBotDamage(fall);
            assertEquals(MatchState.FIGHTING, match.getState());
            assertEquals(0, match.getStats(fixture.human.id).getHits());
            assertEquals(0, fixture.finishTasks.size());
            fixture.service.shutdown();
        }
    }

    @Test
    public void botDamageIsBlockedForTheEntireEndingPresentation() throws Exception {
        try (Fixture fixture = new Fixture()) {
            BotMatch match = fixture.register("combo");
            fixture.service.handleBotDefeat(fixture.bot.id);
            BotListener listener = new BotListener(fixture.service, null);
            EntityDamageByEntityEvent attack = new EntityDamageByEntityEvent(fixture.human.player,
                    fixture.bot.player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 40);
            listener.onBotDamage(attack);
            assertTrue(attack.isCancelled());
            assertEquals(MatchState.ENDING, match.getState());
            assertEquals(1, fixture.finishTasks.size());
            fixture.finishTasks.get(0).run();
        }
    }

    @Test
    public void liveUpdatesReachTheUnlistedNpcAndHumanWithoutLosingTheirOriginalSettings() throws Exception {
        try (Fixture fixture = new Fixture()) {
            BotMatch match = fixture.register("combo", false);
            assertNull(Bukkit.getPlayer(fixture.bot.id));
            when(fixture.bot.player.isOnline()).thenReturn(false);
            ComboConfig firstUpdate = fixture.combo.getConfiguration().withSetting("horizontal", "0.44");

            fixture.combo.updateLive(firstUpdate);

            assertEquals(MatchState.STARTING, match.getState());
            assertSame(match, fixture.service.getByPlayer(fixture.human.id));
            assertSame(match, fixture.service.getByBot(fixture.bot.id));
            for (Participant participant : new Participant[] { fixture.human, fixture.bot }) {
                assertEquals(0.44D, participant.currentProfile.getHorizontal(), 0.0D);
                assertEquals(4, participant.currentMaximum);
                assertTrue(fixture.combo.isApplied(participant.id));
            }
            assertNotSame(fixture.human.currentProfile, fixture.bot.currentProfile);

            match.markFighting();
            fixture.profiles.get(fixture.human.id).setState(PlayerState.FIGHTING);
            ComboConfig secondUpdate = firstUpdate.withSetting("vertical", "0.12")
                    .withSetting("no-damage-ticks", "3");
            fixture.combo.updateLive(secondUpdate);

            assertEquals(MatchState.FIGHTING, match.getState());
            for (Participant participant : new Participant[] { fixture.human, fixture.bot }) {
                assertEquals(0.44D, participant.currentProfile.getHorizontal(), 0.0D);
                assertEquals(0.12D, participant.currentProfile.getVertical(), 0.0D);
                assertEquals(6, participant.currentMaximum);
            }
            assertTrue(fixture.service.forceStop(fixture.human.id));
            fixture.assertFinishedAndRestored(match);
            fixture.assertRestoredBeforeHumanReset();
            assertSame(secondUpdate, fixture.combo.getConfiguration());
        }
    }

    @Test
    public void forceStopRestoresHumanAndNpcAndCancelsBothScheduledTasks() throws Exception {
        try (Fixture fixture = new Fixture()) {
            BotMatch match = fixture.register("combo");
            match.setCountdownTaskId(71);
            match.setAiTaskId(72);

            assertTrue(fixture.service.forceStop(fixture.human.id));
            assertFalse(fixture.service.forceStop(fixture.human.id));

            fixture.assertFinishedAndRestored(match);
            fixture.assertRestoredBeforeHumanReset();
            verify(fixture.human.player).setGameMode(GameMode.ADVENTURE);
            verify(fixture.scheduler).cancelTask(71);
            verify(fixture.scheduler).cancelTask(72);
            verify(fixture.bot.player).setKnockbackProfile(fixture.bot.originalProfile);
        }
    }

    @Test
    public void quitRestoresCapturedHumanAfterBukkitStopsFindingThem() throws Exception {
        try (Fixture fixture = new Fixture()) {
            BotMatch match = fixture.register("combo");
            fixture.onlinePlayers.remove(fixture.human.id);
            when(fixture.human.player.isOnline()).thenReturn(false);

            fixture.service.handleQuit(fixture.human.id);

            fixture.assertFinishedAndRestored(match);
            verify(fixture.human.player, never()).closeInventory();
        }
    }

    @Test
    public void pluginShutdownRestoresBothParticipantsWithoutLobbyTeleport() throws Exception {
        try (Fixture fixture = new Fixture()) {
            BotMatch match = fixture.register("combo");

            fixture.service.shutdown();

            fixture.assertFinishedAndRestored(match, false);
            verify(fixture.human.player, never()).closeInventory();
            verify(fixture.human.player, never()).teleport(any(Location.class));
        }
    }

    @Test
    public void lobbyWorldTransferCannotReplaceOriginalCustomInvulnerability() throws Exception {
        try (Fixture fixture = new Fixture()) {
            BotMatch match = fixture.register("combo");
            fixture.teleportLobbyWithGlobalInvulnerability(30);

            fixture.service.forceStop(fixture.human.id);

            fixture.assertFinishedAndRestored(match);
            InOrder order = inOrder(fixture.human.player);
            order.verify(fixture.human.player).setMaximumNoDamageTicks(fixture.human.originalMaximum);
            order.verify(fixture.human.player).teleport(any(Location.class));
            order.verify(fixture.human.player).setMaximumNoDamageTicks(30);
            order.verify(fixture.human.player).setMaximumNoDamageTicks(fixture.human.originalMaximum);
        }
    }

    @Test
    public void resultAndLobbyFailuresDoNotLeaveNpcOrHumanInComboState() throws Exception {
        try (Fixture fixture = new Fixture()) {
            BotMatch match = fixture.register("combo");
            doThrow(new IllegalStateException("result message failed"))
                    .when(fixture.human.player).sendMessage(anyString());
            doThrow(new IllegalStateException("inventory reset failed"))
                    .when(fixture.human.player).closeInventory();

            assertTrue(fixture.service.forceStop(fixture.human.id));

            fixture.assertFinishedAndRestored(match);
            fixture.assertRestoredBeforeHumanReset();
            verify(fixture.logger, times(2)).log(eq(Level.WARNING), anyString(), any(RuntimeException.class));
        }
    }

    @Test
    public void nextNoDebuffBotMatchRetainsItsOwnCombatDefaults() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.service.forceStop(fixture.register("combo").getPlayerId());
            int humanMaximumWrites = fixture.human.maximumWrites;
            int botMaximumWrites = fixture.bot.maximumWrites;

            BotMatch next = fixture.register("nodebuff");
            fixture.assertRestored(fixture.human);
            fixture.assertRestored(fixture.bot);
            fixture.service.forceStop(next.getPlayerId());

            fixture.assertFinishedAndRestored(next);
            assertEquals(humanMaximumWrites, fixture.human.maximumWrites);
            assertEquals(botMaximumWrites, fixture.bot.maximumWrites);
            verify(fixture.human.player, times(2)).setKnockbackProfile(any(KnockbackProfile.class));
            verify(fixture.bot.player, times(2)).setKnockbackProfile(any(KnockbackProfile.class));
        }
    }

    @Test
    public void missingNpcRegistryEntryStillRestoresItsCapturedCombatProfile() throws Exception {
        try (Fixture fixture = new Fixture()) {
            BotMatch match = fixture.register("combo");
            Field bots = BotService.class.getDeclaredField("botsById");
            bots.setAccessible(true);
            ((Map<?, ?>) bots.get(fixture.service)).clear();

            fixture.service.forceStop(fixture.human.id);

            fixture.assertFinishedAndRestored(match);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final net.minecraft.server.v1_8_R3.EntityPlayer npcHandle =
                mock(net.minecraft.server.v1_8_R3.EntityPlayer.class);
        private final Field serverField;
        private final Object previousServer;
        private final Participant human = new Participant("Human", 18);
        private final Participant bot = new Participant("PracticeBot", 24);
        private final Map<UUID, Player> onlinePlayers = new HashMap<UUID, Player>();
        private final ProfileManager profiles = new ProfileManager();
        private final PluginLogger logger = mock(PluginLogger.class);
        private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        private final List<Runnable> finishTasks = new ArrayList<Runnable>();
        private final YamlConfiguration config = new YamlConfiguration();
        private final ComboCombatService combo = new ComboCombatService(ComboConfig.load(config));
        private final PlayerResetService reset = new PlayerResetService(combo);
        private final Arena arena = new Arena("shared", Arrays.asList("nodebuff", "boxing", "combo"),
                new Location(null, 1000, 4, -55.5), new Location(null, 1000, 4, 55.5), ArenaState.AVAILABLE);
        private final LobbyService lobby;
        private final BotService service;

        private Fixture() throws Exception {
            serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            previousServer = serverField.get(null);
            Server server = mock(Server.class);
            when(server.getScheduler()).thenReturn(scheduler);
            when(server.getItemFactory()).thenReturn(CraftItemFactory.instance());
            when(server.getWorlds()).thenReturn(Collections.emptyList());
            when(server.getPlayer(any(UUID.class)))
                    .thenAnswer(invocation -> onlinePlayers.get(invocation.getArguments()[0]));
            when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                    .thenReturn(mock(BukkitTask.class));
            when(scheduler.runTaskLater(any(Plugin.class), any(Runnable.class), eq(60L)))
                    .thenAnswer(invocation -> {
                        finishTasks.add((Runnable) invocation.getArguments()[1]);
                        BukkitTask task = mock(BukkitTask.class);
                        when(task.getTaskId()).thenReturn(99);
                        return task;
                    });
            onlinePlayers.put(human.id, human.player);
            // The NPC intentionally does not belong to Bukkit's real online-player list.
            serverField.set(null, server);
            try {
                BotSetting.resetAll(config);
                PracticePlugin plugin = new ObjenesisStd().newInstance(PracticePlugin.class);
                setField(JavaPlugin.class, plugin, "newConfig", config);
                setField(JavaPlugin.class, plugin, "logger", logger);
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
                QueueManager queues = new QueueManager(profiles, kits, arenas, null, lobby);
                service = new BotService(plugin, profiles, arenas, kits, queues, reset,
                        lobby, null, scoreboard, null, combo);
            } catch (Exception | Error failure) {
                serverField.set(null, previousServer);
                throw failure;
            }
        }

        private BotMatch register(String kitId) throws Exception {
            return register(kitId, true);
        }

        private BotMatch register(String kitId, boolean fighting) throws Exception {
            return register(kitId, fighting, false);
        }

        private BotMatch register(String kitId, boolean fighting, boolean placement) throws Exception {
            arena.setState(ArenaState.IN_USE);
            BotMatch match = new BotMatch(human.id, bot.id, kitId, arena.getId(), placement);
            if (fighting) {
                match.markFighting();
            }
            BotNpc npc = new ObjenesisStd().newInstance(BotNpc.class);
            setField(BotNpc.class, npc, "player", bot.player);
            setField(BotNpc.class, npc, "handle", npcHandle);
            // Native entity removal is a no-op here; service state and captured players remain real.
            Method register = BotService.class.getDeclaredMethod("register", BotMatch.class,
                    BotNpc.class, BotSettings.class);
            register.setAccessible(true);
            register.invoke(service, match, npc, BotSettings.load(config));
            PlayerProfile profile = profiles.getOrCreate(human.id);
            profile.setState(fighting ? PlayerState.FIGHTING : PlayerState.STARTING);
            profile.setQueuedKitId(kitId);
            if ("combo".equals(kitId)) {
                combo.apply(human.player);
                combo.apply(bot.player);
                assertTrue(combo.isApplied(human.id));
                assertTrue(combo.isApplied(bot.id));
                assertNotSame(human.currentProfile, bot.currentProfile);
            }
            return match;
        }

        private void teleportLobbyWithGlobalInvulnerability(int globalMaximum) throws Exception {
            setField(LobbyService.class, lobby, "lobbyLocation", new Location(mock(World.class), 0, 4, 0));
            doAnswer(invocation -> {
                human.player.setMaximumNoDamageTicks(globalMaximum);
                return true;
            }).when(human.player).teleport(any(Location.class));
        }

        private void assertFinishedAndRestored(BotMatch match) {
            assertFinishedAndRestored(match, true);
        }

        private void assertFinishedAndRestored(BotMatch match, boolean expectLobby) {
            assertEquals(MatchState.FINISHED, match.getState());
            assertNull(service.getByPlayer(human.id));
            assertNull(service.getByBot(bot.id));
            assertNull(service.getBot(match));
            assertEquals(0, service.size());
            assertEquals(ArenaState.AVAILABLE, arena.getState());
            assertRestored(human);
            assertRestored(bot);
            if (expectLobby) {
                assertEquals(PlayerState.LOBBY, profiles.get(human.id).getState());
                assertNull(profiles.get(human.id).getQueuedKitId());
            }
        }

        private void assertRestored(Participant participant) {
            assertSame(participant.originalProfile, participant.currentProfile);
            assertEquals(participant.originalMaximum, participant.currentMaximum);
            assertEquals(0, participant.noDamageTicks);
            assertFalse(combo.isApplied(participant.id));
        }

        private void assertRestoredBeforeHumanReset() {
            InOrder order = inOrder(human.player);
            order.verify(human.player).setKnockbackProfile(human.originalProfile);
            order.verify(human.player).setMaximumNoDamageTicks(human.originalMaximum);
            order.verify(human.player).setNoDamageTicks(0);
            order.verify(human.player).closeInventory();
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
        private final int originalMaximum;
        private KnockbackProfile currentProfile = originalProfile;
        private int currentMaximum;
        private int maximumWrites;
        private int noDamageTicks = 7;

        private Participant(String name, int maximumNoDamageTicks) {
            originalMaximum = maximumNoDamageTicks;
            currentMaximum = maximumNoDamageTicks;
            when(player.getUniqueId()).thenReturn(id);
            when(player.getName()).thenReturn(name);
            when(player.isOnline()).thenReturn(true);
            when(player.getHealth()).thenReturn(20.0D);
            when(player.getMaxHealth()).thenReturn(20.0D);
            when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
            when(player.getActivePotionEffects()).thenReturn(Collections.emptyList());
            when(player.getKnockbackProfile()).thenAnswer(invocation -> currentProfile);
            when(player.getMaximumNoDamageTicks()).thenAnswer(invocation -> currentMaximum);
            doAnswer(invocation -> {
                currentProfile = (KnockbackProfile) invocation.getArguments()[0];
                return null;
            }).when(player).setKnockbackProfile(any(KnockbackProfile.class));
            doAnswer(invocation -> {
                currentMaximum = (Integer) invocation.getArguments()[0];
                maximumWrites++;
                return null;
            }).when(player).setMaximumNoDamageTicks(anyInt());
            doAnswer(invocation -> {
                noDamageTicks = (Integer) invocation.getArguments()[0];
                return null;
            }).when(player).setNoDamageTicks(anyInt());
        }
    }

    private static void setField(Class<?> type, Object instance, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }
}
