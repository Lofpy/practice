package com.poppy.practice.bot;

import com.poppy.practice.kit.BoxingKit;
import com.poppy.practice.kit.ComboKit;
import com.poppy.practice.kit.NoDebuffKit;
import com.poppy.practice.language.PlayerLanguage;
import org.junit.Test;

import java.util.HashSet;
import java.util.Arrays;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BotSettingsMenuTest {
    @Test public void everyBotSettingHasJapaneseAndEnglishLabelsWithoutChangingStoragePaths() {
        for (BotSetting setting : BotSetting.values()) {
            assertEquals(setting.getDisplayName(), setting.getDisplayName(PlayerLanguage.ENGLISH));
            assertEquals(setting.getDescription(), setting.getDescription(PlayerLanguage.ENGLISH));
            assertFalse(setting.getDisplayName().equals(setting.getDisplayName(PlayerLanguage.JAPANESE)));
            assertFalse(setting.getDescription().equals(setting.getDescription(PlayerLanguage.JAPANESE)));
            assertEquals(setting, BotSetting.fromPath(setting.getPath()));
        }
    }
    @Test
    public void allSettingsHaveUniqueSlotsWithoutOverlappingActions() {
        Set<Integer> slots = new HashSet<Integer>();
        for (BotSetting setting : BotSetting.values()) {
            assertTrue(setting.getSlot() >= 0 && setting.getSlot() < 45);
            assertTrue(slots.add(setting.getSlot()));
            assertEquals(setting, BotSettingsMenu.settingAt(setting.getSlot()));
            assertNull(BotSettingsMenu.actionAt(setting.getSlot()));
        }
    }

    @Test
    public void strafeToggleIsFirstInTheMovementRowForAllKits() {
        assertEquals(BotSetting.STRAFE_ENABLED, BotSettingsMenu.settingAt(10));
        assertTrue(BotSettingsMenu.settingAt(10).isBooleanSetting());
        assertEquals(BotSettingCategory.MOVEMENT, BotSettingsMenu.settingAt(10).getCategory());
        assertNull(BotSettingsMenu.actionAt(10));
        assertTrue(BotSettingsMenu.isSettingAvailable(BotSetting.STRAFE_ENABLED, "nodebuff"));
        assertTrue(BotSettingsMenu.isSettingAvailable(BotSetting.STRAFE_ENABLED, "boxing"));
        assertTrue(BotSettingsMenu.isSettingAvailable(BotSetting.STRAFE_ENABLED, "combo"));
    }

    @Test
    public void unrelatedAndBottomInventorySlotsHaveNoAction() {
        assertEquals(BotSettingsMenu.Action.BACK_KITS, BotSettingsMenu.actionAt(45));
        assertEquals(BotSettingsMenu.Action.START, BotSettingsMenu.actionAt(49));
        assertEquals(BotSettingsMenu.Action.RESET_ALL, BotSettingsMenu.actionAt(51));
        assertEquals(BotSettingsMenu.Action.CLOSE, BotSettingsMenu.actionAt(53));
        assertNull(BotSettingsMenu.actionAt(46));
        assertNull(BotSettingsMenu.settingAt(46));
        assertNull(BotSettingsMenu.actionAt(54));
        assertNull(BotSettingsMenu.settingAt(54));
        assertNull(BotSettingsMenu.actionAt(-999));
    }

    @Test
    public void allKitsUseOneStartActionWithNoInlineKitSwitch() {
        int startButtons = 0;
        for (int slot = 0; slot < 54; slot++) {
            if (BotSettingsMenu.actionAt(slot) == BotSettingsMenu.Action.START) {
                startButtons++;
                assertEquals(49, slot);
            }
        }
        assertEquals(1, startButtons);
        assertNull(BotSettingsMenu.settingAt(49));
    }

    @Test
    public void selectedKitExplainsProgressRulesAndPerViewerSelection() {
        String description = Arrays.toString(BotSettingsMenu.selectedKitLore(true));
        assertTrue(description.contains("Step 2 of 2"));
        assertTrue(description.contains("100 hits"));
        assertTrue(description.contains("no health damage"));
        assertTrue(description.contains("Gray settings are not used in Boxing"));
        assertTrue(description.contains("only affects your match"));
        assertTrue(description.contains("Use Back"));
        String noDebuff = Arrays.toString(BotSettingsMenu.selectedKitLore(false));
        assertTrue(noDebuff.contains("Healing settings apply to NoDebuff"));
        assertFalse(noDebuff.contains("100 hits"));
    }

    @Test
    public void boxingDisablesOnlyHealthAndHealingWithoutHidingOtherCategories() {
        int disabledCount = 0;
        for (BotSetting setting : BotSetting.values()) {
            assertTrue(BotSettingsMenu.isSettingAvailable(setting, "nodebuff"));
            boolean unavailable = setting == BotSetting.MAXIMUM_HEALTH
                    || setting.getCategory() == BotSettingCategory.HEALING;
            assertEquals(!unavailable, BotSettingsMenu.isSettingAvailable(setting, "boxing"));
            if (unavailable) disabledCount++;
        }
        assertEquals(10, disabledCount);
        assertFalse(BotSettingsMenu.isSettingAvailable(null, "nodebuff"));
        assertFalse(BotSettingsMenu.isSettingAvailable(null, "boxing"));
    }

    @Test
    public void comboDisablesOnlyPotionHealingAndKeepsItsHealthEditable() {
        int disabledCount = 0;
        for (BotSetting setting : BotSetting.values()) {
            boolean unavailable = setting.getCategory() == BotSettingCategory.HEALING;
            assertEquals(!unavailable, BotSettingsMenu.isSettingAvailable(setting, "combo"));
            assertEquals(!unavailable, BotSettingsMenu.isSettingAvailable(setting, "Combo"));
            if (unavailable) disabledCount++;
        }
        assertEquals(9, disabledCount);
        assertTrue(BotSettingsMenu.isSettingAvailable(BotSetting.MAXIMUM_HEALTH, "combo"));
        assertFalse(BotSettingsMenu.isSettingAvailable(null, "combo"));
    }

    @Test
    public void comboDescriptionExplainsAutomaticAppleHealingAndSeparateCombatRules() {
        String description = Arrays.toString(BotSettingsMenu.selectedKitLore("combo"));
        assertTrue(description.contains("Step 2 of 2"));
        assertTrue(description.contains("dedicated knockback and hit timing"));
        assertTrue(description.contains("automatically uses enchanted golden apples"));
        assertTrue(description.contains("potion-healing settings do not apply to Combo"));
        assertTrue(description.contains("8 seconds"));
        assertTrue(description.contains("only affects your match"));
        assertFalse(description.contains("NoDebuff"));
        assertFalse(description.contains("100 hits"));
        assertEquals(Arrays.toString(BotSettingsMenu.selectedKitLore(true)),
                Arrays.toString(BotSettingsMenu.selectedKitLore("boxing")));
        assertEquals(Arrays.toString(BotSettingsMenu.selectedKitLore(false)),
                Arrays.toString(BotSettingsMenu.selectedKitLore("nodebuff")));
    }

    @Test
    public void categoryHeadingsAndKitBadgeDoNotChangeSettingsOrStartMatches() {
        for (int slot : new int[] { 0, 4, 9, 18, 27, 47 }) {
            assertNull(BotSettingsMenu.settingAt(slot));
            assertNull(BotSettingsMenu.actionAt(slot));
        }
        assertEquals(BotSetting.HEALING_ENABLED, BotSettingsMenu.settingAt(36));
        assertNull(BotSettingsMenu.actionAt(36));
    }

    @Test
    public void titlesIdentifyTheKitWithinLegacyInventoryTitleLimit() {
        assertEquals("Bot Settings | NoDebuff", BotSettingsMenu.titleFor(new NoDebuffKit()));
        assertEquals("Bot Settings | Boxing", BotSettingsMenu.titleFor(new BoxingKit()));
        assertEquals("Bot Settings | Combo", BotSettingsMenu.titleFor(new ComboKit()));
        assertTrue(BotSettingsMenu.titleFor(new NoDebuffKit()).length() <= 32);
        assertTrue(BotSettingsMenu.titleFor(new BoxingKit()).length() <= 32);
        assertTrue(BotSettingsMenu.titleFor(new ComboKit()).length() <= 32);
    }

    @Test(expected = IllegalArgumentException.class)
    public void settingsCannotOpenWithoutSelectingAKit() {
        BotSettingsMenu.titleFor(null);
    }

    @Test
    public void publicMenuDoesNotExposeAnyAdminDebugAction() {
        for (int slot = 0; slot < 54; slot++) {
            BotSettingsMenu.Action action = BotSettingsMenu.actionAt(slot);
            if (action != null) {
                assertFalse(action.name().contains("DEBUG"));
            }
        }
    }
}
