package com.poppy.practice.listener;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.bot.BotSetting;
import com.poppy.practice.bot.BotSettingsMenu;
import com.poppy.practice.bot.BotService;
import com.poppy.practice.kit.Kit;
import com.poppy.practice.kit.KitManager;
import com.poppy.practice.kit.KitLayoutService;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.queue.QueueManager;
import com.poppy.practice.service.MessageService;
import com.poppy.practice.service.MatchResultService;
import com.poppy.practice.ui.MenuHolder;
import com.poppy.practice.ui.MenuNavigation;
import com.poppy.practice.ui.KitSelectionMenu;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class InventoryListener implements Listener {
    private static final short SPLASH_HEALING_TWO_DATA = (short) 16421;

    private final PracticePlugin plugin;
    private final ProfileManager profileManager;
    private final KitManager kitManager;
    private final QueueManager queueManager;
    private final MessageService messageService;
    private final BotMenuActions botService;
    private final KitLayoutService kitLayoutService;
    private final MatchResultService resultService;
    private final RefilledPotionUseGuard refilledPotionUseGuard = new RefilledPotionUseGuard();
    private java.util.function.Consumer<Player> certificationMenuOpener;
    private com.poppy.practice.language.LanguageService languages;

    public void setLanguageService(com.poppy.practice.language.LanguageService languages) {
        this.languages = languages;
    }

    public void setCertificationMenuOpener(java.util.function.Consumer<Player> opener) {
        this.certificationMenuOpener = opener;
    }

    public InventoryListener(PracticePlugin plugin, ProfileManager profileManager, KitManager kitManager,
                             QueueManager queueManager, MessageService messageService,
                             final BotService botService, KitLayoutService kitLayoutService,
                             MatchResultService resultService) {
        this(plugin, profileManager, kitManager, queueManager, messageService, new BotMenuActions() {
            @Override
            public void openSettings(Player player) { botService.openSettings(player); }
            @Override
            public void openSettings(Player player, String kitId) { botService.openSettings(player, kitId); }
            @Override
            public boolean startSelected(Player player, String kitId) { return botService.startSelected(player, kitId); }
            @Override
            public boolean adjustSetting(BotSetting setting, ClickType click) { return botService.adjustSetting(setting, click); }
            @Override
            public void resetSettings() { botService.resetSettings(); }
            @Override
            public void refreshSettings(Inventory inventory) { botService.refreshSettings(inventory); }
        }, kitLayoutService, resultService);
    }

    interface BotMenuActions {
        void openSettings(Player player);
        void openSettings(Player player, String kitId);
        boolean startSelected(Player player, String kitId);
        boolean adjustSetting(BotSetting setting, ClickType click);
        void resetSettings();
        void refreshSettings(Inventory inventory);
    }

    InventoryListener(PracticePlugin plugin, ProfileManager profileManager, KitManager kitManager,
                      QueueManager queueManager, MessageService messageService,
                      BotMenuActions botService, KitLayoutService kitLayoutService,
                      MatchResultService resultService) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        this.kitManager = kitManager;
        this.queueManager = queueManager;
        this.messageService = messageService;
        this.botService = botService;
        this.kitLayoutService = kitLayoutService;
        this.resultService = resultService;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        final Player player = (Player) event.getWhoClicked();
        PlayerProfile profile = profileManager.get(player.getUniqueId());
        final Inventory top = event.getView().getTopInventory();
        final int rawSlot = event.getRawSlot();
        final ClickType click = event.getClick();
        if (resultService.isResultView(top)) {
            event.setCancelled(true);
            if (MenuNavigation.isTopSlot(top, rawSlot) && click == ClickType.LEFT) {
                MenuNavigation.nextTick(plugin, player, top, new Runnable() {
                    @Override
                    public void run() {
                        resultService.handleClick(player, top, rawSlot);
                    }
                });
            }
            return;
        }
        if (top.getHolder() instanceof BotSettingsMenu) {
            final BotSettingsMenu menu = (BotSettingsMenu) top.getHolder();
            event.setCancelled(true);
            if (!MenuNavigation.isTopSlot(top, rawSlot) || !isLobbyState(profile)) {
                return;
            }
            final BotSettingsMenu.Action action = BotSettingsMenu.actionAt(rawSlot);
            if (action == BotSettingsMenu.Action.START || action == BotSettingsMenu.Action.BACK_KITS
                    || action == BotSettingsMenu.Action.CLOSE) {
                if (click == ClickType.LEFT) {
                    MenuNavigation.nextTick(plugin, player, top, new Runnable() {
                        @Override
                        public void run() {
                            if (!isLobbyState(profileManager.get(player.getUniqueId()))) {
                                return;
                            }
                            if (action == BotSettingsMenu.Action.START) {
                                player.closeInventory();
                                botService.startSelected(player, menu.getKitId());
                            } else if (action == BotSettingsMenu.Action.BACK_KITS) {
                                botService.openSettings(player);
                            } else {
                                player.closeInventory();
                            }
                        }
                    });
                }
                return;
            }
            if (action == BotSettingsMenu.Action.RESET_ALL) {
                if (click != ClickType.SHIFT_LEFT) {
                    return;
                }
                botService.resetSettings();
                botService.refreshSettings(top);
                String japanese = "共有Bot設定をデフォルトに戻しました。";
                player.sendMessage(org.bukkit.ChatColor.RED + (languages == null ? japanese : languages.text(player,
                        japanese, "Shared bot settings reset to their original defaults.")));
                return;
            }
            BotSetting setting = menu.getEditableSetting(rawSlot);
            if (setting != null && botService.adjustSetting(setting, click)) {
                botService.refreshSettings(top);
            }
            return;
        }
        if (top.getHolder() instanceof KitSelectionMenu) {
            event.setCancelled(true);
            if (!MenuNavigation.isTopSlot(top, rawSlot) || click != ClickType.LEFT) {
                return;
            }
            final KitSelectionMenu menu = (KitSelectionMenu) top.getHolder();
            MenuNavigation.nextTick(plugin, player, top, new Runnable() {
                @Override
                public void run() {
                    PlayerProfile currentProfile = profileManager.get(player.getUniqueId());
                    if (!isLobbyState(currentProfile)) {
                        return;
                    }
                    if (rawSlot == menu.getCloseSlot()) {
                        player.closeInventory();
                        return;
                    }
                    if (rawSlot == menu.getCertificationSlot()) {
                        if (currentProfile.getState() == PlayerState.LOBBY && certificationMenuOpener != null) {
                            certificationMenuOpener.accept(player);
                        }
                        return;
                    }
                    Kit kit = kitManager.get(menu.kitIdAt(rawSlot));
                    if (kit == null) {
                        return;
                    }
                    if (menu.getPurpose() == KitSelectionMenu.Purpose.QUEUE) {
                        selectKit(player, currentProfile, kit);
                    } else if (menu.getPurpose() == KitSelectionMenu.Purpose.BOT) {
                        botService.openSettings(player, kit.getId());
                    } else if (currentProfile.getState() == PlayerState.LOBBY) {
                        kitLayoutService.openEditor(player, kit);
                    }
                }
            });
            return;
        }
        if (kitLayoutService.isEditing(player, top)) {
            event.setCancelled(true);
            if (MenuNavigation.isTopSlot(top, rawSlot) && click == ClickType.LEFT) {
                MenuNavigation.nextTick(plugin, player, top, new Runnable() {
                    @Override
                    public void run() {
                        PlayerProfile current = profileManager.get(player.getUniqueId());
                        if (current != null && current.getState() == PlayerState.LOBBY
                                && kitLayoutService.isEditing(player, top)) {
                            kitLayoutService.swapEditorSlot(player, top, rawSlot);
                        }
                    }
                });
            }
            return;
        }
        if (top.getHolder() instanceof MenuHolder || isInventoryMovementProtected(profile)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        PlayerProfile profile = profileManager.get(player.getUniqueId());
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder
                || isInventoryMovementProtected(profile)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        Inventory top = event.getView().getTopInventory();
        if (!kitLayoutService.isEditing(player, top)) {
            return;
        }
        kitLayoutService.closeAndSave(player, top);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        PlayerProfile profile = profileManager.get(event.getPlayer().getUniqueId());
        if (isItemDropProtected(profile)
                || (profile != null && profile.getState() == PlayerState.FIGHTING
                && isSword(event.getItemDrop().getItemStack()))) {
            event.setCancelled(true);
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPickup(PlayerPickupItemEvent event) {
        PlayerProfile profile = profileManager.get(event.getPlayer().getUniqueId());
        if (profile == null || profile.getState() != PlayerState.FIGHTING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        PlayerProfile profile = profileManager.get(event.getPlayer().getUniqueId());
        if (profile != null && profile.getState() == PlayerState.ENDING) {
            event.setCancelled(true);
            return;
        }
        if (profile != null && profile.getState() == PlayerState.STARTING
                && !isPotion(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRefilledPotionInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        PlayerProfile profile = profileManager.get(player.getUniqueId());
        if (profile == null || (profile.getState() != PlayerState.STARTING
                && profile.getState() != PlayerState.FIGHTING)) {
            refilledPotionUseGuard.clear(player.getUniqueId());
            return;
        }
        boolean fighting = profile.getState() == PlayerState.FIGHTING;
        if (refilledPotionUseGuard.shouldCancel(player.getUniqueId(),
                player.getInventory().getHeldItemSlot(), event.getItem(), event.getAction(),
                fighting, System.nanoTime())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        refilledPotionUseGuard.clear(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionConsumed(final PlayerItemConsumeEvent event) {
        PlayerProfile profile = profileManager.get(event.getPlayer().getUniqueId());
        if (!isPotion(event.getItem()) || profile == null
                || (profile.getState() != PlayerState.STARTING
                && profile.getState() != PlayerState.FIGHTING)) {
            return;
        }
        final Player player = event.getPlayer();
        final PlayerProfile consumedProfile = profile;
        final long combatSessionVersion = profile.getCombatSessionVersion();
        final int consumedSlot = player.getInventory().getHeldItemSlot();
        final boolean refillHealingPotion = profile.getState() == PlayerState.STARTING;
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()
                        || profileManager.get(player.getUniqueId()) != consumedProfile
                        || !consumedProfile.isSameCombatSession(combatSessionVersion)) {
                    return;
                }
                ItemStack[] contents = player.getInventory().getContents();
                boolean changed = removeOneGlassBottle(contents, consumedSlot);
                boolean refilled = false;
                if (refillHealingPotion
                        && moveSplashHealingToHotbar(contents, consumedSlot)) {
                    changed = true;
                    refilled = true;
                }
                if (changed) {
                    player.getInventory().setContents(contents);
                    player.updateInventory();
                }
                if (refilled) {
                    refilledPotionUseGuard.guard(player.getUniqueId(), consumedSlot,
                            System.nanoTime());
                }
            }
        });
    }

    static boolean isPotion(ItemStack item) {
        return item != null && item.getType() == Material.POTION;
    }

    static boolean isSword(ItemStack item) {
        return item != null && item.getType().name().endsWith("_SWORD");
    }

    static boolean removeOneGlassBottle(ItemStack[] contents, int preferredSlot) {
        if (contents == null) {
            return false;
        }
        if (preferredSlot >= 0 && preferredSlot < contents.length
                && removeGlassBottleAt(contents, preferredSlot)) {
            return true;
        }
        for (int slot = 0; slot < contents.length; slot++) {
            if (slot != preferredSlot && removeGlassBottleAt(contents, slot)) {
                return true;
            }
        }
        return false;
    }

    static boolean moveSplashHealingToHotbar(ItemStack[] contents, int hotbarSlot) {
        if (contents == null || hotbarSlot < 0 || hotbarSlot > 8
                || hotbarSlot >= contents.length || !isEmpty(contents[hotbarSlot])) {
            return false;
        }
        for (int slot = 9; slot < contents.length; slot++) {
            ItemStack source = contents[slot];
            if (!isSplashHealingTwo(source)) {
                continue;
            }
            ItemStack refill = source.clone();
            refill.setAmount(1);
            contents[hotbarSlot] = refill;
            if (source.getAmount() <= 1) {
                contents[slot] = null;
            } else {
                source.setAmount(source.getAmount() - 1);
            }
            return true;
        }
        return false;
    }

    private static boolean isSplashHealingTwo(ItemStack item) {
        return item != null && item.getType() == Material.POTION
                && item.getDurability() == SPLASH_HEALING_TWO_DATA;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    private static boolean removeGlassBottleAt(ItemStack[] contents, int slot) {
        ItemStack item = contents[slot];
        if (item == null || item.getType() != Material.GLASS_BOTTLE) {
            return false;
        }
        if (item.getAmount() <= 1) {
            contents[slot] = null;
        } else {
            item.setAmount(item.getAmount() - 1);
        }
        return true;
    }

    private void selectKit(Player player, PlayerProfile profile, Kit kit) {
        if (!isLobbyState(profile)) {
            return;
        }
        if (profile.getState() == PlayerState.QUEUE) {
            queueManager.leave(player, false);
        }
        profile.setSelectedKitId(kit.getId());
        player.closeInventory();
        messageService.send(player, "kit-selected", "kit", kit.getDisplayName());
        queueManager.join(player);
    }

    private static boolean isLobbyState(PlayerProfile profile) {
        return profile != null && (profile.getState() == PlayerState.LOBBY
                || profile.getState() == PlayerState.QUEUE);
    }

    private static boolean isInventoryMovementProtected(PlayerProfile profile) {
        return isInventoryMovementProtected(profile == null ? null : profile.getState());
    }

    static boolean isInventoryMovementProtected(PlayerState state) {
        return state == null || state == PlayerState.LOBBY || state == PlayerState.QUEUE
                || state == PlayerState.ENDING;
    }

    private static boolean isItemDropProtected(PlayerProfile profile) {
        return profile == null || profile.getState() != PlayerState.FIGHTING;
    }
}
