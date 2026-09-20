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
        assertEquals(10.0, placement.getMinimumCps(), 0);
        assertEquals(13.0, placement.getMaximumCps(), 0);
        assertEquals(20, placement.getMaximumHealth(), 0);
        assertEquals(29, placement.getHealingPotionCount());
        assertTrue(placement.isStrafeEnabled());
        assertEquals(2.8, placement.getAttackRange(), 0);
        assertEquals(0, placement.getHealingRetreatBeforeThrowTicks());
        assertTrue(placement.canHealingPotionHealOpponent());
        global.set("bot.combat.minimum-cps", 20);
        assertEquals(10.0, placement.getMinimumCps(), 0);
        assertNotSame(placement, BotSettings.certificationPreset());
    }

    @Test
    public void certificationIsMoreForgivingWithoutChangingOrdinaryPracticeDefaults() {
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
        assertEquals(16.8D, practice.getMinimumCps(), 0.0D);
        assertEquals(19.2D, practice.getMaximumCps(), 0.0D);
        assertEquals(3.0D, practice.getAttackRange(), 0.0D);
        assertTrue(placement.getMinimumCps() < practice.getMinimumCps());
        assertTrue(placement.getMaximumCps() < practice.getMaximumCps());
        assertTrue(placement.getAttackRange() < practice.getAttackRange());
        assertEquals(placement.getAttackRange() + 1.0D, placement.getSwingRange(), 0.0D);
        assertEquals(32.4D, placement.getMaximumYawChange(), 0.0D);
        assertEquals(24.3D, placement.getMaximumPitchChange(), 0.0D);
        assertEquals(0.5D, placement.getPredictionTicks(), 0.0D);
        assertEquals(0.8D, placement.getAimErrorDegrees(), 0.0D);
        assertTrue(placement.getMaximumYawChange() < practice.getMaximumYawChange());
        assertTrue(placement.getMaximumPitchChange() < practice.getMaximumPitchChange());
        assertTrue(placement.getPredictionTicks() < practice.getPredictionTicks());
        assertTrue(placement.getAimErrorDegrees() > practice.getAimErrorDegrees());
        assertEquals(24, placement.getHealingCooldownTicks());
        assertTrue(placement.getHealingCooldownTicks() > practice.getHealingCooldownTicks());
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
