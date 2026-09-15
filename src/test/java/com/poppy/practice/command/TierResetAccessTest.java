package com.poppy.practice.command;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.bot.BotMatch;
import com.poppy.practice.bot.BotService;
import com.poppy.practice.bot.BotSetting;
import com.poppy.practice.kit.KitManager;
import com.poppy.practice.match.Match;
import com.poppy.practice.match.MatchManager;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.queue.MatchQueue;
import com.poppy.practice.queue.QueueManager;
import com.poppy.practice.rating.CertificationResetPlan;
import com.poppy.practice.rating.RatingService;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Actual in-memory match, Bot, queue and profile registries protect an online reset. */
public class TierResetAccessTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void exactOnlinePlayerResolvesWithoutOfflineOrExternalLookup() throws Exception {
        Fixture f = new Fixture();
        Player alice = player(f.alice, "Alice", true);
        when(f.server.getPlayerExact("Alice")).thenReturn(alice);
        assertEquals(f.alice, f.access.resolvePlayer("Alice"));
        verify(f.server, never()).getOfflinePlayers();
        verify(f.server, never()).getOfflinePlayer(anyString());
        verify(f.server, never()).getOfflinePlayer(any(UUID.class));
    }

    @Test public void offlineKnownNameResolvesCaseInsensitivelyWithoutExternalLookup() throws Exception {
        Fixture f = new Fixture();
        doReturn(new OfflinePlayer[] {
                offline(UUID.randomUUID(), null), offline(f.alice, "Alice"), offline(f.bob, "Bob")
        }).when(f.server).getOfflinePlayers();
        assertEquals(f.alice, f.access.resolvePlayer("aLiCe"));
        assertNull(f.access.resolvePlayer("NeverJoined"));
        verify(f.server, never()).getOfflinePlayer(anyString());
        verify(f.server, never()).getOfflinePlayer(any(UUID.class));
    }

    @Test public void ambiguousOfflineNameIsRejectedRatherThanChoosingAnAccount() throws Exception {
        Fixture f = new Fixture();
        doReturn(new OfflinePlayer[] {
                offline(f.alice, "Alice"), offline(f.bob, "ALICE")
        }).when(f.server).getOfflinePlayers();
        assertNull(f.access.resolvePlayer("Alice"));
        doReturn(new OfflinePlayer[] {
                offline(f.alice, "Alice"), offline(f.alice, "ALICE")
        }).when(f.server).getOfflinePlayers();
        assertEquals(f.alice, f.access.resolvePlayer("Alice"));
    }

    @Test public void onlineTabNamesNeverEnumerateOfflineAccounts() throws Exception {
        Fixture f = new Fixture();
        doReturn(Arrays.asList(player(f.alice, "Alice", true), player(f.bob, "Bob", true)))
                .when(f.server).getOnlinePlayers();
        Collection<String> names = f.access.onlineNames();
        assertEquals(Arrays.asList("Alice", "Bob"), names);
        verify(f.server, never()).getOfflinePlayers();
        verify(f.server, never()).getOfflinePlayer(anyString());
    }

    @Test public void idleLobbyOfflineAndDebugPlayersDoNotPreventReset() throws Exception {
        Fixture f = new Fixture();
        f.profiles.create(f.alice);
        f.profiles.create(f.bob).setState(PlayerState.DEBUG);
        assertNull(f.access.busyReason(f.alice));
        assertNull(f.access.busyReason(f.bob));
        assertNull(f.access.busyReason(UUID.randomUUID()));
        assertNull(f.access.busyReason(null));
    }

    @Test public void everyCombatOrQueueProfileStateBlocksSelectedPlayerAndAll() throws Exception {
        for (PlayerState state : Arrays.asList(PlayerState.QUEUE, PlayerState.STARTING,
                PlayerState.FIGHTING, PlayerState.ENDING)) {
            Fixture f = new Fixture();
            PlayerProfile alice = f.profiles.create(f.alice);
            alice.setState(state);
            assertNotNull(state.name(), f.access.busyReason(f.alice));
            assertNotNull(state.name(), f.access.busyReason(null));
            assertNull(f.access.busyReason(f.bob));
            assertEquals(state, alice.getState());
        }
    }

    @Test public void realQueueRegistryBlocksEvenWhenProfileIsAbsentOrInconsistent() throws Exception {
        Fixture f = new Fixture();
        MatchQueue queue = f.queue("boxing");
        queue.add(f.alice);
        assertTrue(f.access.busyReason(f.alice).contains("queued"));
        assertTrue(f.access.busyReason(null).contains("queued"));
        assertNull(f.access.busyReason(f.bob));
        f.profiles.create(f.alice).setState(PlayerState.LOBBY);
        assertNotNull(f.access.busyReason(f.alice));
        assertEquals(1, queue.size());
        queue.remove(f.alice);
        assertNull(f.access.busyReason(f.alice));
    }

    @Test public void realPvpRegistryBlocksStartingFightingAndEndingMatches() throws Exception {
        Fixture f = new Fixture();
        Match match = new Match(f.alice, f.bob, "combo", "arena");
        assertTrue(f.matches.register(match));
        assertPvpBlocked(f);
        assertTrue(match.markFighting());
        assertPvpBlocked(f);
        assertTrue(match.beginEnding());
        assertPvpBlocked(f);
        f.matches.remove(match);
        assertNull(f.access.busyReason(null));
    }

    @Test public void realBotRegistryBlocksBothOrdinaryAndCertificationMatches() throws Exception {
        for (boolean placement : Arrays.asList(false, true)) {
            Fixture f = new Fixture();
            BotMatch match = new BotMatch(f.alice, UUID.randomUUID(), "nodebuff", "arena", placement);
            f.registerBot(match);
            assertBotBlocked(f);
            match.markFighting();
            assertBotBlocked(f);
            assertTrue(match.beginEnding());
            assertBotBlocked(f);
            assertEquals(1, f.bots.size());
        }
    }

    @Test public void notificationOnlyIncludesSelectedOnlinePlayersAndSelectedKits() throws Exception {
        Fixture f = new Fixture();
        RatingService ratings = ratings();
        ratings.recordPlacement(f.alice, "boxing", UUID.randomUUID(), 50.0D);
        ratings.recordPlacement(f.alice, "combo", UUID.randomUUID(), 50.0D);
        ratings.recordPlacement(f.bob, "boxing", UUID.randomUUID(), 50.0D);
        Player alice = player(f.alice, "Alice", true);
        Player bob = player(f.bob, "Bob", true);
        when(f.server.getPlayer(f.alice)).thenReturn(alice);
        when(f.server.getPlayer(f.bob)).thenReturn(bob);
        CertificationResetPlan plan = ratings.previewCertificationReset(f.alice, "boxing");
        f.access.notifyReset(plan);
        verify(alice).sendMessage(contains("certification and ELO: boxing."));
        verify(bob, never()).sendMessage(anyString());
        verify(f.server, never()).getPlayer(f.bob);
        assertEquals(1, ratings.getPlacementCount(f.alice, "boxing"));
    }

    @Test public void offlineAndAlreadyDisconnectedPlayersAreNotNotified() throws Exception {
        Fixture f = new Fixture();
        RatingService ratings = ratings();
        ratings.recordPlacement(f.alice, "boxing", UUID.randomUUID(), 50.0D);
        ratings.recordPlacement(f.bob, "boxing", UUID.randomUUID(), 50.0D);
        Player disconnected = player(f.alice, "Alice", false);
        when(f.server.getPlayer(f.alice)).thenReturn(disconnected);
        f.access.notifyReset(ratings.previewCertificationReset(null, null));
        verify(disconnected, never()).sendMessage(anyString());
        verify(f.server).getPlayer(f.bob);
    }

    private static void assertPvpBlocked(Fixture f) {
        assertTrue(f.access.busyReason(f.alice).contains("in a match"));
        assertTrue(f.access.busyReason(f.bob).contains("in a match"));
        assertTrue(f.access.busyReason(null).contains("in a match"));
        assertNull(f.access.busyReason(UUID.randomUUID()));
    }

    private static void assertBotBlocked(Fixture f) {
        assertTrue(f.access.busyReason(f.alice).contains("in a match"));
        assertTrue(f.access.busyReason(null).contains("in a match"));
        assertNull(f.access.busyReason(f.bob));
    }

    private RatingService ratings() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return new RatingService(temporary.getRoot(), logger);
    }

    private static Player player(UUID id, String name, boolean online) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn(name);
        when(player.isOnline()).thenReturn(online);
        return player;
    }

    private static OfflinePlayer offline(UUID id, String name) {
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn(name);
        return player;
    }

    private static final class Fixture {
        final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        final Server server = mock(Server.class);
        final ProfileManager profiles = new ProfileManager();
        final MatchManager matches = new MatchManager();
        final KitManager kits = new KitManager();
        final QueueManager queues = new QueueManager(profiles, kits, null, null, null);
        final BotService bots;
        final TierResetAccess access;

        Fixture() throws Exception {
            YamlConfiguration config = new YamlConfiguration();
            BotSetting.resetAll(config);
            PracticePlugin plugin = new ObjenesisStd().newInstance(PracticePlugin.class);
            Field configField = JavaPlugin.class.getDeclaredField("newConfig");
            configField.setAccessible(true);
            configField.set(plugin, config);
            bots = new BotService(plugin, profiles, null, kits, queues, null, null, null, null, null);
            access = new TierResetAccess(server, profiles, matches, bots, queues);
            when(server.getOfflinePlayers()).thenReturn(new OfflinePlayer[0]);
        }

        MatchQueue queue(String kit) {
            for (MatchQueue queue : queues.all()) {
                if (queue.getKitId().equals(kit)) return queue;
            }
            throw new AssertionError("Unknown kit: " + kit);
        }

        @SuppressWarnings("unchecked")
        void registerBot(BotMatch match) throws Exception {
            Field registry = BotService.class.getDeclaredField("matchesByPlayer");
            registry.setAccessible(true);
            ((Map<UUID, BotMatch>) registry.get(bots)).put(match.getPlayerId(), match);
        }
    }
}
