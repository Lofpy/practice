package com.poppy.practice.service;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.util.ItemBuilder;
import com.poppy.practice.util.LocationSerializer;
import com.poppy.practice.ui.LobbyAction;
import com.poppy.practice.language.LanguageService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class LobbyService {
    public static final String KIT_SELECTOR_NAME = "&cKit Selector &7(Right Click)";
    public static final String KIT_EDITOR_NAME = "&cKit Editor &7(Right Click)";
    public static final String BOT_FIGHT_NAME = "&cBot Fight &7(Right Click)";
    public static final String LEAVE_QUEUE_NAME = "&cLeave Queue &7(Right Click)";
    public static final String SETTINGS_NAME = "&cSettings &7(Right Click)";
    public static final String TIER_TEST_NAME = com.poppy.practice.ui.KitSelectionMenu.CERTIFICATION_QUEUE_NAME
            + " &7(Right Click)";

    private final PracticePlugin plugin;
    private final ProfileManager profileManager;
    private final PlayerResetService resetService;
    private final MatchScoreboardService scoreboardService;
    private Location lobbyLocation;
    private LanguageService languages;

    public void setLanguageService(LanguageService languages) { this.languages = languages; }

    private String text(Player player, String japanese, String english) {
        return languages == null ? japanese : languages.text(player, japanese, english);
    }

    public LobbyService(PracticePlugin plugin, ProfileManager profileManager,
                        PlayerResetService resetService, MatchScoreboardService scoreboardService) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        this.resetService = resetService;
        this.scoreboardService = scoreboardService;
        refreshConfiguration();
    }

    public void refreshConfiguration() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("lobby");
        lobbyLocation = LocationSerializer.read(section);
        if (lobbyLocation == null && !Bukkit.getWorlds().isEmpty()) {
            lobbyLocation = Bukkit.getWorlds().get(0).getSpawnLocation();
            plugin.getLogger().warning("Invalid lobby location; using the default world spawn.");
        }
    }

    public void sendToLobby(Player player) {
        PlayerProfile profile = profileManager.getOrCreate(player.getUniqueId());
        profile.setQueuedKitId(null);
        profile.setState(PlayerState.LOBBY);
        resetService.reset(player, GameMode.ADVENTURE);
        if (lobbyLocation != null) {
            player.teleport(lobbyLocation);
        }
        giveLobbyItems(player);
        scoreboardService.showLobby(player);
    }

    public void giveLobbyItems(Player player) {
        updateQueueItem(player);
    }

    public void updateQueueItem(Player player) {
        PlayerProfile profile = profileManager.get(player.getUniqueId());
        if (profile == null || (profile.getState() != PlayerState.LOBBY
                && profile.getState() != PlayerState.QUEUE)) {
            return;
        }
        player.getInventory().clear();
        if (shouldShowLeaveQueueItem(profile == null ? null : profile.getState())) {
            player.getInventory().setItem(LobbyAction.LEAVE_QUEUE.getSlot(), new ItemBuilder(LobbyAction.LEAVE_QUEUE.getMaterial())
                    .name(text(player, "&cキューから退出 &7(右クリック)", LEAVE_QUEUE_NAME))
                    .lore(text(player, "&7参加中のキューから退出します。", "&7Leave your current queue."))
                    .build());
        } else {
            player.getInventory().setItem(LobbyAction.QUEUE.getSlot(), new ItemBuilder(LobbyAction.QUEUE.getMaterial())
                    .name(text(player, "&cキット選択 &7(右クリック)", KIT_SELECTOR_NAME))
                    .lore(text(player, "&7キットを選んで対戦キューに参加します。", "&7Choose a kit and join the match queue."))
                    .build());
            player.getInventory().setItem(LobbyAction.EDIT_KIT.getSlot(), new ItemBuilder(LobbyAction.EDIT_KIT.getMaterial())
                    .name(text(player, "&cキット編集 &7(右クリック)", KIT_EDITOR_NAME))
                    .lore(text(player, "&7キットのアイテム配置を変更します。", "&7Change your kit item positions."))
                    .build());
            player.getInventory().setItem(LobbyAction.BOT_SETTINGS.getSlot(), new ItemBuilder(LobbyAction.BOT_SETTINGS.getMaterial())
                    .name(text(player, "&cBot対戦 &7(右クリック)", BOT_FIGHT_NAME))
                    .lore(text(player, "&7共通のBot設定を調整して対戦します。", "&7Adjust shared bot settings, then start a match."))
                    .build());
            player.getInventory().setItem(LobbyAction.SETTINGS.getSlot(), new ItemBuilder(LobbyAction.SETTINGS.getMaterial())
                    .name(text(player, "&c設定 &7(右クリック)", SETTINGS_NAME))
                    .lore(text(player, "&7言語とキルエフェクトを変更します。", "&7Choose your language and victory kill effect."))
                    .build());
            player.getInventory().setItem(LobbyAction.TIER_TEST.getSlot(), new ItemBuilder(LobbyAction.TIER_TEST.getMaterial())
                    .name(text(player, "&c認定キュー &7(右クリック)", TIER_TEST_NAME))
                    .lore(text(player, "&7NoDebuff・Boxing・Comboの認定を選択します。", "&7Choose NoDebuff, Boxing or Combo certification."),
                            text(player, "&7固定設定のBotを相手に実力を測定します。", "&7Measure your skill against a fixed bot."),
                            text(player, "&7各キット3回の認定でランク対戦を解放します。", "&7Complete 3 tests per kit to unlock its ranked queue."))
                    .build());
        }
        player.updateInventory();
    }

    static boolean shouldShowLeaveQueueItem(PlayerState state) {
        return state == PlayerState.QUEUE;
    }

    public LobbyAction actionFor(Player player, ItemStack item) {
        PlayerProfile profile = profileManager.get(player.getUniqueId());
        return profile == null || item == null ? null : LobbyAction.forSlot(profile.getState(),
                player.getInventory().getHeldItemSlot(), item.getType());
    }
}
