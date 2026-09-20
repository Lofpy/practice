package com.poppy.practice.bot;

import com.poppy.practice.util.ItemBuilder;
import com.poppy.practice.ui.MenuHolder;
import com.poppy.practice.kit.Kit;
import com.poppy.practice.match.BoxingRules;
import com.poppy.practice.match.ComboRules;
import com.poppy.practice.language.PlayerLanguage;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class BotSettingsMenu extends MenuHolder {
    public static final String TITLE = "Bot Settings";
    public enum Action { BACK_KITS, START, RESET_ALL, CLOSE }
    private final Kit selectedKit;
    private final PlayerLanguage language;

    public BotSettingsMenu(Kit selectedKit, FileConfiguration config) {
        this(selectedKit, config, PlayerLanguage.JAPANESE);
    }

    public BotSettingsMenu(Kit selectedKit, FileConfiguration config, PlayerLanguage language) {
        super(54, language.choose("Bot設定 | " + selectedKit.getDisplayName(), titleFor(selectedKit)));
        this.selectedKit = selectedKit;
        this.language = language;
        populate(getInventory(), config);
    }

    public static void open(Player player, FileConfiguration config, Kit selectedKit) {
        player.openInventory(new BotSettingsMenu(selectedKit, config).getInventory());
    }

    public static void open(Player player, FileConfiguration config, Kit selectedKit, PlayerLanguage language) {
        player.openInventory(new BotSettingsMenu(selectedKit, config, language).getInventory());
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
        PlayerLanguage language = menu.language;
        inventory.clear();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, pane((short) 15));
        }
        for (BotSettingCategory category : BotSettingCategory.values()) {
            boolean disabled = category == BotSettingCategory.HEALING
                    && !isSettingAvailable(BotSetting.HEALING_ENABLED, menu.getKitId());
            for (int slot = category.getHeaderSlot(); slot < category.getHeaderSlot() + 9; slot++) {
                inventory.setItem(slot, pane(disabled ? (short) 7 : (short) 14));
            }
            // Healing has nine controls: its first control is also the row heading.
            if (category != BotSettingCategory.HEALING) {
                inventory.setItem(category.getHeaderSlot(), new ItemBuilder(category.getMaterial())
                        .name("&c" + language.choose(category == BotSettingCategory.GENERAL ? "基本"
                                : category == BotSettingCategory.MOVEMENT ? "移動"
                                : category == BotSettingCategory.AIM ? "エイム"
                                : category == BotSettingCategory.COMBAT ? "戦闘" : "回復", category.getDisplayName()))
                        .lore(language.choose("&7この列の設定を調整します。", "&7Adjust the settings in this row."))
                        .build());
            }
        }
        for (BotSetting setting : BotSetting.values()) {
            inventory.setItem(setting.getSlot(), isSettingAvailable(setting, menu.getKitId())
                    ? settingItem(setting, config, language) : disabledSettingItem(setting, menu.selectedKit, language));
        }
        inventory.setItem(4, new ItemBuilder(menu.selectedKit.getIcon())
                .durability(menu.selectedKit.getIconDurability())
                .name(language.choose("&c選択キット: &f", "&cSelected Kit: &f") + menu.selectedKit.getDisplayName())
                .lore(language == PlayerLanguage.JAPANESE ? japaneseKitLore(menu.getKitId()) : selectedKitLore(menu.getKitId()))
                .build());
        inventory.setItem(45, new ItemBuilder(Material.ARROW)
                .name(language.choose("&cキット選択へ戻る", "&cBack to Kit Selection"))
                .lore(language.choose("&7別のキットを選びます。", "&7Choose a different kit."), language.choose("&f左クリック: &7戻る", "&fLeft Click: &7Back"))
                .build());
        inventory.setItem(47, new ItemBuilder(Material.BOOK)
                .name(language.choose("&c共通Bot設定", "&cShared Bot Settings"))
                .lore(language == PlayerLanguage.JAPANESE ? new String[] { "&7全プレイヤーに共通の設定です。", "&7変更はすぐ保存されます。",
                        "&7次に開始するBot対戦に適用されます。", "&7選択キットは自分専用です。", "", "&f左 / 右: &7増加 / 減少", "&fShift: &7大きく変更", "&f中クリック: &7設定を初期化" }
                        : new String[] { "&7These settings apply to all players.",
                        "&7Changes are saved immediately.",
                        "&7They affect newly started bot matches.",
                        "&7Your selected kit is not shared.", "",
                        "&eLeft / Right: &fIncrease / Decrease",
                        "&eShift: &fLarger step", "&eMiddle: &fReset one setting" })
                .build());
        inventory.setItem(49, new ItemBuilder(Material.EMERALD_BLOCK)
                .name(language.choose("&c" + menu.selectedKit.getDisplayName() + " Bot対戦を開始", "&cStart " + menu.selectedKit.getDisplayName() + " Bot Match"))
                .lore(language.choose("&7このキットと現在の共通設定を使用します。", "&7Use this kit and the current shared settings."),
                        language.choose("&7ここをクリックすると試合が始まります。", "&7A match starts only when you click here."), "", language.choose("&f左クリック: &7対戦開始", "&fLeft Click: &7Start Match"))
                .build());
        inventory.setItem(51, new ItemBuilder(Material.REDSTONE_BLOCK)
                .name(language.choose("&c全設定を初期化", "&cReset All Settings"))
                .lore(language.choose("&7すべての設定を初期値に戻します。", "&7Restore the original default values."),
                        language.choose("&7全プレイヤー・全キットに影響します。", "&7Affects all players and all kits."), "", language.choose("&fShift + 左クリック: &7全設定を初期化", "&fShift + Left Click: &7Reset All"))
                .build());
        inventory.setItem(53, new ItemBuilder(Material.BEDROCK)
                .name(language.choose("&c閉じる", "&cClose"))
                .lore(language.choose("&7試合を開始せずに閉じます。", "&7Close without starting a match."), language.choose("&f左クリック: &7閉じる", "&fLeft Click: &7Close"))
                .build());
    }

    private static String[] japaneseKitLore(String kitId) {
        return new String[] { "&f手順2/2: &7Botを設定します。", "",
                BoxingRules.isBoxing(kitId) ? "&7先に100ヒットで勝利・体力ダメージなし。"
                        : ComboRules.isCombo(kitId) ? "&7高速コンボ・金リンゴで回復。" : "&7装備とエンダーパールを使うポーションPvP。",
                "&7灰色の設定はこのキットでは使用されません。", "&7キット選択は自分の試合のみに適用。", "&7戻るボタンで別のキットを選択。" };
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

    private static ItemStack settingItem(BotSetting setting, FileConfiguration config, PlayerLanguage language) {
        String current = setting.isBooleanSetting()
                ? (setting.booleanValue(config) ? language.choose("&c有効", "&cEnabled") : language.choose("&7無効", "&7Disabled"))
                : "&f" + setting.formattedValue(config);
        if (setting.isBooleanSetting()) {
            return new ItemBuilder(setting.getMaterial())
                    .name("&c" + settingLabel(setting, language))
                    .lore("&7" + setting.getDescription(language), "",
                            language.choose("&7現在: ", "&7Current: ") + current,
                            language.choose("&f左/右クリック: &7切り替え", "&fLeft/Right Click: &7Toggle"),
                            language.choose("&f中クリック: &7初期化", "&fMiddle Click: &7Reset"))
                    .build();
        }
        return new ItemBuilder(setting.getMaterial())
                .name("&c" + settingLabel(setting, language))
                .lore("&7" + setting.getDescription(language), "",
                        language.choose("&7現在: ", "&7Current: ") + current,
                        language.choose("&f左クリック: &7+", "&fLeft Click: &7+") + setting.getSmallStep(),
                        language.choose("&f右クリック: &7-", "&fRight Click: &7-") + setting.getSmallStep(),
                        language.choose("&fShiftクリック: &7変更幅 ", "&fShift Click: &7Use ") + setting.getLargeStep(),
                        language.choose("&f中クリック: &7初期化", "&fMiddle Click: &7Reset"))
                .build();
    }

    private static String settingLabel(BotSetting setting, PlayerLanguage language) {
        if (setting == BotSetting.HEALING_ENABLED) {
            return setting.getDisplayName(language);
        }
        String name = setting.getDisplayName(language);
        int separator = name.indexOf(": ");
        return separator < 0 ? name : name.substring(separator + 2);
    }

    private static ItemStack disabledSettingItem(BotSetting setting, Kit kit, PlayerLanguage language) {
        return new ItemBuilder(Material.STAINED_GLASS_PANE)
                .durability((short) 7)
                .name("&8" + settingLabel(setting, language))
                .lore(language == PlayerLanguage.JAPANESE ? new String[] { "&7" + kit.getDisplayName() + " では使用しません。",
                        ComboRules.isCombo(kit.getId()) ? "&7Comboではエンチャント金リンゴを使用します。" : "&7このキットでは使用しない設定です。",
                        "&7NoDebuff等の対応キットで変更してください。", "&7保存されている値は変わりません。" } : new String[] { "&7Not used in " + kit.getDisplayName() + ".",
                        ComboRules.isCombo(kit.getId())
                                ? "&7Combo uses enchanted golden apples automatically."
                                : "&7This kit does not use this setting.",
                        setting.getCategory() == BotSettingCategory.HEALING
                                ? "&7Choose NoDebuff to edit this setting."
                                : "&7Choose NoDebuff or Combo to edit this setting.",
                        "&7Its saved value has not been changed." })
                .build();
    }

    private static ItemStack pane(short color) {
        return new ItemBuilder(Material.STAINED_GLASS_PANE)
                .durability(color).name(" ").build();
    }

}
