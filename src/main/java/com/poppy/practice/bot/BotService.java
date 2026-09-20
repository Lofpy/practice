package com.poppy.practice.bot;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.combat.ComboCombatService;
import com.poppy.practice.combat.ComboFallController;
import com.poppy.practice.cosmetic.MatchFinishEffects;
import com.poppy.practice.tier.TierTestService;
import com.poppy.practice.arena.Arena;
import com.poppy.practice.arena.ArenaManager;
import com.poppy.practice.kit.Kit;
import com.poppy.practice.kit.KitManager;
import com.poppy.practice.kit.KitLayoutService;
import com.poppy.practice.language.LanguageService;
import com.poppy.practice.language.PlayerLanguage;
import com.poppy.practice.match.MatchEndReason;
import com.poppy.practice.match.BoxingRules;
import com.poppy.practice.match.ComboRules;
import com.poppy.practice.match.MatchState;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.queue.QueueManager;
import com.poppy.practice.service.LobbyService;
import com.poppy.practice.service.CleanupTasks;
import com.poppy.practice.service.MatchScoreboardService;
import com.poppy.practice.service.MatchResultService;
import com.poppy.practice.service.PlayerResetService;
import com.poppy.practice.result.MatchParticipantSnapshot;
import com.poppy.practice.result.MatchResult;
import com.poppy.practice.ui.KitSelectionMenu;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BotService {
    private static final double MAXIMUM_ARENA_DISTANCE = 200.0D;
    private static final double MAXIMUM_ARENA_DISTANCE_SQUARED =
            MAXIMUM_ARENA_DISTANCE * MAXIMUM_ARENA_DISTANCE;

    private final PracticePlugin plugin;
    private final ProfileManager profileManager;
    private final ArenaManager arenaManager;
    private final KitManager kitManager;
    private final KitLayoutService kitLayoutService;
    private final QueueManager queueManager;
    private final PlayerResetService resetService;
    private final LobbyService lobbyService;
    private final MatchScoreboardService scoreboardService;
    private final MatchResultService resultService;
    private final ComboCombatService comboCombatService;
    private ComboFallController comboFallController;
    private TierTestService tierTestService;
    private MatchFinishEffects finishEffects;
    private LanguageService languages;

    public void setLanguageService(LanguageService languages) { this.languages = languages; }

    private PlayerLanguage language(Player player) {
        return languages == null ? PlayerLanguage.JAPANESE : languages.language(player);
    }

    private void send(Player player, String japanese, String english) {
        player.sendMessage(language(player).choose(japanese, english));
    }
    private java.util.function.Consumer<UUID> matchEndObserver = id -> { };

    public void setMatchEndObserver(java.util.function.Consumer<UUID> observer) {
        matchEndObserver = observer == null ? id -> { } : observer;
    }
    private final Map<UUID, BotMatch> matchesByPlayer = new HashMap<UUID, BotMatch>();
    private final Map<UUID, BotMatch> matchesByBot = new HashMap<UUID, BotMatch>();
    private final Map<UUID, BotNpc> botsById = new HashMap<UUID, BotNpc>();
    private final Map<UUID, BotSettings> settingsByPlayer = new HashMap<UUID, BotSettings>();
    private BotSettings settings;
    private boolean shuttingDown;

    public BotService(PracticePlugin plugin, ProfileManager profileManager, ArenaManager arenaManager,
                      KitManager kitManager, QueueManager queueManager, PlayerResetService resetService,
                      LobbyService lobbyService, KitLayoutService kitLayoutService,
                      MatchScoreboardService scoreboardService,
                      MatchResultService resultService) {
        this(plugin, profileManager, arenaManager, kitManager, queueManager, resetService,
                lobbyService, kitLayoutService, scoreboardService, resultService, null);
    }

    public BotService(PracticePlugin plugin, ProfileManager profileManager, ArenaManager arenaManager,
                      KitManager kitManager, QueueManager queueManager, PlayerResetService resetService,
                      LobbyService lobbyService, KitLayoutService kitLayoutService,
                      MatchScoreboardService scoreboardService, MatchResultService resultService,
                      ComboCombatService comboCombatService) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        this.arenaManager = arenaManager;
        this.kitManager = kitManager;
        this.kitLayoutService = kitLayoutService;
        this.queueManager = queueManager;
        this.resetService = resetService;
        this.lobbyService = lobbyService;
        this.scoreboardService = scoreboardService;
        this.resultService = resultService;
        this.comboCombatService = comboCombatService;
        reloadConfiguration();
    }

    public void reloadConfiguration() {
        BotSettings.migrateLegacyDifficulty(plugin);
        BotSettings.ensureManagedDefaults(plugin);
        settings = BotSettings.load(plugin);
    }

    public void setComboFallController(ComboFallController controller) {
        comboFallController = controller;
    }

    public void setTierTestService(TierTestService service) {
        tierTestService = service;
    }

    public void setMatchFinishEffects(MatchFinishEffects effects) {
        finishEffects = effects;
    }

    public boolean startTierTest(Player player, String kitId) {
        Kit kit = selectedKit(player, kitId);
        if (kit == null) {
            return false;
        }
        if (tierTestService == null || !tierTestService.canStart(player, kit.getId())) {
            return false;
        }
        return start(player, kit.getId(), true);
    }

    public void openSettings(Player player) {
        if (!canOpenSetup(player)) {
            return;
        }
        ArrayList<Kit> botKits = new ArrayList<Kit>();
        for (Kit kit : kitManager.all()) {
            if (isSupportedKit(kit.getId())) {
                botKits.add(kit);
            }
        }
        player.openInventory(new KitSelectionMenu(KitSelectionMenu.Purpose.BOT, botKits,
                null, player.getUniqueId(), language(player)).getInventory());
    }

    public void openSettings(Player player, String selectedKitId) {
        if (!canOpenSetup(player)) {
            return;
        }
        Kit kit = selectedKit(player, selectedKitId);
        if (kit != null) {
            BotSettingsMenu.open(player, plugin.getConfig(), kit, language(player));
        }
    }

    private boolean canOpenSetup(Player player) {
        PlayerProfile profile = profileManager.getOrCreate(player.getUniqueId());
        if (shuttingDown || (profile.getState() != PlayerState.LOBBY
                && profile.getState() != PlayerState.QUEUE)
                || matchesByPlayer.containsKey(player.getUniqueId())) {
            send(player, ChatColor.RED + "現在Bot設定を開くことはできません。", ChatColor.RED + "You cannot open bot setup right now.");
            return false;
        }
        return true;
    }

    static boolean isSupportedKit(String kitId) {
        return "nodebuff".equalsIgnoreCase(kitId) || "boxing".equalsIgnoreCase(kitId)
                || ComboRules.isCombo(kitId);
    }

    private Kit selectedKit(Player player, String selectedKitId) {
        Kit kit = isSupportedKit(selectedKitId) ? kitManager.get(selectedKitId) : null;
        if (kit == null) {
            send(player, ChatColor.RED + "NoDebuff・Boxing・Comboから利用可能なKitを選択してください。",
                    ChatColor.RED + "Choose an available NoDebuff, Boxing or Combo kit first.");
        }
        return kit;
    }

    public void refreshSettings(Inventory inventory) {
        BotSettingsMenu.populate(inventory, plugin.getConfig());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Inventory open = viewer.getOpenInventory().getTopInventory();
            if (open != inventory && open.getHolder() instanceof BotSettingsMenu) {
                BotSettingsMenu.populate(open, plugin.getConfig());
            }
        }
    }

    public boolean adjustSetting(BotSetting setting, ClickType click) {
        if (setting == null || click == null) {
            return false;
        }
        if (click == ClickType.MIDDLE) {
            setting.reset(plugin.getConfig());
        } else if (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT) {
            setting.adjust(plugin.getConfig(), true, click == ClickType.SHIFT_LEFT);
        } else if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
            setting.adjust(plugin.getConfig(), false, click == ClickType.SHIFT_RIGHT);
        } else {
            return false;
        }
        BotSetting.normalizeRelationships(plugin.getConfig(), setting);
        plugin.saveConfig();
        reloadConfiguration();
        return true;
    }

    public void resetSettings() {
        BotSetting.resetAll(plugin.getConfig());
        plugin.getConfig().set("bot.difficulty", null);
        plugin.saveConfig();
        reloadConfiguration();
    }

    public boolean start(Player player) {
        return start(player, null);
    }

    public boolean startBoxing(Player player) {
        return start(player, "boxing");
    }

    public boolean startSelected(Player player, String selectedKitId) {
        Kit kit = selectedKit(player, selectedKitId);
        return kit != null && start(player, kit.getId());
    }

    private boolean start(Player player, String selectedKitId) {
        return start(player, selectedKitId, false);
    }

    private boolean start(Player player, String selectedKitId, boolean placement) {
        BotSettings matchSettings = placement ? BotSettings.certificationPreset() : BotSettings.load(plugin);
        if (shuttingDown) {
            send(player, ChatColor.RED + "現在Bot戦は利用できません。", ChatColor.RED + "Bot matches are currently unavailable.");
            return false;
        }
        PlayerProfile profile = profileManager.getOrCreate(player.getUniqueId());
        if (profile.getState() == PlayerState.QUEUE) {
            queueManager.leave(player, false);
        }
        if (profile.getState() != PlayerState.LOBBY || matchesByPlayer.containsKey(player.getUniqueId())) {
            send(player, ChatColor.RED + "現在Bot戦を開始することはできません。", ChatColor.RED + "You cannot start a bot match right now.");
            return false;
        }
        Kit kit = kitManager.get(selectedKitId == null ? matchSettings.getKitId() : selectedKitId);
        if (kit == null) {
            send(player, ChatColor.RED + "指定されたBotのKitは利用できません。", ChatColor.RED + "The configured bot kit is unavailable.");
            return false;
        }
        if (ComboRules.isCombo(kit.getId()) && comboCombatService == null) {
            send(player, ChatColor.RED + "Comboの戦闘設定を利用できません。", ChatColor.RED + "Combo combat settings are unavailable.");
            return false;
        }
        Arena arena = arenaManager.acquireAvailable(kit.getId());
        if (arena == null) {
            send(player, ChatColor.RED + "現在Bot戦に利用できるアリーナがありません。", ChatColor.RED + "No arena is currently available for a bot match.");
            return false;
        }

        BotMatch match = null;
        BotNpc npc = null;
        try {
            profile.setQueuedKitId(null);
            profile.setState(PlayerState.STARTING);
            profile.resetEnderPearlCooldown();
            resetService.reset(player, GameMode.SURVIVAL);
            if (!player.teleport(arena.getFirstSpawn())) {
                throw new IllegalStateException("Arena teleport was cancelled");
            }
            kitLayoutService.applyLayout(player, kit);

            npc = BotNpc.spawn(player, arena.getSecondSpawn(), kit, matchSettings);
            match = new BotMatch(player.getUniqueId(), npc.getUniqueId(), kit.getId(), arena.getId(), placement);
            match.setBotRemainingHealingPotions(matchSettings.getHealingPotionCount());
            register(match, npc, matchSettings);
            if (match.isCombo()) {
                // Both teleports/world insertion must finish before setting native hit counters.
                comboCombatService.apply(player);
                comboCombatService.apply(npc.getPlayer());
                if (comboFallController != null) {
                    final Player botPlayer = npc.getPlayer();
                    npc.setVerticalVelocityController(
                            vertical -> comboFallController.controlledVerticalVelocity(botPlayer, vertical));
                }
            }
            scoreboardService.showBotMatch(player, npc.getPlayer(), match);
            // Retain the player's profile until despawn. Chunk loading/retracking may
            // deliver a player spawn long after the initial ADD_PLAYER packet.
            send(player, ChatColor.GRAY + "Bot戦の対戦相手: " + coloredBotName(),
                    ChatColor.GRAY + "Bot match started against " + coloredBotName() + ChatColor.GRAY + ".");
            if (placement) {
                send(player, ChatColor.RED + "認定戦: " + kit.getId() + " | 固定設定のBotと対戦し、試合終了後に評価します。",
                        ChatColor.RED + "Certification: " + kit.getId() + " | fixed bot settings. Finish the match for your assessment.");
            }
            if (match.isBoxing()) {
                send(player, ChatColor.GRAY + "Boxing: " + BoxingRules.HITS_TO_WIN + "ヒット先取。体力ダメージ・回復ポーション・パールはありません。",
                        ChatColor.GRAY + "Boxing: First to " + BoxingRules.HITS_TO_WIN + " hits wins. No health damage, healing potions or pearls.");
            } else if (match.isCombo()) {
                send(player, ChatColor.GRAY + "Combo: 専用KB・無敵時間を使用。パールのクールダウンは8秒です。",
                        ChatColor.GRAY + "Combo: dedicated knockback and hit delay for both players. Ender pearls: 8s.");
            }
            startCountdown(match);
            return true;
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Failed to start player NPC bot match: " + exception.getMessage());
            exception.printStackTrace();
            if (match != null) {
                endMatch(match, false, MatchEndReason.INTERNAL_ERROR);
            } else {
                CleanupTasks cleanup = new CleanupTasks(plugin.getLogger(), "Bot match startup");
                if (npc != null) {
                    final BotNpc failedNpc = npc;
                    if (comboCombatService != null) {
                        cleanup.run("restore bot combat settings", () -> comboCombatService.restore(failedNpc.getUniqueId()));
                    }
                    cleanup.run("remove bot", () -> failedNpc.remove(player));
                }
                cleanup.run("release arena", () -> arenaManager.release(arena.getId()));
                cleanup.run("return player to lobby", () -> returnPlayerToLobby(player.getUniqueId()));
            }
            return false;
        }
    }

    private void register(BotMatch match, BotNpc npc, BotSettings matchSettings) {
        matchesByPlayer.put(match.getPlayerId(), match);
        matchesByBot.put(match.getBotEntityId(), match);
        botsById.put(match.getBotEntityId(), npc);
        settingsByPlayer.put(match.getPlayerId(), matchSettings);
    }

    private void startCountdown(final BotMatch match) {
        final int configuredSeconds = Math.max(0, plugin.getConfig().getInt("match.countdown-seconds", 5));
        BukkitTask task = new BukkitRunnable() {
            private int remaining = configuredSeconds;

            @Override
            public void run() {
                if (match.getState() != MatchState.STARTING || getByPlayer(match.getPlayerId()) != match) {
                    cancel();
                    return;
                }
                Player player = Bukkit.getPlayer(match.getPlayerId());
                BotNpc npc = botsById.get(match.getBotEntityId());
                if (player == null || !player.isOnline() || npc == null || !npc.isValid()) {
                    endMatch(match, false, MatchEndReason.QUIT);
                    cancel();
                    return;
                }
                if (remaining > 0) {
                    player.sendMessage(ChatColor.YELLOW.toString() + ChatColor.BOLD + remaining);
                    remaining--;
                    return;
                }
                beginFighting(match);
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 20L);
        match.setCountdownTaskId(task.getTaskId());
    }

    private void beginFighting(final BotMatch match) {
        if (match.getState() != MatchState.STARTING || getByPlayer(match.getPlayerId()) != match) {
            return;
        }
        final Player player = Bukkit.getPlayer(match.getPlayerId());
        final BotNpc npc = botsById.get(match.getBotEntityId());
        PlayerProfile profile = profileManager.get(match.getPlayerId());
        if (player == null || !player.isOnline() || npc == null || !npc.isValid() || profile == null) {
            endMatch(match, false, MatchEndReason.INTERNAL_ERROR);
            return;
        }
        profile.setState(PlayerState.FIGHTING);
        match.markFighting();
        player.sendMessage(ChatColor.GREEN.toString() + ChatColor.BOLD + "FIGHT!");
        BotSettings matchSettings = settingsByPlayer.get(match.getPlayerId());
        if (matchSettings == null) {
            matchSettings = settings;
        }
        final BotController controller = new BotController(npc, player, matchSettings,
                match.getStats(match.getBotEntityId()), match.getKitId());
        match.setBotRemainingHealingPotions(controller.getRemainingHealingPotions());

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (match.getState() != MatchState.FIGHTING || getByPlayer(match.getPlayerId()) != match
                        || match.getBoxingWinnerId() != null || !player.isOnline() || !npc.isValid()) {
                    cancel();
                    return;
                }
                Player bot = npc.getPlayer();
                bot.setFireTicks(0);
                if (!bot.getWorld().equals(player.getWorld())
                        || exceedsArenaDistance(bot.getLocation().distanceSquared(player.getLocation()))) {
                    plugin.getLogger().warning("Stopped a bot match because the NPC left its arena.");
                    endMatch(match, false, MatchEndReason.INTERNAL_ERROR);
                    cancel();
                    return;
                }
                try {
                    controller.tick();
                    match.setBotRemainingHealingPotions(controller.getRemainingHealingPotions());
                } catch (RuntimeException exception) {
                    plugin.getLogger().severe("Player NPC bot tick failed: " + exception.getMessage());
                    exception.printStackTrace();
                    endMatch(match, false, MatchEndReason.INTERNAL_ERROR);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        match.setAiTaskId(task.getTaskId());
    }

    static boolean exceedsArenaDistance(double distanceSquared) {
        return distanceSquared > MAXIMUM_ARENA_DISTANCE_SQUARED;
    }

    public BotMatch getByPlayer(UUID playerId) {
        return matchesByPlayer.get(playerId);
    }

    public BotMatch getByBot(UUID botEntityId) {
        return matchesByBot.get(botEntityId);
    }

    public Player getBot(BotMatch match) {
        BotNpc npc = match == null ? null : botsById.get(match.getBotEntityId());
        return npc == null ? null : npc.getPlayer();
    }

    /** Publish the NPC profile before the observer teleports into tracking range. */
    public void addSpectator(BotMatch match, Player viewer) {
        BotNpc npc = match == null ? null : botsById.get(match.getBotEntityId());
        if (npc != null && getByPlayer(match.getPlayerId()) == match) {
            npc.getPlayer().hidePlayer(viewer);
            npc.showPlayerInfo(viewer);
        }
    }

    public void removeSpectator(BotMatch match, Player viewer) {
        BotNpc npc = match == null ? null : botsById.get(match.getBotEntityId());
        if (npc == null || viewer == null) return;
        npc.getPlayer().showPlayer(viewer);
        if (!viewer.isOnline()) return;
        ((org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer) viewer).getHandle().playerConnection.sendPacket(
                new net.minecraft.server.v1_8_R3.PacketPlayOutEntityDestroy(npc.getHandle().getId()));
        npc.removePlayerInfo(viewer);
    }

    public boolean canBotHealOpponent(BotMatch match) {
        if (match == null || !match.allowsHealingPotions()) {
            return false;
        }
        BotSettings matchSettings = settingsByPlayer.get(match.getPlayerId());
        return (matchSettings == null ? settings : matchSettings)
                .canHealingPotionHealOpponent();
    }

    public String getBotDisplayName() {
        return coloredBotName();
    }

    public int size() {
        return matchesByPlayer.size();
    }

    public void handleBotDefeat(UUID botEntityId) {
        BotMatch match = getByBot(botEntityId);
        if (match != null) {
            endMatch(match, true, MatchEndReason.DEATH);
        }
    }

    public void handleBoxingHitLimit(final BotMatch match, final UUID winnerId) {
        if (match == null || getByPlayer(match.getPlayerId()) != match
                || getByBot(match.getBotEntityId()) != match || !match.claimBoxingWinner(winnerId)) {
            return;
        }
        // Complete the hit's native damage/KB stack before removing the NPC or teleporting.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (getByPlayer(match.getPlayerId()) == match && getByBot(match.getBotEntityId()) == match
                    && match.getState() == MatchState.FIGHTING
                    && winnerId.equals(match.getBoxingWinnerId())) {
                endMatch(match, winnerId.equals(match.getPlayerId()), MatchEndReason.HIT_LIMIT);
            }
        });
    }

    public void handleBotMeleeHit(UUID botEntityId) {
        BotNpc npc = botsById.get(botEntityId);
        if (npc != null) {
            npc.recordMeleeHit();
        }
    }

    public void handleBotMeleeHitLanded(UUID botEntityId) {
        BotNpc npc = botsById.get(botEntityId);
        if (npc != null) {
            npc.recordMeleeHitLanded();
        }
    }

    public void stopBotBlocking(UUID botEntityId) {
        BotNpc npc = botsById.get(botEntityId);
        if (npc != null) {
            npc.stopBlocking();
        }
    }

    public void handlePlayerDefeat(UUID playerId) {
        BotMatch match = getByPlayer(playerId);
        if (match != null) {
            endMatch(match, false, MatchEndReason.DEATH);
        }
    }

    public void handleQuit(UUID playerId) {
        BotMatch match = getByPlayer(playerId);
        if (match != null) {
            endMatch(match, false, MatchEndReason.QUIT);
        }
    }

    public boolean forceStop(UUID playerId) {
        BotMatch match = getByPlayer(playerId);
        if (match == null) {
            return false;
        }
        endMatch(match, false, MatchEndReason.FORCE_STOP);
        return true;
    }

    private void endMatch(BotMatch match, boolean playerWon, MatchEndReason reason) {
        if (match == null) {
            return;
        }
        if (match.getState() == MatchState.ENDING) {
            if (reason == MatchEndReason.QUIT || reason == MatchEndReason.FORCE_STOP
                    || reason == MatchEndReason.PLUGIN_DISABLE || reason == MatchEndReason.INTERNAL_ERROR) {
                finishMatch(match, reason);
            }
            return;
        }
        boolean wasFighting = match.getState() == MatchState.FIGHTING;
        if (!match.beginEnding()) {
            return;
        }
        boolean lockedBoxingResult = match.getBoxingWinnerId() != null
                && (reason == MatchEndReason.DEATH || reason == MatchEndReason.HIT_LIMIT);
        final boolean resultPlayerWon = lockedBoxingResult
                ? match.getPlayerId().equals(match.getBoxingWinnerId()) : playerWon;
        final MatchEndReason resultReason = lockedBoxingResult ? MatchEndReason.HIT_LIMIT : reason;
        CleanupTasks cleanup = new CleanupTasks(plugin.getLogger(), "Bot match " + match.getPlayerId());
        cleanup.run("cancel countdown", () -> cancelTask(match.getCountdownTaskId()));
        cleanup.run("cancel bot AI", () -> cancelTask(match.getAiTaskId()));
        match.setCountdownTaskId(null);
        match.setAiTaskId(null);

        Player player = Bukkit.getPlayer(match.getPlayerId());
        BotNpc npc = botsById.get(match.getBotEntityId());
        BotSettings matchSettings = settingsByPlayer.get(match.getPlayerId());
        Player bot = npc == null ? null : npc.getPlayer();
        boolean completed = wasFighting
                && (resultReason == MatchEndReason.DEATH || resultReason == MatchEndReason.HIT_LIMIT);
        PlayerProfile profile = profileManager.get(match.getPlayerId());
        if (profile != null) {
            profile.setState(PlayerState.ENDING);
        }
        cleanup.run("stop bot item use", () -> {
            if (npc != null && npc.getHandle().bS()) npc.getHandle().bV();
        });
        cleanup.run("capture and display result", () -> {
            MatchResult result = completed
                    ? captureResult(match, player, bot, resultPlayerWon, matchSettings,
                    resultReason == MatchEndReason.DEATH) : null;
            if (result != null) {
                if (match.isPlacement() && tierTestService != null) {
                    // Persist assessment before display so chat/UI failures cannot erase a valid finish.
                    cleanup.run("record certification", () -> tierTestService.complete(match, result, player));
                }
                if (player != null && player.isOnline() && resultService != null) {
                    resultService.publish(result, match.getPlayerId());
                    resultService.sendResultChat(player, result);
                }
            } else if (resultReason == MatchEndReason.FORCE_STOP && player != null && player.isOnline()) {
                send(player, ChatColor.RED + "Bot戦を終了しました。", ChatColor.RED + "Bot match stopped.");
            }
        });
        if (match.getState() != MatchState.ENDING || getByPlayer(match.getPlayerId()) != match) {
            return;
        }
        if (completed && player != null && player.isOnline() && !shuttingDown) {
            if (finishEffects != null) {
                Player loser = resultPlayerWon ? bot : player;
                if (loser != null) {
                    cleanup.run("show finish effect", () -> finishEffects.play(
                            resultPlayerWon ? player : null, loser.getLocation()));
                }
            }
            try {
                // Retain registry entries and arena ownership during presentation.
                BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin,
                        () -> finishMatch(match, resultReason), 60L);
                match.setFinishTaskId(task.getTaskId());
                return;
            } catch (RuntimeException failure) {
                plugin.getLogger().warning("Could not schedule bot finish: " + failure.getMessage());
            }
        }
        finishMatch(match, reason);
    }

    private void finishMatch(BotMatch match, MatchEndReason reason) {
        if (match.getState() != MatchState.ENDING || getByPlayer(match.getPlayerId()) != match) {
            return;
        }
        CleanupTasks cleanup = new CleanupTasks(plugin.getLogger(), "Bot match cleanup " + match.getId());
        cleanup.run("return spectators", () -> matchEndObserver.accept(match.getId()));
        cleanup.run("cancel finish task", () -> cancelTask(match.getFinishTaskId()));
        match.setFinishTaskId(null);
        Player player = Bukkit.getPlayer(match.getPlayerId());
        BotNpc npc = botsById.remove(match.getBotEntityId());
        settingsByPlayer.remove(match.getPlayerId());
        matchesByPlayer.remove(match.getPlayerId());
        matchesByBot.remove(match.getBotEntityId());
        if (reason == MatchEndReason.PLUGIN_DISABLE) {
            cleanup.run("clear scoreboard", () -> scoreboardService.clear(player));
            if (comboCombatService != null) {
                cleanup.run("restore player combat settings", () -> comboCombatService.restore(match.getPlayerId()));
            }
        }
        if (comboCombatService != null) {
            cleanup.run("restore bot combat settings", () -> comboCombatService.restore(match.getBotEntityId()));
        }
        if (npc != null) {
            cleanup.run("remove bot", () -> npc.remove(player));
        }
        cleanup.run("release arena", () -> arenaManager.release(match.getArenaId()));
        if (reason != MatchEndReason.PLUGIN_DISABLE) {
            cleanup.run("return player to lobby", () -> {
                try {
                    returnPlayerToLobby(match.getPlayerId());
                } catch (RuntimeException exception) {
                    scoreboardService.clear(player);
                    throw exception;
                }
            });
        }
        match.markFinished();
        if (!shuttingDown && reason != MatchEndReason.INTERNAL_ERROR) {
            cleanup.run("resume queues", queueManager::tryMatchAll);
        }
    }

    private MatchResult captureResult(BotMatch match, Player player, Player bot,
                                      boolean playerWon, BotSettings matchSettings, boolean death) {
        String botName = ChatColor.stripColor(matchSettings == null
                ? coloredBotName() : ChatColor.translateAlternateColorCodes('&', matchSettings.getName()));
        MatchParticipantSnapshot playerSnapshot = MatchParticipantSnapshot.capture(
                match.getPlayerId(), player == null ? "Player" : player.getName(),
                player, match.getStats(match.getPlayerId()), null, death && !playerWon);
        MatchParticipantSnapshot botSnapshot = MatchParticipantSnapshot.capture(
                match.getBotEntityId(), botName, bot, match.getStats(match.getBotEntityId()),
                match.getBotRemainingHealingPotions(), death && playerWon);
        long start = match.getStartedAt() > 0L ? match.getStartedAt() : match.getCreatedAt();
        long seconds = Math.max(0L, (System.currentTimeMillis() - start) / 1000L);
        return new MatchResult(playerWon ? match.getPlayerId() : match.getBotEntityId(),
                playerSnapshot, botSnapshot, seconds);
    }

    private void returnPlayerToLobby(UUID playerId) {
        boolean restoringCombo = comboCombatService != null && comboCombatService.isApplied(playerId);
        if (comboCombatService != null) {
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
                    // A lobby world transfer otherwise reinstates WindSpigot's global hit delay.
                    player.setMaximumNoDamageTicks(restoredNoDamageTicks);
                    player.setNoDamageTicks(0);
                }
            }
        }
    }

    private void cancelTask(Integer taskId) {
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private String coloredBotName() {
        return ChatColor.translateAlternateColorCodes('&', settings.getName());
    }

    public boolean isBotEntity(Entity entity) {
        return entity != null && matchesByBot.containsKey(entity.getUniqueId());
    }

    public void shutdown() {
        shuttingDown = true;
        for (BotMatch match : new ArrayList<BotMatch>(matchesByPlayer.values())) {
            endMatch(match, false, MatchEndReason.PLUGIN_DISABLE);
        }
        matchesByPlayer.clear();
        matchesByBot.clear();
        botsById.clear();
        settingsByPlayer.clear();
    }
}
