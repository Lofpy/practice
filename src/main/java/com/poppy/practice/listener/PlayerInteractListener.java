package com.poppy.practice.listener;

import com.poppy.practice.bot.BotService;
import com.poppy.practice.kit.KitManager;
import com.poppy.practice.kit.KitLayoutService;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.queue.QueueManager;
import com.poppy.practice.service.LobbyService;
import com.poppy.practice.ui.KitSelectionMenu;
import com.poppy.practice.ui.LobbyAction;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public final class PlayerInteractListener implements Listener {
    private static final int SPLASH_POTION_DATA_BIT = 0x4000;

    private final ProfileManager profileManager;
    private final QueueManager queueManager;
    private final KitManager kitManager;
    private final LobbyService lobbyService;
    private final BotService botService;
    private final KitLayoutService kitLayoutService;
    private com.poppy.practice.cosmetic.CosmeticSettingsMenu settingsMenu;
    private com.poppy.practice.tier.TierMenu tierMenu;
    private com.poppy.practice.rating.RatingService ratings;
    private com.poppy.practice.language.LanguageService languages;
    public void setLanguageService(com.poppy.practice.language.LanguageService languages) { this.languages = languages; }

    public void setRatingService(com.poppy.practice.rating.RatingService ratings) { this.ratings = ratings; }

    public void setMenus(com.poppy.practice.cosmetic.CosmeticSettingsMenu settings,
                         com.poppy.practice.tier.TierMenu tiers) {
        settingsMenu = settings;
        tierMenu = tiers;
    }

    public PlayerInteractListener(ProfileManager profileManager, QueueManager queueManager,
                                  KitManager kitManager, LobbyService lobbyService,
                                  BotService botService, KitLayoutService kitLayoutService) {
        this.profileManager = profileManager;
        this.queueManager = queueManager;
        this.kitManager = kitManager;
        this.lobbyService = lobbyService;
        this.botService = botService;
        this.kitLayoutService = kitLayoutService;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        PlayerProfile profile = profileManager.get(player.getUniqueId());
        if (profile == null) {
            return;
        }
        if (profile.getState() == PlayerState.ENDING) {
            event.setCancelled(true);
            return;
        }
        if (profile.getState() == PlayerState.STARTING) {
            if (!isDrinkablePotion(event.getAction(), event.getItem())) {
                event.setCancelled(true);
            }
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (profile.getState() != PlayerState.LOBBY && profile.getState() != PlayerState.QUEUE) {
            return;
        }
        ItemStack item = event.getItem();
        LobbyAction lobbyAction = lobbyService.actionFor(player, item);
        if (lobbyAction == null) {
            return;
        }
        event.setCancelled(true);
        switch (lobbyAction) {
            case SETTINGS:
                if (settingsMenu != null) settingsMenu.open(player);
                break;
            case TIER_TEST:
                if (tierMenu != null) tierMenu.open(player);
                break;
            case QUEUE:
                player.openInventory(new KitSelectionMenu(KitSelectionMenu.Purpose.QUEUE,
                        kitManager.all(), ratings, player.getUniqueId(), languages == null
                        ? com.poppy.practice.language.PlayerLanguage.JAPANESE : languages.language(player)).getInventory());
                break;
            case EDIT_KIT:
                kitLayoutService.openSelector(player);
                break;
            case BOT_SETTINGS:
                botService.openSettings(player);
                break;
            case LEAVE_QUEUE:
                queueManager.leave(player, true);
                break;
            default:
                break;
        }
    }

    @SuppressWarnings("deprecation")
    static boolean isDrinkablePotion(Action action, ItemStack item) {
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        return rightClick && item != null && item.getType() == Material.POTION
                && (item.getDurability() & SPLASH_POTION_DATA_BIT) == 0;
    }

}
