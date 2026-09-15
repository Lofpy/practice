package com.poppy.practice.bot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BotHealingPlanTest {
    @Test
    public void usesTwoPotionsAtExtremelyLowHealth() {
        assertEquals(2, BotHealingPlan.potionsForHealth(6.0D, 6.0D, 10));
    }

    @Test
    public void usesOnePotionAboveDoublePotionThreshold() {
        assertEquals(1, BotHealingPlan.potionsForHealth(6.1D, 6.0D, 10));
    }

    @Test
    public void neverUsesMorePotionsThanAvailable() {
        assertEquals(1, BotHealingPlan.potionsForHealth(2.0D, 6.0D, 1));
        assertEquals(0, BotHealingPlan.potionsForHealth(2.0D, 6.0D, 0));
    }

    @Test
    public void comboPressureOrExtremelyLowHealthTriggersEmergencyHealing() {
        assertTrue(BotHealingPlan.isEmergency(10.0D, 6.0D, 2, 2));
        assertTrue(BotHealingPlan.isEmergency(6.0D, 6.0D, 0, 2));
        assertFalse(BotHealingPlan.isEmergency(10.0D, 6.0D, 1, 2));
    }

    @Test
    public void airborneHealingAlwaysUsesImmediateSelfSplash() {
        assertTrue(BotHealingPlan.shouldSplashImmediately(false, false));
        assertTrue(BotHealingPlan.shouldSplashImmediately(true, true));
        assertFalse(BotHealingPlan.shouldSplashImmediately(false, true));
    }

    @Test
    public void unsafeSplashDistanceAddsCappedRetreatWithoutShorteningConfiguredRetreat() {
        assertEquals(10, BotHealingPlan.maximumRetreatTicks(0, 4.0D, 4.5D, 10));
        assertEquals(20, BotHealingPlan.maximumRetreatTicks(20, 2.5D, 4.5D, 10));
        assertEquals(0, BotHealingPlan.maximumRetreatTicks(0, 4.5D, 4.5D, 10));
    }

    @Test
    public void retreatEndsAtSafeSplashDistanceOrAtItsCap() {
        assertFalse(BotHealingPlan.isRetreatComplete(4, 0, 10, 4.49D, 4.5D));
        assertTrue(BotHealingPlan.isRetreatComplete(5, 0, 10, 4.5D, 4.5D));
        assertTrue(BotHealingPlan.isRetreatComplete(10, 0, 10, 3.0D, 4.5D));
        assertFalse(BotHealingPlan.isRetreatComplete(10, 20, 20, 5.0D, 4.5D));
    }
}
