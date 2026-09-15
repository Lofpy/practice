package com.poppy.practice.bot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BotPearlTargetingTest {
    @Test
    public void detectsWhenPlayerFacesAwayFromBot() {
        assertTrue(BotPearlTargeting.isFacingAway(0.0F, 0.0D, 0.0D,
                0.0D, -5.0D));
        assertFalse(BotPearlTargeting.isFacingAway(0.0F, 0.0D, 0.0D,
                0.0D, 5.0D));
    }

    @Test
    public void targetsPlayersRightAndLeftSides() {
        BotPearlTargeting.Target right = BotPearlTargeting.beside(
                10.0D, 20.0D, 0.0F, 1, 1.5D);
        BotPearlTargeting.Target left = BotPearlTargeting.beside(
                10.0D, 20.0D, 0.0F, -1, 1.5D);

        assertEquals(11.5D, right.getX(), 0.0001D);
        assertEquals(20.0D, right.getZ(), 0.0001D);
        assertEquals(8.5D, left.getX(), 0.0001D);
        assertEquals(20.0D, left.getZ(), 0.0001D);
    }

    @Test
    public void targetsThreeBlocksToSideForAirbornePlayer() {
        BotPearlTargeting.Target side = BotPearlTargeting.beside(
                10.0D, 20.0D, 90.0F, 1, 3.0D);

        assertEquals(10.0D, side.getX(), 0.0001D);
        assertEquals(23.0D, side.getZ(), 0.0001D);
    }

    @Test
    public void detectsOnlyGroundToAirTransition() {
        assertTrue(BotPearlTargeting.becameAirborne(true, false));
        assertFalse(BotPearlTargeting.becameAirborne(false, false));
        assertFalse(BotPearlTargeting.becameAirborne(true, true));
        assertFalse(BotPearlTargeting.becameAirborne(false, true));
    }

    @Test
    public void stopsPearlAtOrAfterItsSideTarget() {
        assertFalse(BotPearlTargeting.reachedOrPassed(
                8.0D, 20.0D, 1.0D, 0.0D, 10.0D, 20.0D, 0.6D));
        assertTrue(BotPearlTargeting.reachedOrPassed(
                9.5D, 20.0D, 1.0D, 0.0D, 10.0D, 20.0D, 0.6D));
        assertTrue(BotPearlTargeting.reachedOrPassed(
                10.5D, 20.0D, 1.0D, 0.0D, 10.0D, 20.0D, 0.6D));
    }

    @Test
    public void stationaryPearlDoesNotPassDistantTarget() {
        assertFalse(BotPearlTargeting.reachedOrPassed(
                0.0D, 0.0D, 0.0D, 0.0D, 10.0D, 0.0D, 0.6D));
    }

    @Test
    public void usesSameSixteenSecondCooldownAsPlayer() {
        assertEquals(320, BotPearlTargeting.cooldownTicks(16));
    }
}
