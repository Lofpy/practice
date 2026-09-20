package com.poppy.practice.queue;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.arena.ArenaManager;
import com.poppy.practice.config.MessageConfig;
import com.poppy.practice.kit.KitManager;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.rating.RatingService;
import com.poppy.practice.service.MatchService;
import com.poppy.practice.service.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RankedQueueTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void incompatibleHeadDoesNotBlockAnEligibleLaterPair() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        Map<UUID, Long> ratings = ratings(a, 1800000L, b, 1500000L, c, 1550000L);
        assertArrayEquals(new UUID[] { b, c }, QueueManager.selectPair(Arrays.asList(a, b, c), ratings::get));
    }

    @Test public void oldestCompatiblePlayerAndThenOldestOpponentHavePriority() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
        Map<UUID, Long> ratings = ratings(a, 1650000L, b, 1800000L, c, 1700000L, d, 1650000L);
        assertArrayEquals(new UUID[] { a, c }, QueueManager.selectPair(Arrays.asList(a, b, c, d), ratings::get));
    }

    @Test public void hundredPointsIsInclusiveButOneMilliBeyondIsNot() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        Map<UUID, Long> ratings = ratings(a, 1500000L, b, 1600000L);
        assertNotNull(QueueManager.selectPair(Arrays.asList(a, b), ratings::get));
        ratings.put(b, 1600001L);
        assertNull(QueueManager.selectPair(Arrays.asList(a, b), ratings::get));
    }

    @Test public void selectingPairDoesNotMutateQueueOrder() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        List<UUID> original = Arrays.asList(a, b, c);
        List<UUID> snapshot = new ArrayList<UUID>(original);
        QueueManager.selectPair(snapshot, ratings(a, 1800000L, b, 1500000L, c, 1500000L)::get);
        assertEquals(original, snapshot);
    }

    @Test public void incompatiblePlayersNeverMatchAfterRepeatedRetries() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        Map<UUID, Long> ratings = ratings(a, 1500000L, b, 1600001L);
        for (int attempt = 0; attempt < 1000; attempt++) {
            assertNull(QueueManager.selectPair(Arrays.asList(a, b), ratings::get));
        }
    }

    @Test public void emptyAndSingleEntryQueuesCannotMatch() {
        assertNull(QueueManager.selectPair(Collections.emptyList(), ignored -> 1500000L));
        assertNull(QueueManager.selectPair(Collections.singletonList(UUID.randomUUID()), ignored -> 1500000L));
    }

    @Test public void legacyUnwiredQueueStillUsesFirstInFirstOut() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        assertArrayEquals(new UUID[] { a, b }, QueueManager.selectPair(Arrays.asList(a, b, c), null));
    }

    @Test public void unqualifiedPlayerCannotJoinAndReceivesCurrentKitCounter() {
        ProfileManager profiles = new ProfileManager();
        RatingService rating = new RatingService(temporary.getRoot(), Logger.getAnonymousLogger());
        QueueManager queue = new QueueManager(profiles, new KitManager(), null,
                new MessageService(new MessageConfig(null)), null);
        queue.setRatingService(rating);
        Player player = player(UUID.randomUUID());
        profiles.getOrCreate(player.getUniqueId()).setSelectedKitId("nodebuff");
        rating.recordPlacement(player.getUniqueId(), "nodebuff", UUID.randomUUID(), 50);
        assertFalse(queue.join(player));
        assertEquals(0, queue.size("nodebuff"));
        assertEquals(PlayerState.LOBBY, profiles.get(player.getUniqueId()).getState());
        verify(player).sendMessage(contains("1/3"));
        verify(player).sendMessage(contains("/tier nodebuff"));
    }

    @Test public void opDoesNotBypassCertificationAndAnotherKitCannotUnlockSelectedQueue() {
        ProfileManager profiles = new ProfileManager();
        RatingService rating = new RatingService(temporary.getRoot(), Logger.getAnonymousLogger());
        QueueManager queue = new QueueManager(profiles, new KitManager(), null,
                new MessageService(new MessageConfig(null)), null);
        queue.setRatingService(rating);
        Player player = player(UUID.randomUUID());
        when(player.isOp()).thenReturn(true);
        when(player.hasPermission(anyString())).thenReturn(true);
        for (int i = 0; i < 3; i++) {
            rating.recordPlacement(player.getUniqueId(), "boxing", UUID.randomUUID(), 100);
        }
        profiles.getOrCreate(player.getUniqueId()).setSelectedKitId("combo");
        assertFalse(queue.join(player));
        verify(player).sendMessage(contains("0/3"));
        assertEquals(0, queue.size("combo"));
    }

    @Test public void alreadyWaitingPlayersAreRecheckedForCertificationAndOfflineCleanup() throws Exception {
        ProfileManager profiles = new ProfileManager();
        RatingService rating = new RatingService(temporary.getRoot(), Logger.getAnonymousLogger());
        QueueManager queue = new QueueManager(profiles, new KitManager(), null, null, null);
        queue.setRatingService(rating);
        MatchQueue waiting = queue.all().iterator().next();
        UUID offlineId = UUID.randomUUID(), eligibleId = UUID.randomUUID();
        Player online = player(eligibleId);
        for (int i = 0; i < 3; i++) { rating.recordPlacement(eligibleId, "nodebuff", UUID.randomUUID(), 50); }
        for (UUID id : Arrays.asList(offlineId, eligibleId)) {
            PlayerProfile profile = profiles.getOrCreate(id);
            profile.setState(PlayerState.QUEUE);
            profile.setQueuedKitId("nodebuff");
            waiting.add(id);
        }
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previous = serverField.get(null);
        Server server = mock(Server.class);
        when(server.getPlayer(eligibleId)).thenReturn(online);
        serverField.set(null, server);
        try {
            Method eligible = QueueManager.class.getDeclaredMethod("eligiblePlayers", MatchQueue.class, String.class);
            eligible.setAccessible(true);
            assertEquals(Collections.singletonList(eligibleId), eligible.invoke(queue, waiting, "nodebuff"));
            assertEquals(Collections.singletonList(eligibleId), new ArrayList<UUID>(waiting.snapshot()));
            assertEquals(PlayerState.LOBBY, profiles.get(offlineId).getState());
        } finally {
            serverField.set(null, previous);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void wiringCannotSilentlyDisableRatingsWithNull() {
        new QueueManager(new ProfileManager(), new KitManager(), null, null, null).setRatingService(null);
    }

    @Test public void unavailableArenaLeavesUnmatchedHeadAndSelectedLaterPairInOriginalOrder() throws Exception {
        ProfileManager profiles = new ProfileManager();
        RatingService rating = new RatingService(temporary.getRoot(), Logger.getAnonymousLogger());
        PracticePlugin plugin = new ObjenesisStd().newInstance(PracticePlugin.class);
        Field configuration = JavaPlugin.class.getDeclaredField("newConfig");
        configuration.setAccessible(true);
        configuration.set(plugin, new YamlConfiguration());
        ArenaManager arenas = new ArenaManager(plugin);
        QueueManager queue = new QueueManager(profiles, new KitManager(), arenas,
                new MessageService(new MessageConfig(plugin)), null);
        queue.setRatingService(rating);
        queue.setMatchService(new ObjenesisStd().newInstance(MatchService.class));
        Map<UUID, Player> online = new HashMap<UUID, Player>();
        MatchQueue waiting = queue.all().iterator().next();
        List<UUID> order = Arrays.asList(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        for (int index = 0; index < order.size(); index++) {
            UUID id = order.get(index);
            online.put(id, player(id));
            for (int attempt = 0; attempt < 3; attempt++) {
                rating.recordPlacement(id, "nodebuff", UUID.randomUUID(), index == 0 ? 100 : 0);
            }
            PlayerProfile profile = profiles.getOrCreate(id);
            profile.setQueuedKitId("nodebuff");
            profile.setState(PlayerState.QUEUE);
            waiting.add(id);
        }
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previous = serverField.get(null);
        Server server = mock(Server.class);
        when(server.getPlayer(any(UUID.class))).thenAnswer(call -> online.get(call.getArguments()[0]));
        serverField.set(null, server);
        try {
            queue.tryMatch("nodebuff");
            assertEquals(order, new ArrayList<UUID>(waiting.snapshot()));
            assertEquals(3, queue.size("nodebuff"));
            for (UUID id : order) {
                assertEquals(PlayerState.QUEUE, profiles.get(id).getState());
                verify(online.get(id)).sendMessage(contains("使用できるアリーナがありません"));
            }
        } finally {
            serverField.set(null, previous);
        }
    }

    private static Player player(UUID id) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        return player;
    }

    private static Map<UUID, Long> ratings(Object... values) {
        Map<UUID, Long> result = new HashMap<UUID, Long>();
        for (int i = 0; i < values.length; i += 2) { result.put((UUID) values[i], (Long) values[i + 1]); }
        return result;
    }
}
