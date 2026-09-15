package com.poppy.practice.bot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BotMovementTest {
    @Test
    public void disablingStrafeStopsSidewaysInputInBothDirections() {
        assertEquals(0.0F, BotMovement.strafeInput(false, false, 0.828D, 1), 0.0F);
        assertEquals(0.0F, BotMovement.strafeInput(false, false, 0.828D, -1), 0.0F);
        assertEquals(0.0F, BotMovement.strafeInput(false, true, 1.0D, -1), 0.0F);
    }

    @Test
    public void enabledStrafeRetainsItsConfiguredStrengthAndDirection() {
        assertEquals(0.828F, BotMovement.strafeInput(true, false, 0.828D, 1), 0.0F);
        assertEquals(-0.828F, BotMovement.strafeInput(true, false, 0.828D, -1), 0.0F);
        assertEquals(0.0F, BotMovement.strafeInput(true, false, 0.0D, 1), 0.0F);
    }

    @Test
    public void chasingAnOpponentFacingAwayStillDisablesStrafe() {
        assertEquals(0.0F, BotMovement.strafeInput(true, true, 0.828D, 1), 0.0F);
        assertEquals(0.0F, BotMovement.strafeInput(true, true, 0.828D, -1), 0.0F);
    }

    @Test
    public void aimsUsingMinecraftYawCoordinates() {
        assertEquals(-90.0F, BotMovement.yawTo(10.0D, 0.0D), 0.0001F);
        assertEquals(0.0F, BotMovement.yawTo(0.0D, 10.0D), 0.0001F);
    }

    @Test
    public void turnsAcrossTheWrappedAngleByTheShortestRoute() {
        float result = BotMovement.rotateToward(170.0F, -170.0F, 8.0D);

        assertEquals(178.0F, result, 0.0001F);
        assertEquals(20.0D, BotMovement.angleDifference(170.0F, -170.0F), 0.0001D);
    }

    @Test
    public void limitsAimChangeInsteadOfSnapping() {
        assertEquals(32.0F, BotMovement.rotateToward(0.0F, 90.0F, 32.0D), 0.0001F);
        assertEquals(-24.0F, BotMovement.rotateToward(0.0F, -60.0F, 24.0D), 0.0001F);
    }

    @Test
    public void calculatesTheDirectionFacingAwayFromAPlayer() {
        assertEquals(-180.0F, BotMovement.oppositeYaw(0.0F), 0.0001F);
        assertEquals(-90.0F, BotMovement.oppositeYaw(90.0F), 0.0001F);
        assertEquals(10.0F, BotMovement.oppositeYaw(-170.0F), 0.0001F);
    }

    @Test
    public void approachesHoldsAndRetreatsByDistance() {
        assertEquals(1.0F, BotMovement.forwardInput(6.0D, 2.65D, 1.55D), 0.0001F);
        assertTrue(BotMovement.forwardInput(2.0D, 2.65D, 1.55D) > 0.0F);
        assertTrue(BotMovement.forwardInput(1.0D, 2.65D, 1.55D) < 0.0F);
    }

    @Test
    public void holdsFullForwardInputDuringKnockbackAtEveryCombatDistance() {
        for (double distance : new double[]{1.0D, 1.55D, 2.0D, 2.65D, 4.0D}) {
            assertEquals(1.0F, BotMovement.forwardInput(distance, 2.65D, 1.55D, true), 0.0F);
        }
    }

    @Test
    public void keepsConfiguredSpacingWhenNotTakingKnockback() {
        for (double distance : new double[]{1.0D, 2.0D, 4.0D}) {
            assertEquals(BotMovement.forwardInput(distance, 2.65D, 1.55D),
                    BotMovement.forwardInput(distance, 2.65D, 1.55D, false), 0.0F);
        }
    }

    @Test
    public void usingASwordSlowsMovementInputNotReceivedKnockback() {
        assertEquals(0.2F, BotMovement.itemUseInput(1.0F, true), 0.0001F);
        assertEquals(-0.2F, BotMovement.itemUseInput(-1.0F, true), 0.0001F);
        assertEquals(0.0F, BotMovement.itemUseInput(0.0F, true), 0.0F);
        assertEquals(1.0F, BotMovement.itemUseInput(1.0F, false), 0.0F);
    }
}
