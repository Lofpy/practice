package com.poppy.practice.service;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.arena.Arena;
import com.poppy.practice.arena.ArenaManager;
import com.poppy.practice.arena.ArenaState;
import com.poppy.practice.kit.Kit;
import com.poppy.practice.kit.KitManager;
import com.poppy.practice.kit.KitLayoutService;
import com.poppy.practice.language.LanguageService;
import com.poppy.practice.combat.ComboCombatService;
import com.poppy.practice.match.ComboRules;
import com.poppy.practice.match.BoxingRules;
import com.poppy.practice.match.Match;
import com.poppy.practice.match.MatchCompletion;
import com.poppy.practice.match.MatchEndReason;
import com.poppy.practice.match.MatchManager;
import com.poppy.practice.match.MatchState;
import com.poppy.practice.match.MatchType;
import com.poppy.practice.rating.RatingService;
import com.poppy.practice.rating.RatingChange;
import com.poppy.practice.rating.EloCalculator;
import com.poppy.practice.cosmetic.MatchFinishEffects;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.queue.QueueManager;
import com.poppy.practice.result.MatchParticipantSnapshot;
import com.poppy.practice.result.MatchResult;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;

public final class MatchService {
    private final PracticePlugin plugin;
    private final ProfileManager profileManager;
    private final ArenaManager arenaManager;
    private final MatchManager matchManager;
    private final KitManager kitManager;
    private final KitLayoutService kitLayoutService;
    private final QueueManager queueManager;
    private final PlayerResetService resetService;
    private final LobbyService lobbyService;
    private final MatchScoreboardService scoreboardService;
    private final MatchResultService resultService;
    private final MatchCompletion completion;
    private final ComboCombatService comboCombatService;
    private boolean shuttingDown;
    private RatingService ratings;
    private MatchFinishEffects finishEffects;
    private LanguageService languages;
    private java.util.function.Consumer<UUID> matchEndObserver = id -> { };
    private final Map<UUID, BukkitTask> endingTasks = new HashMap<UUID, BukkitTask>();

    public void setRatingService(RatingService ratings) { this.ratings = ratings; }
    public void setLanguageService(LanguageService languages) { this.languages = languages; }
    public void setMatchFinishEffects(MatchFinishEffects effects) { this.finishEffects = effects; }
    public void setMatchEndObserver(java.util.function.Consumer<UUID> observer) {
        matchEndObserver = observer == null ? id -> { } : observer;
    }

    public MatchService(PracticePlugin plugin, ProfileManager profileManager, ArenaManager arenaManager,
                        MatchManager matchManager, KitManager kitManager, QueueManager queueManager,
                        PlayerResetService resetService, LobbyService lobbyService,
                        KitLayoutService kitLayoutService,
                        MatchScoreboardService scoreboardService,
                        MatchResultService resultService) {
        this(plugin, profileManager, arenaManager, matchManager, kitManager, queueManager,
                resetService, lobbyService, kitLayoutService, scoreboardService, resultService, null);
    }

