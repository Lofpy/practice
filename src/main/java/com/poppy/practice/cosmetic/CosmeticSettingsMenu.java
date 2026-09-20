package com.poppy.practice.cosmetic;

import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import com.poppy.practice.language.PlayerLanguage;
import com.poppy.practice.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;
import java.util.function.Consumer;

/** Viewer-owned settings, separate from the shared combat bot settings. */
public final class CosmeticSettingsMenu implements Listener {
    public static final int LANGUAGE_SLOT = 20;
    private final PreferencesService preferences;
    private final ProfileManager profiles;
    private final MenuRenderer renderer;
    private Consumer<Player> onLanguageChanged = player -> { };

    public CosmeticSettingsMenu(PreferencesService preferences, ProfileManager profiles) {
        this(preferences, profiles, new BukkitRenderer());
    }

    CosmeticSettingsMenu(PreferencesService preferences, ProfileManager profiles, MenuRenderer renderer) {
        this.preferences = preferences;
        this.profiles = profiles;
        this.renderer = renderer;
    }

    public void open(Player player) {
        if (!isIdle(player)) {
            player.sendMessage(ChatColor.RED + language(player).choose(
                    "設定はロビーで待機中のみ変更できます。", "Settings can only be changed while idle in the lobby."));
            return;
        }
        SettingsHolder holder = new SettingsHolder(this, player.getUniqueId());
        holder.inventory = renderer.create(holder);
        renderer.render(holder.inventory, preferences.getKillEffect(player.getUniqueId()));
        player.openInventory(holder.inventory);
    }

    public void setOnLanguageChanged(Consumer<Player> callback) {
        onLanguageChanged = callback == null ? player -> { } : callback;
    }

