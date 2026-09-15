package com.poppy.practice.cosmetic;

import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
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

/** Viewer-owned settings, separate from the shared combat bot settings. */
public final class CosmeticSettingsMenu implements Listener {
    private final PreferencesService preferences;
    private final ProfileManager profiles;
    private final MenuRenderer renderer;

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
            player.sendMessage(ChatColor.RED + "Settings can only be changed while idle in the lobby.");
            return;
        }
        SettingsHolder holder = new SettingsHolder(this, player.getUniqueId());
        holder.inventory = renderer.create(holder);
        renderer.render(holder.inventory, preferences.getKillEffect(player.getUniqueId()));
        player.openInventory(holder.inventory);
    }

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
        KillEffect selected = KillEffect.atSlot(rawSlot);
        if (selected == null) return false;
        if (!preferences.setKillEffect(player.getUniqueId(), selected)) {
            player.sendMessage(ChatColor.RED + "Your setting could not be saved. Please contact an administrator.");
            return false;
        }
        renderer.render(current, selected);
        player.sendMessage(ChatColor.GREEN + "Kill effect: " + selected.getDisplayName());
        return true;
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
            return Bukkit.createInventory(holder, 27, "Settings | Kill Effects");
        }

        @Override
        public void render(Inventory inventory, KillEffect selected) {
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, new ItemBuilder(Material.STAINED_GLASS_PANE)
                        .durability((short) 7).name(" ").build());
            }
            inventory.setItem(4, new ItemBuilder(Material.NETHER_STAR).name("&bVictory Kill Effect")
                    .lore("&7Shown where your defeated opponent was.",
                            "&7Visual only: no damage or broken blocks.",
                            "&7Matches return to the lobby after 3 seconds.").build());
            for (KillEffect effect : KillEffect.values()) {
                inventory.setItem(effect.getSlot(), new ItemBuilder(effect.getIcon())
                        .name((effect == selected ? "&a" : "&e") + effect.getDisplayName())
                        .lore(effect == selected ? "&aSelected" : "&eLeft click to select",
                                effect == KillEffect.REDSTONE ? "&7Red dust with a blood-like appearance."
                                        : effect == KillEffect.LIGHTNING ? "&7A harmless lightning strike."
                                        : "&7An explosion of particles and sound.",
                                "&7Saved for your next match, across all kits.").build());
            }
            inventory.setItem(22, new ItemBuilder(Material.BOOK).name("&7Press Escape to close")
                    .lore("&7Your selection is saved immediately.").build());
        }
    }
}
