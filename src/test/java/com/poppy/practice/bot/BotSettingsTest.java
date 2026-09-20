package com.poppy.practice.bot;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BotSettingsTest {
    @Test
    public void opponentHealingDefaultsTrueButExplicitChoiceIsPreserved() {
        YamlConfiguration config = new YamlConfiguration();
        assertTrue(BotSettings.load(config).canHealingPotionHealOpponent());
        config.set("bot.healing.can-heal-opponent", false);
        BotSettings.ensureManagedDefaults(config);
        assertFalse(BotSettings.load(config).canHealingPotionHealOpponent());
    }

    @Test
    public void absentStrafeFlagKeepsTheExistingDefaultBehavior() {
        YamlConfiguration config = new YamlConfiguration();

        BotSettings settings = BotSettings.load(config);

        assertTrue(settings.isStrafeEnabled());
        assertEquals(0.828D, settings.getStrafeInput(), 0.0001D);
        assertEquals(11, settings.getStrafeSwitchMinimumTicks());
        assertEquals(20, settings.getStrafeSwitchMaximumTicks());
    }

    @Test
    public void explicitFalseLoadsWithoutOverwritingSavedMovementOrCombatSettings() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("bot.movement.strafe-enabled", false);
        config.set("bot.movement.strafe-input", 0.65D);
        config.set("bot.movement.strafe-switch-minimum-ticks", 15);
        config.set("bot.movement.strafe-switch-maximum-ticks", 31);
        config.set("bot.combat.attack-range", 2.8D);

        BotSettings settings = BotSettings.load(config);

        assertFalse(settings.isStrafeEnabled());
        assertEquals(0.65D, settings.getStrafeInput(), 0.0001D);
        assertEquals(15, settings.getStrafeSwitchMinimumTicks());
        assertEquals(31, settings.getStrafeSwitchMaximumTicks());
        assertEquals(2.8D, settings.getAttackRange(), 0.0001D);
        assertTrue(settings.isHealingEnabled());
        assertFalse(config.getBoolean("bot.movement.strafe-enabled"));
    }

    @Test
    public void managedDefaultsBackfillStrafeOnceAndPreserveExplicitFalse() {
        YamlConfiguration config = new YamlConfiguration();
        BotSetting.resetAll(config);
        config.set("bot.movement.strafe-enabled", null);
        config.set("bot.movement.strafe-input", 0.42D);

        assertTrue(BotSettings.ensureManagedDefaults(config));
        assertTrue(config.getBoolean("bot.movement.strafe-enabled"));
        assertFalse(BotSettings.ensureManagedDefaults(config));
        assertEquals(0.42D, config.getDouble("bot.movement.strafe-input"), 0.0001D);

        config.set("bot.movement.strafe-enabled", false);
        config.set("bot.healing.potion-count", null);

        assertTrue(BotSettings.ensureManagedDefaults(config));
        assertFalse(config.getBoolean("bot.movement.strafe-enabled"));
        assertFalse(BotSettings.ensureManagedDefaults(config));
        assertEquals(0.42D, config.getDouble("bot.movement.strafe-input"), 0.0001D);
    }

    @Test
    public void legacyMigrationEnablesStrafeByDefaultAndKeepsHardEquivalentStrength() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("bot.difficulty", "HARD");

        assertTrue(BotSettings.migrateLegacyDifficulty(config));

        BotSettings settings = BotSettings.load(config);
        assertTrue(settings.isStrafeEnabled());
        assertEquals(0.828D, settings.getStrafeInput(), 0.0001D);
        assertFalse(config.contains("bot.difficulty"));
        assertFalse(BotSettings.migrateLegacyDifficulty(config));
    }

    @Test
    public void legacyMigrationPreservesExplicitlyDisabledStrafe() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("bot.difficulty", "HARD");
        config.set("bot.movement.strafe-enabled", false);

        assertTrue(BotSettings.migrateLegacyDifficulty(config));
        assertFalse(BotSettings.load(config).isStrafeEnabled());
        assertEquals(0.828D, BotSettings.load(config).getStrafeInput(), 0.0001D);
    }
}
