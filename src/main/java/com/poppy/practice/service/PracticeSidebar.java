package com.poppy.practice.service;

import com.poppy.practice.match.BoxingRules;
import com.poppy.practice.rating.RatingService;
import org.bukkit.ChatColor;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/** One reusable sidebar; line values and visibility change only when necessary. */
final class PracticeSidebar {
    private static final String TITLE = ChatColor.AQUA.toString()
            + ChatColor.BOLD + "Poppy Practice";
    private final Scoreboard scoreboard;
    private final Objective objective;
    private final Line selfPing;
    private final Line opponentPing;
    private final Line online;
    private final Line selfHits;
    private final Line opponentHits;
    private final Line hitDifference;
    private final Line nodebuffRating;
    private final Line boxingRating;
    private final Line comboRating;
    private final Line selfRating;
    private final Line opponentRating;

    PracticeSidebar(Scoreboard scoreboard) {
        this.scoreboard = scoreboard;
        objective = scoreboard.registerNewObjective("practice", "dummy");
        objective.setDisplayName(TITLE);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        separator("top", ChatColor.DARK_GRAY.toString(), 10);
        selfRating = new Line("self_rating", ChatColor.DARK_GREEN.toString(), 9,
                ChatColor.WHITE + "Your ELO: ");
        opponentRating = new Line("opponent_rating", ChatColor.DARK_RED.toString(), 8,
                ChatColor.WHITE + "Their ELO: ");
        nodebuffRating = new Line("nodebuff_rating", ChatColor.BLUE.toString(), 6,
                ChatColor.WHITE + "NoDebuff: ");
        boxingRating = new Line("boxing_rating", ChatColor.DARK_AQUA.toString(), 5,
                ChatColor.WHITE + "Boxing: ");
        comboRating = new Line("combo_rating", ChatColor.DARK_PURPLE.toString(), 4,
                ChatColor.WHITE + "Combo: ");
        selfHits = new Line("self_hits", ChatColor.AQUA.toString(), 6,
                ChatColor.WHITE + "Your Hits: ");
        opponentHits = new Line("opponent_hits", ChatColor.LIGHT_PURPLE.toString(), 5,
                ChatColor.WHITE + "Their Hits: ");
        hitDifference = new Line("hit_difference", ChatColor.YELLOW.toString(), 4,
                ChatColor.WHITE + "Difference: ");
        selfPing = new Line("self_ping", ChatColor.GREEN.toString(), 3,
                ChatColor.WHITE + "Your Ping: ");
        opponentPing = new Line("opponent_ping", ChatColor.RED.toString(), 2,
                ChatColor.WHITE + "Opponent: ");
        online = new Line("online", ChatColor.GOLD.toString(), 3,
                ChatColor.WHITE + "Online: ");
        separator("bottom", ChatColor.BLACK.toString(), 1);
    }

    Scoreboard getScoreboard() {
        return scoreboard;
    }

    void showLobby(int onlinePlayers) {
        showLobby(onlinePlayers, null, null, null);
    }

    void showLobby(int onlinePlayers, String nodebuff, String boxing, String combo) {
        showHitLines(false);
        showValue(nodebuffRating, nodebuff);
        showValue(boxingRating, boxing);
        showValue(comboRating, combo);
        selfRating.setVisible(false);
        opponentRating.setVisible(false);
        selfPing.setVisible(false);
        opponentPing.setVisible(false);
        online.setValue(ChatColor.GREEN
                + MatchScoreboardService.onlineCountText(onlinePlayers));
        online.setVisible(true);
    }

    void showMatch(int ownPing, int otherPing) {
        showMatch(ownPing, otherPing, null, null);
    }

    void showMatch(int ownPing, int otherPing, String ownRating, String otherRating) {
        showHitLines(false);
        showMatchRatings(ownRating, otherRating);
        showPings(ownPing, otherPing);
    }

    void showBoxing(int ownPing, int otherPing, int ownHits, int otherHits) {
        showBoxing(ownPing, otherPing, ownHits, otherHits, null, null);
    }

    void showBoxing(int ownPing, int otherPing, int ownHits, int otherHits,
                    String ownRating, String otherRating) {
        showMatchRatings(ownRating, otherRating);
        showPings(ownPing, otherPing);
        selfHits.setValue(ChatColor.GREEN + hitText(ownHits));
        opponentHits.setValue(ChatColor.RED + hitText(otherHits));
        hitDifference.setValue(differenceText(ownHits, otherHits));
        showHitLines(true);
    }

    private void showMatchRatings(String ownRating, String otherRating) {
        nodebuffRating.setVisible(false);
        boxingRating.setVisible(false);
        comboRating.setVisible(false);
        showValue(selfRating, ownRating);
        showValue(opponentRating, otherRating);
    }

    private void showValue(Line line, String value) {
        if (value != null) { line.setValue(value); }
        line.setVisible(value != null);
    }

    static String ratingText(long ratingMilli) {
        // Legacy clients cap each team suffix at 16 characters; storage retains the full number.
        String number = ratingMilli > 999999999999L ? ">999999999.999" : RatingService.format(ratingMilli);
        return ChatColor.AQUA + number;
    }

    static String placementText(int completed) {
        return ChatColor.YELLOW + "Tier " + Math.max(0, Math.min(3, completed)) + "/3";
    }

    private void showHitLines(boolean visible) {
        selfHits.setVisible(visible);
        opponentHits.setVisible(visible);
        hitDifference.setVisible(visible);
    }

    private void showPings(int ownPing, int otherPing) {
        online.setVisible(false);
        selfPing.setValue(pingText(ownPing));
        opponentPing.setValue(pingText(otherPing));
        selfPing.setVisible(true);
        opponentPing.setVisible(true);
    }

    static String hitText(int hits) {
        return normalizedHits(hits) + "/" + BoxingRules.HITS_TO_WIN;
    }

    static String differenceText(int ownHits, int otherHits) {
        int difference = normalizedHits(ownHits) - normalizedHits(otherHits);
        ChatColor color = difference > 0 ? ChatColor.GREEN
                : difference < 0 ? ChatColor.RED : ChatColor.GRAY;
        return color.toString() + (difference > 0 ? "+" : "") + difference;
    }

    private static int normalizedHits(int hits) {
        return Math.max(0, Math.min(BoxingRules.HITS_TO_WIN, hits));
    }

    static String pingText(int ping) {
        int normalized = MatchScoreboardService.normalizePing(ping);
        return MatchScoreboardService.pingColor(normalized).toString() + normalized + "ms";
    }

    private void separator(String name, String entry, int score) {
        Line line = new Line(name, entry, score, ChatColor.DARK_GRAY + "--------------");
        line.setValue(ChatColor.DARK_GRAY + "---");
        line.setVisible(true);
    }

    private final class Line {
        private final Team team;
        private final String entry;
        private final int score;
        private String value;
        private boolean visible;

        private Line(String name, String entry, int score, String prefix) {
            this.entry = entry;
            this.score = score;
            team = scoreboard.registerNewTeam(name);
            team.setPrefix(prefix);
            team.addEntry(entry);
        }

        private void setValue(String value) {
            if (!value.equals(this.value)) {
                team.setSuffix(value);
                this.value = value;
            }
        }

        private void setVisible(boolean visible) {
            if (visible == this.visible) {
                return;
            }
            if (visible) {
                objective.getScore(entry).setScore(score);
            } else {
                scoreboard.resetScores(entry);
            }
            this.visible = visible;
        }
    }
}
