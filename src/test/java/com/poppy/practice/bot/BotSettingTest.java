package com.poppy.practice.bot;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BotSettingTest {
    @Test
    public void categoriesHaveStableVisualMetadataAndOneRowEach() {
        assertCategory(BotSettingCategory.GENERAL, "General", ChatColor.WHITE,
                0, Material.PAPER, 7);
        assertCategory(BotSettingCategory.MOVEMENT, "Movement", ChatColor.GREEN,
                9, Material.LEATHER_BOOTS, 5);
        assertCategory(BotSettingCategory.AIM, "Aim", ChatColor.GOLD,
                18, Material.EYE_OF_ENDER, 1);
        assertCategory(BotSettingCategory.COMBAT, "Combat", ChatColor.RED,
                27, Material.DIAMOND_SWORD, 14);
        assertCategory(BotSettingCategory.HEALING, "Healing", ChatColor.LIGHT_PURPLE,
                36, Material.POTION, 10);
    }

    @Test
    public void settingsUseUniqueSlotsWithinTheirCategoryAndLeaveFooterFree() {
        Set<Integer> slots = new HashSet<Integer>();
        Map<BotSettingCategory, Integer> counts =
                new EnumMap<BotSettingCategory, Integer>(BotSettingCategory.class);
        for (BotSettingCategory category : BotSettingCategory.values()) {
            counts.put(category, 0);
        }

        for (BotSetting setting : BotSetting.values()) {
            BotSettingCategory category = setting.getCategory();
            assertTrue("Duplicate setting slot: " + setting, slots.add(setting.getSlot()));
            assertTrue("Setting overlaps footer: " + setting, setting.getSlot() < 45);
            assertEquals("Setting is outside its category row: " + setting,
                    category.getHeaderSlot() / 9, setting.getSlot() / 9);
            if (setting != BotSetting.HEALING_ENABLED) {
                assertTrue("Setting overlaps category heading: " + setting,
                        setting.getSlot() > category.getHeaderSlot());
            }
            counts.put(category, counts.get(category) + 1);
        }

        assertEquals(Integer.valueOf(2), counts.get(BotSettingCategory.GENERAL));
        assertEquals(Integer.valueOf(6), counts.get(BotSettingCategory.MOVEMENT));
        assertEquals(Integer.valueOf(4), counts.get(BotSettingCategory.AIM));
        assertEquals(Integer.valueOf(5), counts.get(BotSettingCategory.COMBAT));
        assertEquals(Integer.valueOf(9), counts.get(BotSettingCategory.HEALING));
        assertEquals(BotSettingCategory.HEALING.getHeaderSlot(),
                BotSetting.HEALING_ENABLED.getSlot());
    }

    @Test
    public void categoryAndSlotOrderingFollowStableConfigurationGroups() {
        assertSettings(BotSettingCategory.GENERAL, 1,
                BotSetting.COPY_PLAYER_SKIN, BotSetting.MAXIMUM_HEALTH);
        assertSettings(BotSettingCategory.MOVEMENT, 10,
                BotSetting.STRAFE_ENABLED, BotSetting.PREFERRED_DISTANCE,
                BotSetting.RETREAT_DISTANCE, BotSetting.STRAFE_INPUT,
                BotSetting.STRAFE_SWITCH_MINIMUM, BotSetting.STRAFE_SWITCH_MAXIMUM);
        assertSettings(BotSettingCategory.AIM, 19,
                BotSetting.MAXIMUM_YAW_CHANGE, BotSetting.MAXIMUM_PITCH_CHANGE,
                BotSetting.PREDICTION_TICKS, BotSetting.AIM_ERROR_DEGREES);
        assertSettings(BotSettingCategory.COMBAT, 28,
                BotSetting.SWING_LEAD_DISTANCE, BotSetting.ATTACK_RANGE,
                BotSetting.MINIMUM_CPS, BotSetting.MAXIMUM_CPS, BotSetting.SPRINT_RESET_TICKS);
        assertSettings(BotSettingCategory.HEALING, 36,
                BotSetting.HEALING_ENABLED, BotSetting.HEALING_HEALTH,
                BotSetting.HEALING_COOLDOWN, BotSetting.HEALING_POTION_COUNT,
                BotSetting.HEALING_RETREAT_TICKS, BotSetting.DOUBLE_POTION_HEALTH,
                BotSetting.EMERGENCY_UNANSWERED_HITS, BotSetting.EMERGENCY_RETREAT_TICKS,
                BotSetting.HEAL_OPPONENT);
    }

    @Test
    public void hardDefaultsMatchTheFormerHardBot() {
        YamlConfiguration config = new YamlConfiguration();
        BotSetting.resetAll(config);

        assertTrue(BotSetting.STRAFE_ENABLED.booleanValue(config));
        assertEquals(0.828D, BotSetting.STRAFE_INPUT.numberValue(config), 0.0001D);
        assertEquals(43.2D, BotSetting.MAXIMUM_YAW_CHANGE.numberValue(config), 0.0001D);
        assertEquals(11.0D, BotSetting.STRAFE_SWITCH_MINIMUM.numberValue(config), 0.0001D);
        assertEquals(3.0D, BotSetting.ATTACK_RANGE.numberValue(config), 0.0001D);
        assertEquals(1.0D, BotSetting.SWING_LEAD_DISTANCE.numberValue(config), 0.0001D);
        assertEquals(16.8D, BotSetting.MINIMUM_CPS.numberValue(config), 0.0001D);
        assertEquals(19.2D, BotSetting.MAXIMUM_CPS.numberValue(config), 0.0001D);
        assertEquals(18.0D, BotSetting.HEALING_COOLDOWN.numberValue(config), 0.0001D);
        assertEquals(0.0D, BotSetting.HEALING_RETREAT_TICKS.numberValue(config), 0.0001D);
        assertTrue(BotSetting.COPY_PLAYER_SKIN.booleanValue(config));
        assertTrue(BotSetting.HEAL_OPPONENT.booleanValue(config));
    }

    @Test
    public void adjustsNumbersAndTogglesBooleans() {
        YamlConfiguration config = new YamlConfiguration();
        BotSetting.resetAll(config);

        BotSetting.ATTACK_RANGE.adjust(config, true, false);
        assertEquals(3.05D, BotSetting.ATTACK_RANGE.numberValue(config), 0.0001D);
        BotSetting.ATTACK_RANGE.adjust(config, false, true);
        assertEquals(2.8D, BotSetting.ATTACK_RANGE.numberValue(config), 0.0001D);

        BotSetting.HEALING_ENABLED.adjust(config, true, false);
        assertFalse(BotSetting.HEALING_ENABLED.booleanValue(config));
        BotSetting.HEAL_OPPONENT.adjust(config, true, false);
        assertFalse(BotSetting.HEAL_OPPONENT.booleanValue(config));
    }

    @Test
    public void keepsDependentMinimumsAndMaximumsValid() {
        YamlConfiguration config = new YamlConfiguration();
        BotSetting.resetAll(config);
        config.set("bot.combat.minimum-cps", 20.0D);
        BotSetting.normalizeRelationships(config, BotSetting.MINIMUM_CPS);

        assertEquals(20.0D, BotSetting.MAXIMUM_CPS.numberValue(config), 0.0001D);

        config.set("bot.healing.health-threshold", 5.0D);
        BotSetting.normalizeRelationships(config, BotSetting.HEALING_HEALTH);
        assertEquals(5.0D, BotSetting.DOUBLE_POTION_HEALTH.numberValue(config), 0.0001D);
    }

    @Test
    public void strafeTogglePreservesStrengthAndTimingsInBothDirections() {
        YamlConfiguration config = new YamlConfiguration();
        BotSetting.resetAll(config);
        config.set("bot.movement.strafe-input", 0.65D);
        config.set("bot.movement.strafe-switch-minimum-ticks", 15);
        config.set("bot.movement.strafe-switch-maximum-ticks", 31);

        BotSetting.STRAFE_ENABLED.adjust(config, true, false);
        BotSetting.normalizeRelationships(config, BotSetting.STRAFE_ENABLED);

        assertFalse(BotSetting.STRAFE_ENABLED.booleanValue(config));
        assertEquals("Disabled", BotSetting.STRAFE_ENABLED.formattedValue(config));
        assertEquals(0.65D, config.getDouble("bot.movement.strafe-input"), 0.0001D);
        assertEquals(15, config.getInt("bot.movement.strafe-switch-minimum-ticks"));
        assertEquals(31, config.getInt("bot.movement.strafe-switch-maximum-ticks"));

        BotSetting.STRAFE_ENABLED.adjust(config, false, false);

        assertTrue(BotSetting.STRAFE_ENABLED.booleanValue(config));
        assertEquals("Enabled", BotSetting.STRAFE_ENABLED.formattedValue(config));
        assertEquals(0.65D, config.getDouble("bot.movement.strafe-input"), 0.0001D);
        assertEquals(15, config.getInt("bot.movement.strafe-switch-minimum-ticks"));
        assertEquals(31, config.getInt("bot.movement.strafe-switch-maximum-ticks"));
    }

    @Test
    public void strafeResetEnablesMovementWithoutChangingSavedStrength() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("bot.movement.strafe-enabled", false);
        config.set("bot.movement.strafe-input", 0.42D);

        BotSetting.STRAFE_ENABLED.reset(config);

        assertTrue(BotSetting.STRAFE_ENABLED.booleanValue(config));
        assertEquals(0.42D, config.getDouble("bot.movement.strafe-input"), 0.0001D);
        assertEquals(BotSetting.STRAFE_ENABLED, BotSetting.fromPath("movement.strafe-enabled"));
    }

    @Test
    public void doesNotExposeRetiredComboRetreatSettings() {
        assertNull(BotSetting.fromPath("combat.combo-retreat.required-hits"));
        assertNull(BotSetting.fromPath("combat.combo-retreat.duration-ticks"));
        assertNull(BotSetting.fromPath("combat.combo-retreat.forward-input"));
    }

    private static void assertCategory(BotSettingCategory category, String displayName,
                                       ChatColor color, int headerSlot, Material material,
                                       int paneDurability) {
        assertEquals(displayName, category.getDisplayName());
        assertEquals(color, category.getColor());
        assertEquals(headerSlot, category.getHeaderSlot());
        assertEquals(material, category.getMaterial());
        assertEquals(paneDurability, category.getPaneDurability());
    }

    private static void assertSettings(BotSettingCategory category, int firstSlot,
                                       BotSetting... settings) {
        for (int index = 0; index < settings.length; index++) {
            assertEquals(category, settings[index].getCategory());
            assertEquals(firstSlot + index, settings[index].getSlot());
            assertEquals(settings[index], BotSetting.fromPath(settings[index].getPath()));
        }
    }
}
