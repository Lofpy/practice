package com.poppy.practice.service;

import com.poppy.practice.bot.BotMatch;
import com.poppy.practice.match.Match;
import com.poppy.practice.network.PlayerPingService;
import com.poppy.practice.rating.RatingService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

public class MatchScoreboardServiceTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void wiredRatingServiceRefreshesIndependentLobbyProgressAndElo() throws Exception {
        try (Fixture fixture = new Fixture()) {
            RatingService ratings = new RatingService(temporary.getRoot(), Logger.getAnonymousLogger());
            fixture.service.setRatingService(ratings);
            fixture.service.showLobby(fixture.viewer);
            verify(fixture.teams.get("nodebuff_rating")).setSuffix(ChatColor.YELLOW + "Tier 0/3");
            UUID id = fixture.viewer.getUniqueId();
            for (int i = 0; i < 3; i++) { ratings.recordPlacement(id, "nodebuff", UUID.randomUUID(), 50); }
            ratings.recordPlacement(id, "boxing", UUID.randomUUID(), 100);
            fixture.refresh();
            verify(fixture.teams.get("nodebuff_rating")).setSuffix(ChatColor.AQUA + "1650.000");
            verify(fixture.teams.get("boxing_rating")).setSuffix(ChatColor.YELLOW + "Tier 1/3");
            verify(fixture.teams.get("combo_rating"), times(1)).setSuffix(ChatColor.YELLOW + "Tier 0/3");
        }
    }

    @Test public void wiredPvpRatingsUseOnlyCurrentKitAndDisappearInBotFight() throws Exception {
        try (Fixture fixture = new Fixture()) {
            RatingService ratings = new RatingService(temporary.getRoot(), Logger.getAnonymousLogger());
            fixture.service.setRatingService(ratings);
            UUID own = fixture.viewer.getUniqueId(), other = fixture.opponent.getUniqueId();
            for (int i = 0; i < 3; i++) {
                ratings.recordPlacement(own, "nodebuff", UUID.randomUUID(), 0);
                ratings.recordPlacement(own, "boxing", UUID.randomUUID(), 50);
                ratings.recordPlacement(other, "boxing", UUID.randomUUID(), 60);
            }
            Match match = new Match(own, other, "boxing", "arena");
            fixture.service.show(fixture.viewer, fixture.opponent, match);
            verify(fixture.teams.get("self_rating")).setSuffix(ChatColor.AQUA + "1650.000");
            verify(fixture.teams.get("opponent_rating")).setSuffix(ChatColor.AQUA + "1680.000");
            fixture.service.showBotMatch(fixture.viewer, fixture.opponent, fixture.botMatch("boxing"));
            verify(fixture.board).resetScores(ChatColor.DARK_GREEN.toString());
            verify(fixture.board).resetScores(ChatColor.DARK_RED.toString());
        }
    }

    @Test
    public void boxingBotScoreboardRefreshesBothHitCountsEveryTwoTicks() throws Exception {
        try (Fixture fixture = new Fixture()) {
            BotMatch match = fixture.botMatch("boxing");
            fixture.service.showBotMatch(fixture.viewer, fixture.opponent, match);
            verify(fixture.teams.get("self_hits")).setSuffix(ChatColor.GREEN + "0/100");
            match.getStats(fixture.viewer.getUniqueId()).recordMeleeHit(false);
            match.getStats(fixture.opponent.getUniqueId()).recordMeleeHit(false);
            match.getStats(fixture.opponent.getUniqueId()).recordMeleeHit(false);

            fixture.refresh();

            verify(fixture.teams.get("self_hits")).setSuffix(ChatColor.GREEN + "1/100");
            verify(fixture.teams.get("opponent_hits")).setSuffix(ChatColor.RED + "2/100");
            verify(fixture.teams.get("hit_difference")).setSuffix(ChatColor.RED + "-1");
            verify(fixture.scheduler).runTaskTimer((Plugin) isNull(), any(Runnable.class),
                    eq(2L), eq(2L));
        }
    }

    @Test
    public void switchingBotModesAndReturningToLobbyClearsOldMatchReferences() throws Exception {
        try (Fixture fixture = new Fixture()) {
            BotMatch boxing = fixture.botMatch("boxing");
            fixture.service.showBotMatch(fixture.viewer, fixture.opponent, boxing);
            fixture.service.showBotMatch(fixture.viewer, fixture.opponent, fixture.botMatch("nodebuff"));
            verify(fixture.board).resetScores(ChatColor.AQUA.toString());
            verify(fixture.board).resetScores(ChatColor.LIGHT_PURPLE.toString());
            verify(fixture.board).resetScores(ChatColor.YELLOW.toString());

            fixture.service.showBotMatch(fixture.viewer, fixture.opponent, boxing);
            fixture.service.showLobby(fixture.viewer);
            fixture.refresh();

            verify(fixture.teams.get("online")).setSuffix(ChatColor.GREEN + "1");
            assertNull(fixture.displayField("match"));
            assertNull(fixture.displayField("botMatch"));
            assertNull(fixture.displayField("opponent"));
        }
    }

    @Test
    public void switchingBetweenHumanAndBotBoxingDoesNotUsePreviousScores() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Match humanMatch = new Match(fixture.viewer.getUniqueId(), fixture.opponent.getUniqueId(),
                    "boxing", "arena");
            humanMatch.getStats(fixture.viewer.getUniqueId()).recordMeleeHit(false);
            fixture.service.show(fixture.viewer, fixture.opponent, humanMatch);
            fixture.service.showBotMatch(fixture.viewer, fixture.opponent, fixture.botMatch("boxing"));
            assertNull(fixture.displayField("match"));
            verify(fixture.teams.get("self_hits")).setSuffix(ChatColor.GREEN + "0/100");

            fixture.service.show(fixture.viewer, fixture.opponent, humanMatch);
            assertNull(fixture.displayField("botMatch"));
            fixture.service.show(fixture.viewer, fixture.opponent);
            assertNull(fixture.displayField("botMatch"));
            assertNull(fixture.displayField("match"));
        }
    }

    @Test
    public void normalizesInvalidAndExcessivePingValues() {
        assertEquals(0, MatchScoreboardService.normalizePing(-1));
        assertEquals(42, MatchScoreboardService.normalizePing(42));
        assertEquals(9999, MatchScoreboardService.normalizePing(12000));
    }

    @Test
    public void colorsPingByLatency() {
        assertEquals(ChatColor.GREEN, MatchScoreboardService.pingColor(50));
        assertEquals(ChatColor.YELLOW, MatchScoreboardService.pingColor(51));
        assertEquals(ChatColor.GOLD, MatchScoreboardService.pingColor(101));
        assertEquals(ChatColor.RED, MatchScoreboardService.pingColor(151));
    }

    @Test
    public void formatsLobbyOnlineCount() {
        assertEquals("0", MatchScoreboardService.onlineCountText(0));
        assertEquals("12", MatchScoreboardService.onlineCountText(12));
        assertEquals("0", MatchScoreboardService.onlineCountText(-1));
    }

    private static final class Fixture implements AutoCloseable {
        private final Field serverField;
        private final Object previousServer;
        private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        private final Scoreboard board = mock(Scoreboard.class);
        private final Player viewer = player();
        private final Player opponent = player();
        private final Map<String, Team> teams = new HashMap<String, Team>();
        private final List<Runnable> tasks = new ArrayList<Runnable>();
        private final MatchScoreboardService service;

        private Fixture() throws Exception {
            Server server = mock(Server.class);
            ScoreboardManager boards = mock(ScoreboardManager.class);
            Objective objective = mock(Objective.class);
            when(server.getScheduler()).thenReturn(scheduler);
            when(server.getScoreboardManager()).thenReturn(boards);
            doReturn(Collections.singletonList(viewer)).when(server).getOnlinePlayers();
            when(boards.getNewScoreboard()).thenReturn(board);
            when(board.registerNewObjective("practice", "dummy")).thenReturn(objective);
            when(objective.getScore(anyString())).thenReturn(mock(Score.class));
            when(viewer.getScoreboard()).thenReturn(board);
            when(board.registerNewTeam(anyString())).thenAnswer(invocation -> {
                Team team = mock(Team.class);
                teams.put((String) invocation.getArguments()[0], team);
                return team;
            });
            when(scheduler.runTaskTimer((Plugin) isNull(), any(Runnable.class), anyLong(), anyLong()))
                    .thenAnswer(invocation -> {
                        tasks.add((Runnable) invocation.getArguments()[1]);
                        return mock(BukkitTask.class);
                    });
            serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            previousServer = serverField.get(null);
            serverField.set(null, server);
            try {
                service = new MatchScoreboardService(null, new PlayerPingService(null));
            } catch (RuntimeException | Error exception) {
                serverField.set(null, previousServer);
                throw exception;
            }
        }

        private BotMatch botMatch(String kit) {
            return new BotMatch(viewer.getUniqueId(), opponent.getUniqueId(), kit, "arena");
        }

        private void refresh() {
            tasks.get(tasks.size() - 1).run();
        }

        private Object displayField(String name) throws Exception {
            Field displays = MatchScoreboardService.class.getDeclaredField("displays");
            displays.setAccessible(true);
            Object display = ((Map<?, ?>) displays.get(service)).get(viewer.getUniqueId());
            Field field = display.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(display);
        }

        @Override
        public void close() throws Exception {
            serverField.set(null, previousServer);
        }

        private static Player player() {
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.isOnline()).thenReturn(true);
            return player;
        }
    }
}
