package com.poppy.practice.bot;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import static org.junit.Assert.*;

public class BotCertificationPresetTest {
    @Test
    public void fixedPresetIsDetachedFromGlobalPracticeBotEdits() {
        YamlConfiguration global = new YamlConfiguration();
        BotSetting.resetAll(global);
        global.set("bot.combat.minimum-cps", 1);
        global.set("bot.combat.maximum-cps", 1);
        global.set("bot.maximum-health", 1);
        global.set("bot.movement.strafe-enabled", false);
        global.set("bot.healing.potion-count", 0);
        BotSettings practice = BotSettings.load(global);
        BotSettings placement = BotSettings.certificationPreset();
        assertFalse(practice.isCertificationMovement());
        assertTrue(placement.isCertificationMovement());
        assertEquals(1, practice.getMinimumCps(), 0);
        assertEquals(16.8, placement.getMinimumCps(), 0);
        assertEquals(19.2, placement.getMaximumCps(), 0);
        assertEquals(20, placement.getMaximumHealth(), 0);
        assertEquals(29, placement.getHealingPotionCount());
        assertTrue(placement.isStrafeEnabled());
        assertEquals(3.0, placement.getAttackRange(), 0);
        assertEquals(0, placement.getHealingRetreatBeforeThrowTicks());
        assertTrue(placement.canHealingPotionHealOpponent());
        global.set("bot.combat.minimum-cps", 20);
        assertEquals(16.8, placement.getMinimumCps(), 0);
        assertNotSame(placement, BotSettings.certificationPreset());
    }

    @Test
    public void certificationChangesSpacingWithoutChangingOrdinaryPracticeDefaults() {
        YamlConfiguration config = new YamlConfiguration();
        BotSetting.resetAll(config);
        BotSettings practice = BotSettings.load(config);
        BotSettings placement = BotSettings.certificationPreset();

        assertEquals(0.828D, practice.getStrafeInput(), 0.0D);
        assertEquals(1.55D, practice.getRetreatDistance(), 0.0D);
        assertEquals(0.18D, placement.getStrafeInput(), 0.0D);
        assertEquals(2.35D, placement.getRetreatDistance(), 0.0D);
        assertEquals(2.65D, placement.getPreferredDistance(), 0.0D);
        assertFalse(practice.isCertificationMovement());
        assertTrue(placement.isCertificationMovement());
        assertEquals(practice.getMaximumHealth(), placement.getMaximumHealth(), 0.0D);
        assertEquals(practice.getMinimumCps(), placement.getMinimumCps(), 0.0D);
        assertEquals(practice.getMaximumCps(), placement.getMaximumCps(), 0.0D);
        assertEquals(practice.getAttackRange(), placement.getAttackRange(), 0.0D);
        assertEquals(practice.getSwingRange(), placement.getSwingRange(), 0.0D);
        assertEquals(practice.getSprintResetTicks(), placement.getSprintResetTicks());
        assertEquals(practice.getHealingPotionCount(), placement.getHealingPotionCount());
        assertEquals(practice.getHealingHealth(), placement.getHealingHealth(), 0.0D);
        assertEquals(practice.getHealingRetreatBeforeThrowTicks(),
                placement.getHealingRetreatBeforeThrowTicks());
        assertEquals(practice.getEnderPearlCooldownTicks(), placement.getEnderPearlCooldownTicks());
    }

    @Test
    public void matchingConfigurationValuesCannotEnableCertificationMovementInPractice() {
        YamlConfiguration config = new YamlConfiguration();
        BotSetting.resetAll(config);
        config.set("bot.movement.strafe-input", 0.18D);
        config.set("bot.movement.retreat-distance", 2.35D);
        config.set("bot.movement.certification-movement", true);

        assertFalse(BotSettings.load(config).isCertificationMovement());
    }
}
