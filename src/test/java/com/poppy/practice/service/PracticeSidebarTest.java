package com.poppy.practice.service;

import org.bukkit.ChatColor;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PracticeSidebarTest {
    @Test public void brandingIsRedAndScoreboardLabelsStayEnglish() {
        Board board = new Board();
        PracticeSidebar sidebar = new PracticeSidebar(board.scoreboard);
        sidebar.showLobby(1, PracticeSidebar.placementText(0), PracticeSidebar.placementText(0), PracticeSidebar.placementText(0));
        assertEquals(ChatColor.RED.toString() + ChatColor.BOLD + "Practice", board.title);
        assertEquals(ChatColor.WHITE + "Your Ping: ", board.prefixes.get("self_ping"));
        assertEquals(ChatColor.WHITE + "Online: ", board.prefixes.get("online"));
        assertEquals(ChatColor.YELLOW + "Tier 0/3", board.suffixes.get("nodebuff_rating"));
        sidebar.showMatch(20, 30);
        assertEquals(ChatColor.RED.toString() + ChatColor.BOLD + "Practice", board.title);
        sidebar.showBoxing(20, 30, 1, 2);
        assertEquals(ChatColor.RED.toString() + ChatColor.BOLD + "Practice", board.title);
    }

    @Test
    public void boxingDisplaysHitsDifferenceAndPingWithoutResendingUnchangedLines() {
        Board board = new Board();
        PracticeSidebar sidebar = new PracticeSidebar(board.scoreboard);
        sidebar.showBoxing(25, 75, 42, 30);

        assertEquals(7, board.entries.size());
        assertEquals(ChatColor.GREEN + "42/100", board.suffixes.get("self_hits"));
        assertEquals(ChatColor.RED + "30/100", board.suffixes.get("opponent_hits"));
        assertEquals(ChatColor.GREEN + "+12", board.suffixes.get("hit_difference"));
        assertEquals(ChatColor.GREEN + "25ms", board.suffixes.get("self_ping"));
        int suffixWrites = board.suffixWrites;
        int scoreWrites = board.scoreWrites;
        sidebar.showBoxing(25, 75, 42, 30);
        assertEquals(suffixWrites, board.suffixWrites);
        assertEquals(scoreWrites, board.scoreWrites);

        sidebar.showBoxing(25, 75, 42, 43);
        assertEquals(ChatColor.RED + "-1", board.suffixes.get("hit_difference"));
        assertEquals(suffixWrites + 2, board.suffixWrites);
        assertEquals(scoreWrites, board.scoreWrites);
    }

    @Test
    public void leavingBoxingHidesItsScoreLinesInRegularMatchesAndLobby() {
        Board board = new Board();
        PracticeSidebar sidebar = new PracticeSidebar(board.scoreboard);
        sidebar.showBoxing(25, 75, 100, 50);
        sidebar.showMatch(25, 75);
        assertEquals(4, board.entries.size());
        assertFalse(board.entries.containsKey(ChatColor.AQUA.toString()));
        assertFalse(board.entries.containsKey(ChatColor.LIGHT_PURPLE.toString()));
        assertFalse(board.entries.containsKey(ChatColor.YELLOW.toString()));

        sidebar.showBoxing(25, 75, 0, 0);
        sidebar.showLobby(2);
        assertEquals(3, board.entries.size());
        assertTrue(board.entries.containsKey(ChatColor.GOLD.toString()));
        assertFalse(board.entries.containsKey(ChatColor.AQUA.toString()));
        assertEquals(1, board.objectiveRegistrations);
    }

    @Test
    public void boxingCountersAreBoundedAndFitLegacyTeamFields() {
        assertEquals("0/100", PracticeSidebar.hitText(-1));
        assertEquals("100/100", PracticeSidebar.hitText(Integer.MAX_VALUE));
        assertEquals(ChatColor.GRAY + "0", PracticeSidebar.differenceText(25, 25));
        assertEquals(ChatColor.GREEN + "+100",
                PracticeSidebar.differenceText(Integer.MAX_VALUE, Integer.MIN_VALUE));
        assertEquals(ChatColor.RED + "-100",
                PracticeSidebar.differenceText(Integer.MIN_VALUE, Integer.MAX_VALUE));
        PracticeSidebar sidebar = new PracticeSidebar(new Board().scoreboard);
        sidebar.showBoxing(Integer.MAX_VALUE, -1, Integer.MAX_VALUE, Integer.MIN_VALUE);
    }

    @Test
    public void unchangedRefreshDoesNotResendTeamOrScoreUpdates() {
        Board board = new Board();
        PracticeSidebar sidebar = new PracticeSidebar(board.scoreboard);
        sidebar.showMatch(25, 75);
        int suffixWrites = board.suffixWrites;
        int scoreWrites = board.scoreWrites;

        for (int tick = 0; tick < 20; tick++) {
            sidebar.showMatch(25, 75);
        }

        assertEquals(suffixWrites, board.suffixWrites);
        assertEquals(scoreWrites, board.scoreWrites);
        sidebar.showMatch(26, 75);
        assertEquals(suffixWrites + 1, board.suffixWrites);
        assertEquals(scoreWrites, board.scoreWrites);
    }

    @Test
    public void switchesLobbyAndMatchWithoutReplacingObjectiveOrKeepingOldLines() {
        Board board = new Board();
        PracticeSidebar sidebar = new PracticeSidebar(board.scoreboard);
        sidebar.showLobby(5);
        assertEquals(3, board.entries.size());
        assertTrue(board.entries.containsKey(ChatColor.GOLD.toString()));

        sidebar.showMatch(50, 100);
        assertEquals(4, board.entries.size());
        assertFalse(board.entries.containsKey(ChatColor.GOLD.toString()));
        assertTrue(board.entries.containsKey(ChatColor.GREEN.toString()));
        assertTrue(board.entries.containsKey(ChatColor.RED.toString()));

        sidebar.showLobby(6);
        assertEquals(3, board.entries.size());
        assertFalse(board.entries.containsKey(ChatColor.GREEN.toString()));
        assertFalse(board.entries.containsKey(ChatColor.RED.toString()));
        assertEquals(1, board.objectiveRegistrations);
        assertEquals(ChatColor.WHITE + "Online: ", board.prefixes.get("online"));
        assertEquals(board.prefixes.get("top"), board.prefixes.get("bottom"));
    }

    @Test
    public void largeOnlineCountsFitLegacyTeamFieldsAndUnchangedCountsAreNotResent() {
        Board board = new Board();
        PracticeSidebar sidebar = new PracticeSidebar(board.scoreboard);
        sidebar.showLobby(Integer.MAX_VALUE);
        int writes = board.suffixWrites;
        sidebar.showLobby(Integer.MAX_VALUE);
        assertEquals(writes, board.suffixWrites);
        assertEquals(ChatColor.GREEN + "2147483647", board.suffixes.get("online"));
    }

    @Test
    public void normalizesPingBeforeFormattingItsNumber() {
        assertEquals(ChatColor.GREEN + "0ms", PracticeSidebar.pingText(-1));
        assertEquals(ChatColor.RED + "9999ms", PracticeSidebar.pingText(Integer.MAX_VALUE));
    }

    @Test public void lobbyDisplaysAllThreeIndependentRatingsOrPlacementCounters() {
        Board board = new Board();
        PracticeSidebar sidebar = new PracticeSidebar(board.scoreboard);
        sidebar.showLobby(5, PracticeSidebar.ratingText(1650123L),
                PracticeSidebar.placementText(2), PracticeSidebar.placementText(0));
        assertEquals(6, board.entries.size());
        assertEquals(ChatColor.RED + "1650.123", board.suffixes.get("nodebuff_rating"));
        assertEquals(ChatColor.YELLOW + "Tier 2/3", board.suffixes.get("boxing_rating"));
        assertEquals(ChatColor.YELLOW + "Tier 0/3", board.suffixes.get("combo_rating"));
        assertEquals(ChatColor.WHITE + "Online: ", board.prefixes.get("online"));
        int suffixWrites = board.suffixWrites;
        int scoreWrites = board.scoreWrites;
        sidebar.showLobby(5, PracticeSidebar.ratingText(1650123L),
                PracticeSidebar.placementText(2), PracticeSidebar.placementText(0));
        assertEquals(suffixWrites, board.suffixWrites);
        assertEquals(scoreWrites, board.scoreWrites);
    }

    @Test public void rankedBoxingShowsBothPlayersRatingsAndKeepsItsHitAndPingLines() {
        Board board = new Board();
        PracticeSidebar sidebar = new PracticeSidebar(board.scoreboard);
        sidebar.showBoxing(25, 75, 42, 30, PracticeSidebar.ratingText(1650000L),
                PracticeSidebar.ratingText(1700000L));
        assertEquals(9, board.entries.size());
        assertEquals(ChatColor.RED + "1650.000", board.suffixes.get("self_rating"));
        assertEquals(ChatColor.RED + "1700.000", board.suffixes.get("opponent_rating"));
        assertEquals(ChatColor.GREEN + "42/100", board.suffixes.get("self_hits"));
        assertEquals(ChatColor.GREEN + "25ms", board.suffixes.get("self_ping"));
        int suffixWrites = board.suffixWrites;
        int scoreWrites = board.scoreWrites;
        sidebar.showBoxing(25, 75, 42, 30, PracticeSidebar.ratingText(1650000L),
                PracticeSidebar.ratingText(1700000L));
        assertEquals(suffixWrites, board.suffixWrites);
        assertEquals(scoreWrites, board.scoreWrites);
    }

    @Test public void switchingFromRankedToBotOrLobbyDoesNotLeakRatingLines() {
        Board board = new Board();
        PracticeSidebar sidebar = new PracticeSidebar(board.scoreboard);
        sidebar.showMatch(10, 20, PracticeSidebar.ratingText(1500000L), PracticeSidebar.ratingText(1550000L));
        assertEquals(6, board.entries.size());
        sidebar.showMatch(10, 20);
        assertEquals(4, board.entries.size());
        assertFalse(board.entries.containsKey(ChatColor.DARK_GREEN.toString()));
        assertFalse(board.entries.containsKey(ChatColor.DARK_RED.toString()));
        sidebar.showLobby(2, PracticeSidebar.placementText(0),
                PracticeSidebar.placementText(1), PracticeSidebar.placementText(2));
        assertEquals(6, board.entries.size());
        assertTrue(board.entries.containsKey(ChatColor.BLUE.toString()));
        sidebar.showBoxing(10, 20, 1, 1);
        assertEquals(7, board.entries.size());
        assertFalse(board.entries.containsKey(ChatColor.BLUE.toString()));
        assertFalse(board.entries.containsKey(ChatColor.DARK_AQUA.toString()));
        assertFalse(board.entries.containsKey(ChatColor.DARK_PURPLE.toString()));
    }

    @Test public void ratingAndPlacementTextFitLegacyFieldsEvenAtExtremeValues() {
        assertEquals(ChatColor.RED + "1500.000", PracticeSidebar.ratingText(1500000L));
        assertTrue(PracticeSidebar.ratingText(Long.MAX_VALUE).length() <= 16);
        assertEquals(ChatColor.YELLOW + "Tier 0/3", PracticeSidebar.placementText(-1));
        assertEquals(ChatColor.YELLOW + "Tier 3/3", PracticeSidebar.placementText(Integer.MAX_VALUE));
        PracticeSidebar sidebar = new PracticeSidebar(new Board().scoreboard);
        sidebar.showMatch(0, 0, PracticeSidebar.ratingText(Long.MAX_VALUE),
                PracticeSidebar.ratingText(999999999999L));
    }

    private static final class Board {
        private final Map<String, Integer> entries = new HashMap<String, Integer>();
        private final Map<String, String> prefixes = new HashMap<String, String>();
        private final Map<String, String> suffixes = new HashMap<String, String>();
        private int suffixWrites;
        private int scoreWrites;
        private int objectiveRegistrations;
        private String title;
        private final Objective objective = proxy(Objective.class, (instance, method, args) -> {
            if ("setDisplayName".equals(method.getName())) { title = (String) args[0]; }
            if ("getScore".equals(method.getName())) {
                String entry = (String) args[0];
                return proxy(Score.class, (score, operation, values) -> {
                    if ("setScore".equals(operation.getName())) {
                        entries.put(entry, (Integer) values[0]);
                        scoreWrites++;
                    }
                    return null;
                });
            }
            return null;
        });
        private final Scoreboard scoreboard = proxy(Scoreboard.class, (instance, method, args) -> {
            if ("registerNewObjective".equals(method.getName())) {
                objectiveRegistrations++;
                return objective;
            }
            if ("resetScores".equals(method.getName())) {
                entries.remove((String) args[0]);
                scoreWrites++;
            }
            if ("registerNewTeam".equals(method.getName())) {
                String team = (String) args[0];
                return proxy(Team.class, (line, operation, values) -> {
                    if ("setPrefix".equals(operation.getName())) {
                        assertTrue(((String) values[0]).length() <= 16);
                        prefixes.put(team, (String) values[0]);
                    }
                    if ("setSuffix".equals(operation.getName())) {
                        assertTrue(((String) values[0]).length() <= 16);
                        suffixes.put(team, (String) values[0]);
                        suffixWrites++;
                    }
                    return null;
                });
            }
            return null;
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),
                new Class<?>[] { type }, handler));
    }
}