    private PlayerLanguage language(Player player) { return preferences.getLanguage(player.getUniqueId()); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!isSettingsView(top)) return;
        // This also blocks shift insertion, number swaps, drops, double-click collection and bottom slots.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player) || event.getClick() != ClickType.LEFT) return;
        handleClick((Player) event.getWhoClicked(), top, event.getRawSlot());
    }

    public boolean handleClick(Player player, Inventory inventory, int rawSlot) {
        if (!isSettingsView(inventory) || !isIdle(player)) return false;
        SettingsHolder holder = (SettingsHolder) inventory.getHolder();
        Inventory current = player.getOpenInventory().getTopInventory();
        // CraftBukkit may return a fresh CraftInventory wrapper for the same open container.
        // The server-owned holder identifies the menu session, not the wrapper instance.
        if (!holder.viewer.equals(player.getUniqueId())
                || current == null || current.getHolder() != holder) return false;
        if (rawSlot == LANGUAGE_SLOT) {
            PlayerLanguage selectedLanguage = language(player).next();
            if (!preferences.setLanguage(player.getUniqueId(), selectedLanguage)) {
                saveFailure(player);
                return false;
            }
            onLanguageChanged.accept(player);
            // InventoryClickEvent must not replace its open container synchronously.
            renderer.render(current, preferences.getKillEffect(player.getUniqueId()));
            player.sendMessage(ChatColor.RED + selectedLanguage.choose("言語: ", "Language: ")
                    + selectedLanguage.getDisplayName());
            return true;
        }
        KillEffect selected = KillEffect.atSlot(rawSlot);
        if (selected == null) return false;
        if (!preferences.setKillEffect(player.getUniqueId(), selected)) {
            saveFailure(player);
            return false;
        }
        renderer.render(current, selected);
        player.sendMessage(ChatColor.RED + language(player).choose("キルエフェクト: ", "Kill effect: ")
                + effectName(language(player), selected));
        return true;
    }

    private void saveFailure(Player player) {
        player.sendMessage(ChatColor.RED + language(player).choose(
                "設定を保存できませんでした。管理者に連絡してください。",
                "Your setting could not be saved. Please contact an administrator."));
    }

    private static String effectName(PlayerLanguage language, KillEffect effect) {
        return language.choose(effect == KillEffect.LIGHTNING ? "雷"
                : effect == KillEffect.EXPLOSION ? "爆発" : "レッドストーン", effect.getDisplayName());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (isSettingsView(event.getView().getTopInventory())) event.setCancelled(true);
    }

    private boolean isSettingsView(Inventory inventory) {
        if (inventory == null || !(inventory.getHolder() instanceof SettingsHolder)) return false;
        return ((SettingsHolder) inventory.getHolder()).owner == this;
    }

    private boolean isIdle(Player player) {
        PlayerProfile profile = profiles.get(player.getUniqueId());
        return profile != null && profile.getState() == PlayerState.LOBBY;
    }

    static final class SettingsHolder implements InventoryHolder {
        private final CosmeticSettingsMenu owner;
        private final UUID viewer;
        private Inventory inventory;

        SettingsHolder(CosmeticSettingsMenu owner, UUID viewer) {
            this.owner = owner;
            this.viewer = viewer;
        }

        @Override
        public Inventory getInventory() { return inventory; }
    }

    interface MenuRenderer {
        Inventory create(InventoryHolder holder);
        void render(Inventory inventory, KillEffect selected);
    }

    private static final class BukkitRenderer implements MenuRenderer {
        @Override
        public Inventory create(InventoryHolder holder) {
            return Bukkit.createInventory(holder, 27, "設定 / Settings");
        }

        @Override
        public void render(Inventory inventory, KillEffect selected) {
            SettingsHolder settings = (SettingsHolder) inventory.getHolder();
            PlayerLanguage language = settings.owner.preferences.getLanguage(settings.viewer);
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, new ItemBuilder(Material.STAINED_GLASS_PANE)
                        .durability((short) 7).name(" ").build());
            }
            inventory.setItem(4, new ItemBuilder(Material.NETHER_STAR).name(language.choose("&c勝利時のキルエフェクト", "&cVictory Kill Effect"))
                    .lore(language.choose("&7倒した相手の位置に表示されます。", "&7Shown where your defeated opponent was."),
                            language.choose("&7演出のみで、ダメージやブロック破壊はありません。", "&7Visual only: no damage or broken blocks."),
                            language.choose("&7試合終了から3秒後にロビーへ戻ります。", "&7Matches return to the lobby after 3 seconds.")).build());
            for (KillEffect effect : KillEffect.values()) {
                inventory.setItem(effect.getSlot(), new ItemBuilder(effect.getIcon())
                        .name((effect == selected ? "&c" : "&f") + effectName(language, effect))
                        .lore(effect == selected ? language.choose("&c選択中", "&cSelected") : language.choose("&f左クリックで選択", "&fLeft click to select"),
                                language.choose(effect == KillEffect.REDSTONE ? "&7血のような赤いパーティクル。"
                                        : effect == KillEffect.LIGHTNING ? "&7ダメージのない雷。" : "&7パーティクルと音の爆発。",
                                        effect == KillEffect.REDSTONE ? "&7Red dust with a blood-like appearance."
                                        : effect == KillEffect.LIGHTNING ? "&7A harmless lightning strike."
                                        : "&7An explosion of particles and sound."),
                                language.choose("&7すべてのキットで次の試合から適用されます。", "&7Saved for your next match, across all kits.")).build());
            }
            inventory.setItem(LANGUAGE_SLOT, new ItemBuilder(Material.PAPER)
                    .name("&c言語 / Language: &f" + language.getDisplayName())
                    .lore("&f日本語 &7/ &fEnglish", language.choose("&7左クリックでEnglishに変更", "&7Left click to switch to 日本語"),
                            language.choose("&7スコアボードは常に英語で表示されます。", "&7Scoreboards always remain in English.")).build());
            inventory.setItem(22, new ItemBuilder(Material.BOOK).name(language.choose("&7Escapeキーで閉じる", "&7Press Escape to close"))
                    .lore(language.choose("&7選択内容はすぐに保存されます。", "&7Your selection is saved immediately.")).build());
        }
    }
}
