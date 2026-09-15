package com.poppy.practice;

import com.poppy.practice.chatter.ChatterKbCommand;
import com.poppy.practice.chatter.ChatterKbLifecycleListener;
import com.poppy.practice.chatter.ChatterKbService;
import com.poppy.practice.chatter.ProtocolAdapter47;
import com.poppy.practice.chatter.KnockbackObservationApi;
import com.poppy.practice.arena.ArenaManager;
import com.poppy.practice.bot.BotListener;
import com.poppy.practice.bot.BotService;
import com.poppy.practice.bot.BotBoxingCombat;
import com.poppy.practice.bot.HitDebugRoomService;
import com.poppy.practice.command.BotCommand;
import com.poppy.practice.command.ComboKbCommand;
import com.poppy.practice.command.PracticeCommand;
import com.poppy.practice.command.PingCommand;
import com.poppy.practice.command.HitDebugCommand;
import com.poppy.practice.command.QueueCommand;
import com.poppy.practice.command.SpawnCommand;
import com.poppy.practice.command.TierResetCommand;
import com.poppy.practice.command.TierResetAccess;
import com.poppy.practice.config.MessageConfig;
import com.poppy.practice.config.SplashPotionConfig;
import com.poppy.practice.config.ComboConfig;
import com.poppy.practice.config.ComboSettingsService;
import com.poppy.practice.combat.ComboCombatService;
import com.poppy.practice.combat.ComboFallController;
import com.poppy.practice.kit.KitManager;
import com.poppy.practice.kit.KitLayoutService;
import com.poppy.practice.listener.BlockListener;
import com.poppy.practice.listener.CombatListener;
import com.poppy.practice.listener.EnderPearlCooldownListener;
import com.poppy.practice.listener.FoodListener;
import com.poppy.practice.listener.InventoryListener;
import com.poppy.practice.listener.MatchPotionStatisticsListener;
import com.poppy.practice.listener.PlayerConnectionListener;
import com.poppy.practice.listener.PlayerDeathListener;
import com.poppy.practice.listener.PlayerInteractListener;
import com.poppy.practice.listener.PlayerMoveListener;
import com.poppy.practice.listener.SplashPotionListener;
import com.poppy.practice.listener.WorldListener;
import com.poppy.practice.match.MatchManager;
import com.poppy.practice.network.PlayerLatencyService;
import com.poppy.practice.network.PlayerPingService;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.rating.RatingService;
import com.poppy.practice.tier.TierTestService;
import com.poppy.practice.tier.TierMenu;
import com.poppy.practice.duel.DuelService;
import com.poppy.practice.cosmetic.PreferencesService;
import com.poppy.practice.cosmetic.CosmeticSettingsMenu;
import com.poppy.practice.cosmetic.MatchFinishEffects;
import com.poppy.practice.queue.QueueManager;
import com.poppy.practice.reach.ReachGuardCommand;
import com.poppy.practice.reach.ReachGuardListener;
import com.poppy.practice.reach.ReachGuardService;
import com.poppy.practice.reach.packet.NmsReachPacketBridge;
import com.poppy.practice.reach.packet.PacketBridge;
import com.poppy.practice.service.LobbyService;
import com.poppy.practice.service.CleanupTasks;
import com.poppy.practice.service.DamageDebugService;
import com.poppy.practice.service.ArenaWorldLayoutService;
import com.poppy.practice.service.HitDebugRoomLayoutService;
import com.poppy.practice.service.MatchService;
import com.poppy.practice.service.MatchScoreboardService;
import com.poppy.practice.service.MatchResultService;
import com.poppy.practice.service.MessageService;
import com.poppy.practice.service.PlayerResetService;
import org.bukkit.GameMode;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class PracticePlugin extends JavaPlugin {
    private ProfileManager profileManager;
    private ArenaManager arenaManager;
    private MatchManager matchManager;
    private QueueManager queueManager;
    private LobbyService lobbyService;
    private MatchService matchService;
    private PlayerResetService resetService;
    private ComboCombatService comboCombatService;
    private ComboFallController comboFallController;
    private SplashPotionListener splashPotionListener;
    private EnderPearlCooldownListener enderPearlListener;
    private BotService botService;
    private HitDebugRoomService hitDebugRoomService;
    private PlayerLatencyService latencyService;
    private KitLayoutService kitLayoutService;
    private MatchScoreboardService scoreboardService;
    private MatchResultService resultService;
    private PlayerPingService pingService;
    private PingCommand pingCommand;
    private DamageDebugService damageDebugService;
    private ChatterKbService chatterKbService;
    private ProtocolAdapter47 chatterProtocolAdapter;
    private ReachGuardService reachGuardService;
    private PacketBridge reachPacketBridge;
    private RatingService ratingService;
    private TierMenu tierMenu;
    private DuelService duelService;
    private CosmeticSettingsMenu settingsMenu;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        saveResourceIfMissing("arenas.yml");

        profileManager = new ProfileManager();
        ratingService = new RatingService(getDataFolder(), getLogger());
        PreferencesService preferences = new PreferencesService(getDataFolder(), getLogger());
        settingsMenu = new CosmeticSettingsMenu(preferences, profileManager);
        MatchFinishEffects finishEffects = new MatchFinishEffects(preferences);
        KitManager kitManager = new KitManager();
        kitLayoutService = new KitLayoutService(this, kitManager);
        arenaManager = new ArenaManager(this);
        matchManager = new MatchManager();
        ComboConfig comboConfig = ComboConfig.load(getConfig());
        comboCombatService = new ComboCombatService(comboConfig);
        comboFallController = new ComboFallController(comboCombatService, getLogger());
        getServer().getScheduler().runTaskTimer(this, comboFallController, 1L, 1L);
        getLogger().info("Combo PvP initialized with independent knockback and a "
                + comboConfig.getNoDamageTicks() + "-tick hit interval; ender pearl cooldown is 8s.");
        resetService = new PlayerResetService(comboCombatService);
        pingService = new PlayerPingService(this);
        scoreboardService = new MatchScoreboardService(this, pingService);
        scoreboardService.setRatingService(ratingService);
        resultService = new MatchResultService(profileManager);
        pingCommand = new PingCommand(pingService);
        damageDebugService = new DamageDebugService(this);
        chatterKbService = new ChatterKbService(this, damageDebugService);
        chatterProtocolAdapter = new ProtocolAdapter47(this, chatterKbService);
        chatterProtocolAdapter.register();
        getLogger().info("ChatterKB Normalizer initialized in "
                + chatterKbService.getMode() + " mode for Protocols "
                + chatterKbService.getSupportedProtocolsDescription() + '.');
        reachGuardService = new ReachGuardService(this, pingService);
        reachPacketBridge = new NmsReachPacketBridge(this, reachGuardService);
        reachGuardService.setPacketBridge(reachPacketBridge);
        getLogger().info("ReachGuard initialized in "
                + reachGuardService.getConfiguration().getMode()
                + " mode using "
                + reachGuardService.getConfiguration().getPacketLibrary() + '.');
        MessageService messageService = new MessageService(new MessageConfig(this));
        new ArenaWorldLayoutService(this).ensureLayout();
        new HitDebugRoomLayoutService(this).ensureLayout();
        arenaManager.load();
        lobbyService = new LobbyService(this, profileManager, resetService, scoreboardService);
        queueManager = new QueueManager(profileManager, kitManager, arenaManager,
                messageService, lobbyService);
        matchService = new MatchService(this, profileManager, arenaManager, matchManager, kitManager,
                queueManager, resetService, lobbyService, kitLayoutService, scoreboardService,
                resultService, comboCombatService);
        queueManager.setMatchService(matchService);
        queueManager.setRatingService(ratingService);
        matchService.setRatingService(ratingService);
        matchService.setMatchFinishEffects(finishEffects);
        botService = new BotService(this, profileManager, arenaManager, kitManager,
                queueManager, resetService, lobbyService, kitLayoutService, scoreboardService,
                resultService, comboCombatService);
        botService.setComboFallController(comboFallController);
        botService.setTierTestService(new TierTestService(getDataFolder(), ratingService, getLogger()));
        botService.setMatchFinishEffects(finishEffects);
        tierMenu = new TierMenu(this, ratingService, profileManager, kitManager, botService);
        duelService = new DuelService(this, profileManager, kitManager, arenaManager, matchService);
        hitDebugRoomService = new HitDebugRoomService(this, profileManager, queueManager,
                resetService, lobbyService, kitManager, kitLayoutService, damageDebugService,
                scoreboardService);
        latencyService = new PlayerLatencyService(this);
        splashPotionListener = new SplashPotionListener(this);

        registerCommands(messageService);
        registerListeners(kitManager, messageService);

        for (Player player : getServer().getOnlinePlayers()) {
            profileManager.getOrCreate(player.getUniqueId());
            chatterKbService.handleJoin(player.getUniqueId(), player.getEntityId());
            chatterProtocolAdapter.install(player);
            reachGuardService.handleJoin(player);
            reachPacketBridge.install(player);
            lobbyService.sendToLobby(player);
        }
        getLogger().info("PoppyPractice 0.1.0 enabled.");
    }

    @Override
    public void onDisable() {
        CleanupTasks cleanup = new CleanupTasks(getLogger(), "Plugin shutdown");
        if (duelService != null) cleanup.run("clear duel invitations", duelService::shutdown);
        if (comboFallController != null) {
            cleanup.run("stop Combo fall control", comboFallController::shutdown);
        }
        if (reachPacketBridge != null) {
            cleanup.run("detach ReachGuard packets", reachPacketBridge::unregister);
        }
        if (reachGuardService != null) {
            cleanup.run("stop ReachGuard", reachGuardService::shutdown);
        }
        if (chatterProtocolAdapter != null) {
            cleanup.run("detach ChatterKB packets", chatterProtocolAdapter::unregister);
        }
        if (chatterKbService != null) {
            cleanup.run("stop ChatterKB", chatterKbService::shutdown);
        }
        if (latencyService != null) {
            cleanup.run("stop artificial latency", latencyService::shutdown);
        }
        if (enderPearlListener != null) {
            cleanup.run("stop pearl cooldown displays", enderPearlListener::shutdown);
        }
        if (kitLayoutService != null) {
            cleanup.run("save open kit editors", kitLayoutService::shutdown);
        }
        // Close editors while listeners can still return cursor items and save layouts.
        for (Player player : getServer().getOnlinePlayers()) {
            cleanup.run("close inventory for " + player.getName(), player::closeInventory);
        }
        if (botService != null) {
            cleanup.run("end bot matches", botService::shutdown);
        }
        if (hitDebugRoomService != null) {
            cleanup.run("stop hit debug room", hitDebugRoomService::shutdown);
        }
        if (matchService != null) {
            cleanup.run("end player matches", matchService::shutdown);
        }
        if (comboCombatService != null) {
            cleanup.run("restore Combo combat settings", comboCombatService::shutdown);
        }
        if (scoreboardService != null) {
            cleanup.run("clear scoreboards", scoreboardService::shutdown);
        }
        if (pingService != null) {
            cleanup.run("stop ping measurements", pingService::shutdown);
        }
        if (queueManager != null) {
            cleanup.run("clear matchmaking queue", queueManager::clear);
        }
        cleanup.run("cancel scheduled tasks", () -> getServer().getScheduler().cancelTasks(this));
        if (resetService != null) {
            for (Player player : getServer().getOnlinePlayers()) {
                cleanup.run("reset " + player.getName(),
                        () -> resetService.reset(player, GameMode.ADVENTURE));
            }
        }
        if (profileManager != null) {
            cleanup.run("clear profiles", profileManager::clear);
        }
        if (resultService != null) {
            cleanup.run("clear match results", resultService::clear);
        }
        getLogger().info("PoppyPractice disabled.");
    }

    public void reloadPracticeConfiguration() {
        reloadConfig();
        try {
            comboCombatService.reload(ComboConfig.load(getConfig()));
        } catch (IllegalArgumentException exception) {
            getLogger().warning("Kept the previous Combo configuration: " + exception.getMessage());
        }
        splashPotionListener.reloadConfiguration();
        botService.reloadConfiguration();
        hitDebugRoomService.reloadConfiguration();
        damageDebugService.reload();
        if (chatterKbService != null) {
            try {
                chatterKbService.reloadConfiguration();
            } catch (IllegalArgumentException exception) {
                getLogger().warning("Kept the previous ChatterKB configuration: "
                        + exception.getMessage());
            }
        }
        if (reachGuardService != null) {
            try {
                reachGuardService.reloadConfiguration();
            } catch (IllegalArgumentException | IllegalStateException exception) {
                getLogger().warning("Kept the previous ReachGuard configuration: "
                        + exception.getMessage());
            }
        }
        lobbyService.refreshConfiguration();
        arenaManager.load();
        queueManager.tryMatchAll();
    }

    public SplashPotionConfig getSplashPotionConfiguration() {
        return splashPotionListener.getConfiguration();
    }

    public KnockbackObservationApi getChatterKnockbackObservationApi() {
        return chatterKbService == null ? null : chatterKbService.getObservationApi();
    }

    public void updateSplashPotionConfiguration(String setting, double value) {
        getConfig().set("splash-potion." + setting, value);
        saveConfig();
        splashPotionListener.reloadConfiguration();
    }

    public void updateSplashPotionConfiguration(String setting, int value) {
        getConfig().set("splash-potion." + setting, value);
        saveConfig();
        splashPotionListener.reloadConfiguration();
    }

    private void registerCommands(MessageService messageService) {
        PluginCommand practice = getCommand("practice");
        PluginCommand queue = getCommand("queue");
        PluginCommand spawn = getCommand("spawn");
        PluginCommand bot = getCommand("bot");
        PluginCommand ping = getCommand("ping");
        PluginCommand matchResult = getCommand("matchresult");
        PluginCommand chatterKb = getCommand("chatterkb");
        PluginCommand hitDebug = getCommand("hitdebug");
        PluginCommand reachGuard = getCommand("reachguard");
        PluginCommand comboKb = getCommand("combokb");
        PluginCommand tier = getCommand("tier");
        PluginCommand tierReset = getCommand("tierreset");
        PluginCommand elo = getCommand("elo");
        PluginCommand duel = getCommand("duel");
        PluginCommand settings = getCommand("settings");
        if (practice == null || queue == null || spawn == null || bot == null || ping == null
                || matchResult == null || chatterKb == null || hitDebug == null
                || reachGuard == null || comboKb == null || tier == null || elo == null
                || duel == null || settings == null || tierReset == null) {
            throw new IllegalStateException("Commands are missing from plugin.yml");
        }
        PracticeCommand practiceCommand = new PracticeCommand(this, profileManager, queueManager, matchManager,
                arenaManager, matchService, botService, latencyService, damageDebugService);
        practice.setExecutor(practiceCommand);
        practice.setTabCompleter(practiceCommand);
        queue.setExecutor(new QueueCommand(queueManager));
        spawn.setExecutor(new SpawnCommand(profileManager, queueManager, lobbyService,
                messageService, hitDebugRoomService));
        BotCommand botCommand = new BotCommand(botService);
        bot.setExecutor(botCommand);
        bot.setTabCompleter(botCommand);
        ping.setExecutor(pingCommand);
        matchResult.setExecutor(resultService);
        ChatterKbCommand chatterCommand = new ChatterKbCommand(chatterKbService,
                new ChatterKbCommand.ReloadAction() {
                    @Override
                    public void reload() {
                        reloadConfig();
                        chatterKbService.reloadConfiguration();
                    }
                });
        chatterKb.setExecutor(chatterCommand);
        chatterKb.setTabCompleter(chatterCommand);
        HitDebugCommand hitDebugCommand = new HitDebugCommand(hitDebugRoomService);
        hitDebug.setExecutor(hitDebugCommand);
        hitDebug.setTabCompleter(hitDebugCommand);
        ReachGuardCommand reachCommand = new ReachGuardCommand(this, reachGuardService);
        reachGuard.setExecutor(reachCommand);
        reachGuard.setTabCompleter(reachCommand);
        ComboKbCommand comboCommand = new ComboKbCommand(new ComboSettingsService(
                comboCombatService, this::getConfig, new File(getDataFolder(), "config.yml"), getLogger()));
        comboKb.setExecutor(comboCommand);
        comboKb.setTabCompleter(comboCommand);
        tier.setExecutor(tierMenu);
        tier.setTabCompleter(tierMenu);
        TierResetCommand tierResetCommand = new TierResetCommand(ratingService,
                new TierResetAccess(getServer(), profileManager, matchManager, botService, queueManager),
                getLogger());
        tierReset.setExecutor(tierResetCommand);
        tierReset.setTabCompleter(tierResetCommand);
        elo.setExecutor(tierMenu);
        elo.setTabCompleter(tierMenu);
        duel.setExecutor(duelService);
        duel.setTabCompleter(duelService);
        settings.setExecutor((sender, command, label, args) -> {
            if (sender instanceof Player) settingsMenu.open((Player) sender);
            else sender.sendMessage("Players only.");
            return true;
        });
    }

    private void registerListeners(KitManager kitManager, MessageService messageService) {
        PluginManager manager = getServer().getPluginManager();
        manager.registerEvents(comboFallController, this);
        manager.registerEvents(new PlayerConnectionListener(this, profileManager, queueManager,
                matchManager, matchService, lobbyService, botService,
                hitDebugRoomService, latencyService), this);
        manager.registerEvents(new ChatterKbLifecycleListener(this, chatterKbService,
                chatterProtocolAdapter, matchManager, botService), this);
        manager.registerEvents(new ReachGuardListener(this, reachGuardService,
                reachPacketBridge, botService, hitDebugRoomService), this);
        PlayerInteractListener interactListener = new PlayerInteractListener(profileManager, queueManager, kitManager,
                lobbyService, botService, kitLayoutService);
        interactListener.setMenus(settingsMenu, tierMenu);
        interactListener.setRatingService(ratingService);
        manager.registerEvents(interactListener, this);
        manager.registerEvents(settingsMenu, this);
        manager.registerEvents(tierMenu, this);
        manager.registerEvents(duelService, this);
        enderPearlListener = new EnderPearlCooldownListener(this, profileManager, matchManager, botService);
        manager.registerEvents(enderPearlListener, this);
        InventoryListener inventoryListener = new InventoryListener(this, profileManager, kitManager,
                queueManager, messageService, botService, kitLayoutService, resultService);
        inventoryListener.setCertificationMenuOpener(tierMenu::open);
        manager.registerEvents(inventoryListener, this);
        manager.registerEvents(new PlayerMoveListener(profileManager), this);
        manager.registerEvents(pingCommand, this);
        BotBoxingCombat botBoxingCombat = new BotBoxingCombat(botService, profileManager);
        manager.registerEvents(new CombatListener(profileManager, matchManager, matchService,
                botService, hitDebugRoomService, damageDebugService, botBoxingCombat), this);
        manager.registerEvents(new MatchPotionStatisticsListener(matchManager, botService), this);
        manager.registerEvents(new PlayerDeathListener(this, matchManager, matchService,
                profileManager, lobbyService, botService), this);
        manager.registerEvents(new BotListener(botService, damageDebugService, botBoxingCombat), this);
        manager.registerEvents(resultService, this);
        manager.registerEvents(new BlockListener(), this);
        manager.registerEvents(new FoodListener(profileManager, matchManager, botService), this);
        manager.registerEvents(splashPotionListener, this);
        manager.registerEvents(new WorldListener(), this);
    }

    private void saveResourceIfMissing(String name) {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create plugin data folder");
        }
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
        }
    }
}
