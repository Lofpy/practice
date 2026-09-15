package com.poppy.practice.bot;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BotAttackPlanTest {
    @Test
    public void zeroReachNeverDealsDamageEvenWhenPlayersOverlap() {
        assertFalse(BotAttackPlan.shouldDamage(0.0D, 0.0D));
        assertFalse(BotAttackPlan.shouldDamage(0.01D, 0.0D));
        assertFalse(BotAttackPlan.shouldDamage(2.8D, 0.0D));
        assertTrue(BotAttackPlan.shouldDamage(2.8D, 2.8D));
    }

    @Test
    public void keepsAttackingWhileTargetHasInvulnerabilityTicks() {
        assertFalse(BotAttackPlan.shouldAttemptAttack(1, 10));
        assertTrue(BotAttackPlan.shouldAttemptAttack(0, 10));
        assertTrue(BotAttackPlan.shouldAttemptAttack(0, 0));
        assertFalse(BotAttackPlan.shouldAttemptAttack(1, 0));
    }

    @Test
    public void startsSwingingBeforeTheTargetEntersDamageRange() {
        assertTrue(BotAttackPlan.shouldSwing(3.8D, 4.0D,
                0.0D, true, 5.0D));
        assertFalse(BotAttackPlan.shouldDamage(3.8D, 3.0D));

        assertTrue(BotAttackPlan.shouldSwing(2.9D, 4.0D,
                0.0D, true, 5.0D));
        assertTrue(BotAttackPlan.shouldDamage(2.9D, 3.0D));
    }

    @Test
    public void doesNotSwingThroughWallsOrWhileLookingAway() {
        assertFalse(BotAttackPlan.shouldSwing(3.0D, 4.0D,
                0.0D, false, 0.0D));
        assertFalse(BotAttackPlan.shouldSwing(3.0D, 4.0D,
                0.0D, true, 19.0D));
        assertFalse(BotAttackPlan.shouldSwing(3.0D, 4.0D,
                2.5D, true, 0.0D));
    }
}
