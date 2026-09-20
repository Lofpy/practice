package com.poppy.practice.service;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.bot.BotMatch;
import com.poppy.practice.match.BoxingRules;
import com.poppy.practice.match.Match;
import com.poppy.practice.network.PlayerPingService;
import com.poppy.practice.rating.RatingService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class MatchScoreboardService {
    private final Map<UUID, Display> displays = new HashMap<UUID, Display>();
    private final PlayerPingService pingService;
    private final BukkitTask updateTask;
    private RatingService ratings;

    public void setRatingService(RatingService ratings) {
        this.ratings = ratings;
    }

    public MatchScoreboardService(PracticePlugin plugin, PlayerPingService pingService) {
        this.pingService = pingService;
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                updateAll();
            }
        }, 2L, 2L);
    }

    public void show(Player viewer, Player opponent) {
        show(viewer, opponent, null);
    }

    public void show(Player viewer, Player opponent, Match match) {
        if (opponent == null) {
            return;
        }
        Display display = getOrCreate(viewer);
        if (display != null) {
            display.opponent = opponent;
            display.match = match;
            display.botMatch = null;
            update(display, 0);
            attach(display);
        }
    }

    public void showBotMatch(Player viewer, Player opponent, BotMatch match) {
        if (opponent == null) {
            return;
        }
        Display display = getOrCreate(viewer);
        if (display != null) {
            display.opponent = opponent;
            display.match = null;
            display.botMatch = match;
            update(display, 0);
            attach(display);
        }
    }

    public void showLobby(Player viewer) {
        Display display = getOrCreate(viewer);
        if (display != null) {
            display.opponent = null;
            display.match = null;
            display.botMatch = null;
            update(display, Bukkit.getOnlinePlayers().size());
            attach(display);
        }
    }

    private Display getOrCreate(Player viewer) {
        if (viewer == null || !viewer.isOnline()) {
            return null;
        }
        Display display = displays.get(viewer.getUniqueId());
        if (display != null && display.viewer == viewer) {
            return display;
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return null;
        }
        display = new Display(viewer, new PracticeSidebar(manager.getNewScoreboard()));
        displays.put(viewer.getUniqueId(), display);
        return display;
    }

    private static void attach(Display display) {
        if (display.viewer.getScoreboard() != display.sidebar.getScoreboard()) {
            display.viewer.setScoreboard(display.sidebar.getScoreboard());
        }
    }

    public void clear(Player player) {
        if (player == null) {
            return;
        }
        Display removed = displays.remove(player.getUniqueId());
        if (removed == null || !player.isOnline()
                || player.getScoreboard() != removed.sidebar.getScoreboard()) {
            return;
        }
        restoreMainScoreboard(player);
    }

    public void shutdown() {
        updateTask.cancel();
        for (Display display : displays.values()) {
            if (display.viewer.isOnline()
                    && display.viewer.getScoreboard() == display.sidebar.getScoreboard()) {
                restoreMainScoreboard(display.viewer);
            }
        }
        displays.clear();
    }

    private static void restoreMainScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }

    private void updateAll() {
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        Iterator<Display> iterator = displays.values().iterator();
        while (iterator.hasNext()) {
            Display display = iterator.next();
            if (!display.viewer.isOnline()) {
                iterator.remove();
            } else {
                update(display, onlinePlayers);
            }
        }
    }

    private void update(Display display, int onlinePlayers) {
        boolean ranked = display.match != null
                && display.match.getType() == com.poppy.practice.match.MatchType.RANKED;
        String ownRating = !ranked ? null
                : ratingText(display.viewer.getUniqueId(), display.match.getKitId());
        String otherRating = !ranked || display.opponent == null ? null
                : ratingText(display.opponent.getUniqueId(), display.match.getKitId());
        if (display.opponent == null) {
            UUID viewerId = display.viewer.getUniqueId();
            display.sidebar.showLobby(onlinePlayers, ratingText(viewerId, "nodebuff"),
                    ratingText(viewerId, "boxing"), ratingText(viewerId, "combo"));
        } else if (display.match != null && BoxingRules.isBoxing(display.match.getKitId())
                && display.match.getStats(display.viewer.getUniqueId()) != null
                && display.match.getStats(display.opponent.getUniqueId()) != null) {
            display.sidebar.showBoxing(pingService.getPing(display.viewer),
                    pingService.getPing(display.opponent),
                    display.match.getStats(display.viewer.getUniqueId()).getHits(),
                    display.match.getStats(display.opponent.getUniqueId()).getHits(), ownRating, otherRating);
        } else if (display.botMatch != null && display.botMatch.isBoxing()
                && display.botMatch.getStats(display.viewer.getUniqueId()) != null
                && display.botMatch.getStats(display.opponent.getUniqueId()) != null) {
            display.sidebar.showBoxing(pingService.getPing(display.viewer),
                    pingService.getPing(display.opponent),
                    display.botMatch.getStats(display.viewer.getUniqueId()).getHits(),
                    display.botMatch.getStats(display.opponent.getUniqueId()).getHits());
        } else {
            display.sidebar.showMatch(pingService.getPing(display.viewer),
                    pingService.getPing(display.opponent), ownRating, otherRating);
        }
    }

    private String ratingText(UUID player, String kit) {
        if (ratings == null) { return null; }
        return ratings.isQualified(player, kit) ? PracticeSidebar.ratingText(ratings.getRatingMilli(player, kit))
                : PracticeSidebar.placementText(ratings.getPlacementCount(player, kit));
    }

    static String onlineCountText(int onlinePlayers) {
        return Integer.toString(Math.max(0, onlinePlayers));
    }

    static int normalizePing(int ping) {
        return PlayerPingService.normalizePing(ping);
    }

    static ChatColor pingColor(int ping) {
        int normalized = normalizePing(ping);
        if (normalized <= 50) {
            return ChatColor.GREEN;
        }
        if (normalized <= 100) {
            return ChatColor.YELLOW;
        }
        if (normalized <= 150) {
            return ChatColor.GOLD;
        }
        return ChatColor.RED;
    }

    private static final class Display {
        private final Player viewer;
        private final PracticeSidebar sidebar;
        private Player opponent;
        private Match match;
        private BotMatch botMatch;

        private Display(Player viewer, PracticeSidebar sidebar) {
            this.viewer = viewer;
            this.sidebar = sidebar;
        }
    }
}