    public MatchService(PracticePlugin plugin, ProfileManager profileManager, ArenaManager arenaManager,
                        MatchManager matchManager, KitManager kitManager, QueueManager queueManager,
                        PlayerResetService resetService, LobbyService lobbyService,
                        KitLayoutService kitLayoutService, MatchScoreboardService scoreboardService,
                        MatchResultService resultService, ComboCombatService comboCombatService) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        this.arenaManager = arenaManager;
        this.matchManager = matchManager;
        this.kitManager = kitManager;
        this.kitLayoutService = kitLayoutService;
        this.queueManager = queueManager;
        this.resetService = resetService;
        this.lobbyService = lobbyService;
        this.scoreboardService = scoreboardService;
        this.resultService = resultService;
        this.comboCombatService = comboCombatService;
        this.completion = new MatchCompletion(matchManager, this::returnToLobby, arenaManager::release,
                (message, exception) -> plugin.getLogger().log(Level.WARNING, message, exception));
    }

    public boolean startMatch(UUID firstId, UUID secondId, String kitId, Arena arena) {
        return startMatch(firstId, secondId, kitId, arena, MatchType.RANKED);
    }

    public boolean startDuel(UUID firstId, UUID secondId, String kitId, Arena arena) {
        return startMatch(firstId, secondId, kitId, arena, MatchType.DUEL);
    }

    private boolean startMatch(UUID firstId, UUID secondId, String kitId, Arena arena, MatchType type) {
        if (arena == null || arenaManager.get(arena.getId()) != arena
                || arena.getState() != ArenaState.IN_USE
                || matchManager.getByArena(arena.getId()) != null) {
            return false;
        }
        Match match = null;
        boolean registered = false;
        boolean started = false;
        try {
            Player first = Bukkit.getPlayer(firstId);
            Player second = Bukkit.getPlayer(secondId);
            PlayerProfile firstProfile = profileManager.get(firstId);
            PlayerProfile secondProfile = profileManager.get(secondId);
            Kit kit = kitManager.get(kitId);
            if (shuttingDown || firstId.equals(secondId) || kit == null
                    || !canStart(first, firstProfile, kitId, type)
                    || !canStart(second, secondProfile, kitId, type)) {
                return false;
            }
            if (type == MatchType.RANKED && ratings != null
                    && !EloCalculator.inMatchRange(ratings.getRatingMilli(firstId, kitId),
                            ratings.getRatingMilli(secondId, kitId))) {
                return false;
            }

            match = new Match(firstId, secondId, kitId, arena.getId(), type);
            registered = matchManager.register(match);
            if (!registered) {
                return false;
            }
            firstProfile.setState(PlayerState.STARTING);
            secondProfile.setState(PlayerState.STARTING);
            firstProfile.setQueuedKitId(null);
            secondProfile.setQueuedKitId(null);
            firstProfile.resetEnderPearlCooldown();
            secondProfile.resetEnderPearlCooldown();

            resetService.reset(first, GameMode.SURVIVAL);
            resetService.reset(second, GameMode.SURVIVAL);
            if (!first.teleport(arena.getFirstSpawn()) || !second.teleport(arena.getSecondSpawn())) {
                throw new IllegalStateException("An arena teleport was rejected");
            }
            if (match.getState() != MatchState.STARTING
                    || matchManager.getByArena(match.getArenaId()) != match) {
                return false;
            }
            kitLayoutService.applyLayout(first, kit);
            kitLayoutService.applyLayout(second, kit);
            if (ComboRules.isCombo(kitId)) {
                if (comboCombatService == null) {
                    throw new IllegalStateException("Combo combat settings are unavailable");
                }
                comboCombatService.apply(first);
                comboCombatService.apply(second);
            }
            scoreboardService.show(first, second, match);
            scoreboardService.show(second, first, match);
            send(first, ChatColor.GRAY + "対戦相手: " + ChatColor.WHITE + second.getName(),
                    ChatColor.GRAY + "Matched against " + ChatColor.WHITE + second.getName());
            send(second, ChatColor.GRAY + "対戦相手: " + ChatColor.WHITE + first.getName(),
                    ChatColor.GRAY + "Matched against " + ChatColor.WHITE + first.getName());
            sendToMatch(match, ChatColor.RED + (type == MatchType.DUEL ? "Duel: " : "ランク戦: ") + kit.getDisplayName(),
                    ChatColor.RED + (type == MatchType.DUEL ? "Duel: " : "Ranked: ") + kit.getDisplayName());
            if (BoxingRules.isBoxing(kitId)) {
                sendToMatch(match, ChatColor.GRAY + "Boxing: " + BoxingRules.HITS_TO_WIN + "ヒット先取。体力と空腹度は減りません。",
                        ChatColor.GRAY + "Boxing: first to " + BoxingRules.HITS_TO_WIN + " hits wins. No health or hunger loss.");
            } else if (ComboRules.isCombo(kitId)) {
                sendToMatch(match, ChatColor.GRAY + "Combo: 専用KB・無敵時間を使用。パールのクールダウンは8秒です。",
                        ChatColor.GRAY + "Combo: independent knockback and hit delay. Ender pearls: 8s cooldown.");
            }
            startCountdown(match);
            started = true;
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start match in arena " + arena.getId(), exception);
            return false;
        } finally {
            if (!started) {
                if (registered) {
                    endMatch(match, null, null, MatchEndReason.INTERNAL_ERROR);
                } else {
                    recoverUnstartedPlayer(firstId);
                    recoverUnstartedPlayer(secondId);
                    arenaManager.release(arena.getId());
                }
            }
        }
    }

    private boolean canStart(Player player, PlayerProfile profile, String kitId, MatchType type) {
        return player != null && player.isOnline() && !player.isDead() && profile != null
                && (type == MatchType.DUEL ? profile.getState() == PlayerState.LOBBY
                    : profile.getState() == PlayerState.QUEUE && kitId.equals(profile.getQueuedKitId())
                    && (ratings == null || ratings.isQualified(player.getUniqueId(), kitId)))
                && matchManager.getByPlayer(player.getUniqueId()) == null;
    }

    private void startCountdown(final Match match) {
        final int configuredSeconds = Math.max(0, plugin.getConfig().getInt("match.countdown-seconds", 5));
        BukkitTask task = new BukkitRunnable() {
            private int remaining = configuredSeconds;

            @Override
            public void run() {
                try {
                    if (match.getState() != MatchState.STARTING || matchManager.getByArena(match.getArenaId()) != match) {
                        cancel();
                        return;
                    }
                    if (remaining > 0) {
                        sendToMatch(match, ChatColor.YELLOW.toString() + ChatColor.BOLD + remaining);
                        remaining--;
                        return;
                    }
                    beginFighting(match);
                    match.setCountdownTaskId(null);
                    cancel();
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.WARNING, "Match countdown failed for " + match.getId(), exception);
                    endMatch(match, null, null, MatchEndReason.INTERNAL_ERROR);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
        match.setCountdownTaskId(task.getTaskId());
    }

    private void beginFighting(Match match) {
        if (match.getState() != MatchState.STARTING || matchManager.getByArena(match.getArenaId()) != match) {
            return;
        }
        Player first = Bukkit.getPlayer(match.getFirstPlayerId());
        Player second = Bukkit.getPlayer(match.getSecondPlayerId());
        if (first == null || !first.isOnline()) {
            endMatch(match, match.getSecondPlayerId(), match.getFirstPlayerId(), MatchEndReason.QUIT);
            return;
        }
        if (second == null || !second.isOnline()) {
            endMatch(match, match.getFirstPlayerId(), match.getSecondPlayerId(), MatchEndReason.QUIT);
            return;
        }
        PlayerProfile firstProfile = profileManager.get(match.getFirstPlayerId());
        PlayerProfile secondProfile = profileManager.get(match.getSecondPlayerId());
        if (firstProfile == null || secondProfile == null
                || firstProfile.getState() != PlayerState.STARTING
                || secondProfile.getState() != PlayerState.STARTING) {
            endMatch(match, null, null, MatchEndReason.INTERNAL_ERROR);
            return;
        }
        if (!match.markFighting()) {
            return;
        }
        firstProfile.setState(PlayerState.FIGHTING);
        secondProfile.setState(PlayerState.FIGHTING);
        sendToMatch(match, ChatColor.GREEN.toString() + ChatColor.BOLD + "FIGHT!");
    }

    public void handleDeath(Player loser) {
        Match match = matchManager.getByPlayer(loser.getUniqueId());
        if (match == null) {
            return;
        }
        UUID winnerId = match.getOpponent(loser.getUniqueId());
        endMatch(match, winnerId, loser.getUniqueId(), MatchEndReason.DEATH);
    }

    public void handleBoxingHitLimit(final Match match, final UUID winnerId) {
        if (match == null || matchManager.getByArena(match.getArenaId()) != match
                || !match.claimBoxingWinner(winnerId)) {
            return;
        }
        // NMS still applies the final hit's hurt/knockback after the event returns.
        // Keep both players in the arena until that has completed, then reset them.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (matchManager.getByArena(match.getArenaId()) == match
                    && match.getState() == MatchState.FIGHTING
                    && winnerId.equals(match.getBoxingWinnerId())) {
                endMatch(match, winnerId, match.getOpponent(winnerId), MatchEndReason.HIT_LIMIT);
            }
        });
    }

    public void handleQuit(UUID loserId) {
        Match match = matchManager.getByPlayer(loserId);
        if (match == null) {
            return;
        }
        endMatch(match, match.getOpponent(loserId), loserId, MatchEndReason.QUIT);
    }

    public boolean forceStop(UUID loserId) {
        Match match = matchManager.getByPlayer(loserId);
        if (match == null) {
            return false;
        }
        endMatch(match, match.getOpponent(loserId), loserId, MatchEndReason.FORCE_STOP);
        return true;
    }

    public void endMatch(Match match, UUID winnerId, UUID loserId, MatchEndReason reason) {
        if (match == null) return;
        if (match.getState() == MatchState.ENDING) {
            if (reason == MatchEndReason.PLUGIN_DISABLE || reason == MatchEndReason.INTERNAL_ERROR
                    || reason == MatchEndReason.FORCE_STOP || reason == MatchEndReason.QUIT) finishEnding(match);
            return;
        }
        final boolean wasFighting = match.getState() == MatchState.FIGHTING;
        boolean lockedBoxingResult = match.getBoxingWinnerId() != null
                && (reason == MatchEndReason.DEATH || reason == MatchEndReason.HIT_LIMIT
                    || reason == MatchEndReason.QUIT);
        final UUID resultWinnerId = lockedBoxingResult ? match.getBoxingWinnerId() : winnerId;
        final UUID resultLoserId = lockedBoxingResult ? match.getOpponent(resultWinnerId) : loserId;
        final MatchEndReason resultReason = lockedBoxingResult ? MatchEndReason.HIT_LIMIT : reason;
        final boolean completed = wasFighting && validResultParticipants(match, resultWinnerId, resultLoserId)
                && (resultReason == MatchEndReason.DEATH || resultReason == MatchEndReason.HIT_LIMIT);
        boolean ended = completion.begin(match, () -> {
            CleanupTasks presentation = new CleanupTasks(plugin.getLogger(), "Match " + match.getId());
            presentation.run("mark first participant ending", () -> markEnding(match.getFirstPlayerId()));
            presentation.run("mark second participant ending", () -> markEnding(match.getSecondPlayerId()));
            presentation.run("cancel countdown", () -> cancelCountdown(match));
            if (wasFighting) {
                presentation.run("update ranked result", () -> updateRating(match, resultWinnerId, resultLoserId, resultReason));
            }
            presentation.run("show result", () -> {
                MatchResult result = completed
                        ? captureResult(match, resultWinnerId, resultReason == MatchEndReason.DEATH) : null;
                if (result != null && resultService != null) {
                    resultService.publish(result, match.getFirstPlayerId(), match.getSecondPlayerId());
                    presentation.run("show first result", () -> resultService.sendResultChat(
                            Bukkit.getPlayer(match.getFirstPlayerId()), result));
                    presentation.run("show second result", () -> resultService.sendResultChat(
                            Bukkit.getPlayer(match.getSecondPlayerId()), result));
                } else if (resultReason != MatchEndReason.PLUGIN_DISABLE
                        && resultReason != MatchEndReason.INTERNAL_ERROR) {
                    showResult(match, resultWinnerId, resultLoserId);
                }
            });
            if (finishEffects != null && completed) {
                presentation.run("show finish effect", () -> {
                    Player loser = Bukkit.getPlayer(resultLoserId);
                    if (loser != null) finishEffects.play(Bukkit.getPlayer(resultWinnerId), loser.getLocation());
                });
            }
        });
        if (!ended) return;
        // A result callback can synchronously quit/stop the match; never queue a stale return.
        if (match.getState() != MatchState.ENDING || matchManager.getByArena(match.getArenaId()) != match) return;
        if (!shuttingDown && completed
                && (reason == MatchEndReason.DEATH || reason == MatchEndReason.HIT_LIMIT)) {
            try {
                BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> finishEnding(match), 60L);
                if (task != null) { endingTasks.put(match.getId(), task); return; }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not schedule match return", exception);
            }
        }
        finishEnding(match);
    }

    private void markEnding(UUID id) {
        PlayerProfile profile = profileManager.get(id);
        if (profile != null) profile.setState(PlayerState.ENDING);
    }

    private void finishEnding(Match match) {
        BukkitTask task = endingTasks.remove(match.getId());
        try {
            // Return observers while participants/NPCs still belong to this match.
            new CleanupTasks(plugin.getLogger(), "Match observers").run(
                    "return spectators", () -> matchEndObserver.accept(match.getId()));
            if (task != null) task.cancel();
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "Could not cancel ending task for " + match.getId(), failure);
        } finally {
            if (completion.finish(match) && !shuttingDown) queueManager.tryMatchAll();
        }
    }

    private void updateRating(Match match, UUID winner, UUID loser, MatchEndReason reason) {
        if (ratings == null || match.getType() != MatchType.RANKED || match.getStartedAt() <= 0
                || !validResultParticipants(match, winner, loser) || !(reason == MatchEndReason.DEATH
                || reason == MatchEndReason.HIT_LIMIT || reason == MatchEndReason.QUIT)) return;
        RatingChange change;
        try {
            change = ratings.recordRankedWin(match.getId(), match.getKitId(), winner, loser);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Rating persistence failed for match " + match.getId(), exception);
            sendToMatch(match, ChatColor.RED + "ELOを保存できませんでした。管理者に連絡してください。",
                    ChatColor.RED + "ELO could not be saved. Please contact an administrator.");
            return;
        }
        if (change == null) return;
        try {
            sendToMatch(match, ChatColor.GOLD + "ELO [" + match.getKitId() + "] " + playerName(winner)
                    + ": " + RatingService.format(change.getWinnerAfterMilli()) + " (+"
                    + RatingService.format(change.getWinnerDeltaMilli()) + ") | " + playerName(loser)
                    + ": " + RatingService.format(change.getLoserAfterMilli()) + " ("
                    + RatingService.format(change.getLoserDeltaMilli()) + ")");
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not display committed ELO for match " + match.getId(), exception);
        }
    }

    private static boolean validResultParticipants(Match match, UUID winner, UUID loser) {
        return winner != null && loser != null && winner.equals(match.getOpponent(loser));
    }

    private void cancelCountdown(Match match) {
        Integer taskId = match.getCountdownTaskId();
        match.setCountdownTaskId(null);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private MatchResult captureResult(Match match, UUID winnerId, boolean death) {
        Player first = Bukkit.getPlayer(match.getFirstPlayerId());
        Player second = Bukkit.getPlayer(match.getSecondPlayerId());
        MatchParticipantSnapshot firstSnapshot = MatchParticipantSnapshot.capture(
                match.getFirstPlayerId(), playerName(match.getFirstPlayerId()),
                first, match.getStats(match.getFirstPlayerId()), null,
                death && winnerId != null && !winnerId.equals(match.getFirstPlayerId()));
        MatchParticipantSnapshot secondSnapshot = MatchParticipantSnapshot.capture(
                match.getSecondPlayerId(), playerName(match.getSecondPlayerId()),
                second, match.getStats(match.getSecondPlayerId()), null,
                death && winnerId != null && !winnerId.equals(match.getSecondPlayerId()));
        long start = match.getStartedAt() > 0L ? match.getStartedAt() : match.getCreatedAt();
        long seconds = Math.max(0L, (System.currentTimeMillis() - start) / 1000L);
        return new MatchResult(winnerId, firstSnapshot, secondSnapshot, seconds);
    }

    private void showResult(Match match, UUID winnerId, UUID loserId) {
        if (winnerId == null || loserId == null) {
            return;
        }
        Player winner = Bukkit.getPlayer(winnerId);
        Player loser = Bukkit.getPlayer(loserId);
        String winnerName = playerName(winnerId);
        String loserName = playerName(loserId);
        long start = match.getStartedAt() > 0L ? match.getStartedAt() : match.getCreatedAt();
        long seconds = Math.max(0L, (System.currentTimeMillis() - start) / 1000L);
        String duration = String.format("%02d:%02d", seconds / 60L, seconds % 60L);
        double winnerHealth = winner == null ? 0.0D : winner.getHealth() / 2.0D;
        String[] lines = new String[] {
                ChatColor.GREEN + "Winner: " + winnerName,
                ChatColor.RED + "Loser: " + loserName,
                ChatColor.GRAY + "Duration: " + duration,
                ChatColor.GRAY + "Winner health: " + String.format("%.1f", winnerHealth) + " hearts"
        };
        sendLines(Bukkit.getPlayer(match.getFirstPlayerId()), lines);
        sendLines(Bukkit.getPlayer(match.getSecondPlayerId()), lines);
    }

    private String playerName(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return player == null ? playerId.toString() : player.getName();
    }

    private void sendLines(Player player, String[] lines) {
        if (player == null || !player.isOnline()) {
            return;
        }
        for (String line : lines) {
            player.sendMessage(line);
        }
    }

    private void sendToMatch(Match match, String message) {
        Player first = Bukkit.getPlayer(match.getFirstPlayerId());
        Player second = Bukkit.getPlayer(match.getSecondPlayerId());
        if (first != null) {
            first.sendMessage(message);
        }
        if (second != null) {
            second.sendMessage(message);
        }
    }

    private void sendToMatch(Match match, String japanese, String english) {
        send(Bukkit.getPlayer(match.getFirstPlayerId()), japanese, english);
        send(Bukkit.getPlayer(match.getSecondPlayerId()), japanese, english);
    }

    private void send(Player player, String japanese, String english) {
        if (player != null && player.isOnline()) {
            player.sendMessage(languages == null ? japanese : languages.text(player, japanese, english));
        }
    }

    private void returnToLobby(UUID playerId) {
        boolean restoringCombo = comboCombatService != null && comboCombatService.isApplied(playerId);
        if (comboCombatService != null) {
            // Restore tracked entities even if Bukkit no longer lists a quitting player.
            comboCombatService.restore(playerId);
        }
        PlayerProfile profile = profileManager.get(playerId);
        if (profile != null) {
            profile.setQueuedKitId(null);
            profile.setState(PlayerState.LOBBY);
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            int restoredNoDamageTicks = restoringCombo ? player.getMaximumNoDamageTicks() : 0;
            try {
                lobbyService.sendToLobby(player);
            } finally {
                if (restoringCombo) {
                    // WindSpigot reapplies the global hit delay on a world transfer.
                    // Preserve a participant's original custom interval after lobby teleport too.
                    player.setMaximumNoDamageTicks(restoredNoDamageTicks);
                    player.setNoDamageTicks(0);
                }
            }
        }
    }

    private void safelyReturnToLobby(UUID playerId) {
        try {
            returnToLobby(playerId);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Could not fully reset player " + playerId + ": " + exception.getMessage());
        }
    }

    private void recoverUnstartedPlayer(UUID playerId) {
        PlayerProfile profile = profileManager.get(playerId);
        if (profile != null && matchManager.getByPlayer(playerId) == null
                && (profile.getState() == PlayerState.QUEUE || profile.getState() == PlayerState.LOBBY)) {
            safelyReturnToLobby(playerId);
        }
    }

    public void shutdown() {
        shuttingDown = true;
        Collection<Match> matches = matchManager.all();
        for (Match match : matches) {
            endMatch(match, null, null, MatchEndReason.PLUGIN_DISABLE);
        }
        matchManager.clear();
    }
}
