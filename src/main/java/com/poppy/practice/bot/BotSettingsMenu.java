package com.poppy.practice.bot;

import com.poppy.practice.util.ItemBuilder;
import com.poppy.practice.ui.MenuHolder;
import com.poppy.practice.kit.Kit;
import com.poppy.practice.match.BoxingRules;
import com.poppy.practice.match.ComboRules;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class BotSettingsMenu extends MenuHolder {
    public static final String TITLE = "Bot Settings";
    public enum Action { BACK_KITS, START, RESET_ALL, CLOSE }
    private final Kit selectedKit;

    public BotSettingsMenu(Kit selectedKit, FileConfiguration config) {
        super(54, titleFor(selectedKit));
        this.selectedKit = selectedKit;
        populate(getInventory(), config);
    }

    public static void open(Player player, FileConfiguration config, Kit selectedKit) {
        player.openInventory(new BotSettingsMenu(selectedKit, config).getInventory());
    }

    public String getKitId() {
        return selectedKit.getId();
    }

    public boolean isBoxing() {
        return BoxingRules.isBoxing(getKitId());
    }

    public BotSetting getEditableSetting(int rawSlot) {
        BotSetting setting = settingAt(rawSlot);
        return isSettingAvailable(setting, getKitId()) ? setting : null;
    }

    public static boolean isSettingAvailable(BotSetting setting, String kitId) {
        if (setting == null) {
            return false;
        }
        if (setting.getCategory() == BotSettingCategory.HEALING) {
            return !BoxingRules.isBoxing(kitId) && !ComboRules.isCombo(kitId);
        }
        return setting != BotSetting.MAXIMUM_HEALTH || !BoxingRules.isBoxing(kitId);
    }

    static String titleFor(Kit kit) {
        if (kit == null) {
            throw new IllegalArgumentException("Select a bot kit before opening settings");
        }
        return TITLE + " | " + kit.getDisplayName();
    }

    public static void populate(Inventory inventory, FileConfiguration config) {
        if (inventory == null || !(inventory.getHolder() instanceof BotSettingsMenu)) {
            return;
        }
        BotSettingsMenu menu = (BotSettingsMenu) inventory.getHolder();
        inventory.clear();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane((short) 15));
        }
        for (BotSettingCategory category : BotSettingCategory.values()) {
            boolean disabled = category == BotSettingCategory.HEALING
                    && !isSettingAvailable(BotSetting.HEALING_ENABLED, menu.getKitId());
            for (int slot = category.getHeaderSlot(); slot < category.getHeaderSlot() + 9; slot++) {
                inventory.setItem(slot, pane(disabled ? (short) 7 : category.getPaneDurability()));
            }
            // Healing has nine controls: its first control is also the row heading.
            if (category != BotSettingCategory.HEALING) {
                inventory.setItem(category.getHeaderSlot(), new ItemBuilder(category.getMaterial())
                        .name(category.getColor() + category.getDisplayName())
                        .lore("&7Adjust the settings in this row.")
                        .build());
            }
        }
        for (BotSetting setting : BotSetting.values()) {
            inventory.setItem(setting.getSlot(), isSettingAvailable(setting, menu.getKitId())
                    ? settingItem(setting, config) : disabledSettingItem(setting, menu.selectedKit));
        }
        inventory.setItem(4, new ItemBuilder(menu.selectedKit.getIcon())
                .durability(menu.selectedKit.getIconDurability())
                .name("&bSelected Kit: &f" + menu.selectedKit.getDisplayName())
                .lore(selectedKitLore(menu.getKitId()))
                .build());
        inventory.setItem(45, new ItemBuilder(Material.ARROW)
                .name("&eBack to Kit Selection")
                .lore("&7Choose a different kit.", "&eLeft Click: &fBack")
                .build());
        inventory.setItem(47, new ItemBuilder(Material.BOOK)
                .name("&bShared Bot Settings")
                .lore("&7These settings apply to all players.",
                        "&7Changes are saved immediately.",
                        "&7They affect newly started bot matches.",
                        "&7Your selected kit is not shared.", "",
                        "&eLeft / Right: &fIncrease / Decrease",
                        "&eShift: &fLarger step", "&eMiddle: &fReset one setting")
                .build());
        inventory.setItem(49, new ItemBuilder(Material.EMERALD_BLOCK)
                .name("&aStart " + menu.selectedKit.getDisplayName() + " Bot Match")
                .lore("&7Use this kit and the current shared settings.",
                        "&7A match starts only when you click here.", "", "&eLeft Click: &fStart Match")
                .build());
        inventory.setItem(51, new ItemBuilder(Material.REDSTONE_BLOCK)
                .name("&cReset All Settings")
                .lore("&7Restore the original default values.",
                        "&7Affects all players and all kits.", "", "&eShift + Left Click: &fReset All")
                .build());
        inventory.setItem(53, new ItemBuilder(Material.BEDROCK)
                .name("&cClose")
                .lore("&7Close without starting a match.", "&eLeft Click: &fClose")
                .build());
    }

    static String[] selectedKitLore(boolean boxing) {
        return new String[] { "&eStep 2 of 2: &fConfigure your bot.", "",
                boxing ? "&7First to 100 hits wins; no health damage."
                        : "&7Potion PvP with armor and ender pearls.",
                boxing ? "&7Gray settings are not used in Boxing."
                        : "&7Healing settings apply to NoDebuff.",
                "&7This kit selection only affects your match.",
                "&7Use Back to choose a different kit." };
    }

    static String[] selectedKitLore(String kitId) {
        if (ComboRules.isCombo(kitId)) {
            return new String[] { "&eStep 2 of 2: &fConfigure your bot.", "",
                    "&7Fast combos with dedicated knockback and hit timing.",
                    "&7The bot automatically uses enchanted golden apples.",
                    "&7Gray potion-healing settings do not apply to Combo.",
                    "&7Ender pearl cooldown: &f8 seconds&7.",
                    "&7This kit selection only affects your match.",
                    "&7Use Back to choose a different kit." };
        }
        return selectedKitLore(BoxingRules.isBoxing(kitId));
    }

    public static BotSetting settingAt(int rawSlot) {
        for (BotSetting setting : BotSetting.values()) {
            if (setting.getSlot() == rawSlot) {
                return setting;
            }
        }
        return null;
    }

    public static Action actionAt(int rawSlot) {
        switch (rawSlot) {
            case 45: return Action.BACK_KITS;
            case 49: return Action.START;
            case 51: return Action.RESET_ALL;
            case 53: return Action.CLOSE;
            default: return null;
        }
    }

    private static ItemStack settingItem(BotSetting setting, FileConfiguration config) {
        String current = setting.isBooleanSetting()
                ? (setting.booleanValue(config) ? "&aEnabled" : "&cDisabled")
                : "&f" + setting.formattedValue(config);
        if (setting.isBooleanSetting()) {
            return new ItemBuilder(setting.getMaterial())
                    .name(setting.getCategory().getColor() + settingLabel(setting))
                    .lore("&7" + setting.getDescription(), "",
                            "&7Current: " + current,
                            "&eLeft/Right Click: &fToggle",
                            "&eMiddle Click: &fReset")
                    .build();
        }
        return new ItemBuilder(setting.getMaterial())
                .name(setting.getCategory().getColor() + settingLabel(setting))
                .lore("&7" + setting.getDescription(), "",
                        "&7Current: " + current,
                        "&eLeft Click: &f+" + setting.getSmallStep(),
                        "&eRight Click: &f-" + setting.getSmallStep(),
                        "&eShift Click: &fUse " + setting.getLargeStep(),
                        "&eMiddle Click: &fReset")
                .build();
    }

    private static String settingLabel(BotSetting setting) {
        if (setting == BotSetting.HEALING_ENABLED) {
            return "Healing: Enabled";
        }
        String name = setting.getDisplayName();
        int separator = name.indexOf(": ");
        return separator < 0 ? name : name.substring(separator + 2);
    }

    private static ItemStack disabledSettingItem(BotSetting setting, Kit kit) {
        return new ItemBuilder(Material.STAINED_GLASS_PANE)
                .durability((short) 7)
                .name("&8" + settingLabel(setting))
                .lore("&7Not used in " + kit.getDisplayName() + ".",
                        ComboRules.isCombo(kit.getId())
                                ? "&7Combo uses enchanted golden apples automatically."
                                : "&7This kit does not use this setting.",
                        setting.getCategory() == BotSettingCategory.HEALING
                                ? "&7Choose NoDebuff to edit this setting."
                                : "&7Choose NoDebuff or Combo to edit this setting.",
                        "&7Its saved value has not been changed.")
                .build();
    }

    private static ItemStack pane(short color) {
        return new ItemBuilder(Material.STAINED_GLASS_PANE)
                .durability(color).name(" ").build();
    }

}
