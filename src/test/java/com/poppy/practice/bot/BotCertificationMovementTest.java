package com.poppy.practice.bot;

import org.junit.Test;
import static org.junit.Assert.*;

public class BotCertificationMovementTest {
    private final BotCertificationMovement movement =
            new BotCertificationMovement(BotSettings.certificationPreset());

    @Test public void approachesWithoutArtificialRetreatBeforeACombo() {
        for (double distance : new double[] {1.0D, 2.2D, 2.55D, 2.7D, 3.4D}) {
            assertForward(1.0F, distance, -0.2D);
        }
        assertEquals(0.18F, movement.strafeInput(0.18D, 1, false, 1.0F), 0.0F);
    }

    @Test public void oneLandedHitIsNotYetACombo() {
        movement.recordLandedHit();
        assertFalse(movement.isComboSpacing());
        assertForward(1.0F, 2.2D, 0.0D);
        assertEquals(-0.18F, movement.strafeInput(0.18D, -1, false, 1.0F), 0.0F);
    }

    @Test public void aSecondConfirmedHitAtNormalHurtTimeStartsSpacing() {
        movement.recordLandedHit();
        for (int tick = 0; tick < 10; tick++) movement.advanceTick();
        movement.recordLandedHit();
        assertTrue(movement.isComboSpacing());
        assertForward(0.0F, 2.55D, 0.0D);
        assertEquals(0.0F, movement.strafeInput(0.18D, -1, false, 0.0F), 0.0F);
    }

    @Test public void isolatedHitsCannotAccumulateACombo() {
        movement.recordLandedHit();
        for (int tick = 0; tick < 16; tick++) movement.advanceTick();
        movement.recordLandedHit();
        assertFalse(movement.isComboSpacing());
        assertForward(1.0F, 2.2D, 0.0D);
    }

    @Test public void establishedComboUsesBackwardHysteresis() {
        establishCombo();
        assertForward(-0.62F, 2.34D, 0.0D);
        assertForward(-0.62F, 2.36D, 0.0D);
        assertForward(-0.62F, 2.49D, 0.0D);
        assertForward(0.0F, 2.50D, 0.0D);
        assertForward(0.0F, 2.40D, 0.0D);
    }

    @Test public void establishedComboAnticipatesAClosingGapButPursuesAnOpeningOne() {
        establishCombo();
        assertForward(-0.62F, 2.55D, -0.20D);
        assertForward(1.0F, 2.4D, 0.20D);
        assertForward(1.0F, 3.0D, -10.0D);
        assertForward(0.0F, 2.10D, 1000.0D);
    }

    @Test public void nonFiniteSpeedsCannotCreateUnboundedPrediction() {
        establishCombo();
        assertForward(0.0F, 2.55D, Double.NaN);
        assertForward(0.0F, 2.55D, Double.POSITIVE_INFINITY);
        assertForward(0.0F, 2.55D, Double.NEGATIVE_INFINITY);
    }

    @Test public void spacingExpiresAfterEightTicksInsteadOfContinuingToRetreat() {
        establishCombo();
        for (int tick = 0; tick < 7; tick++) {
            movement.advanceTick();
            assertForward(0.0F, 2.55D, 0.0D);
        }
        movement.advanceTick();
        assertForward(1.0F, 2.2D, 0.0D);
        assertFalse(movement.isComboSpacing());
    }

    @Test public void anotherLandedHitRefreshesSpacing() {
        establishCombo();
        for (int tick = 0; tick < 7; tick++) movement.advanceTick();
        movement.recordLandedHit();
        movement.advanceTick();
        assertForward(0.0F, 2.55D, 0.0D);
    }

    @Test public void receivingKnockbackClearsTheComboAndHoldsW() {
        establishCombo();
        assertForward(-0.62F, 2.2D, 0.0D);
        assertEquals(1.0F, movement.forwardInput(1.0D, -1.0D, false, true), 0.0F);
        assertFalse(movement.isComboSpacing());
        movement.recordLandedHit();
        assertForward(1.0F, 2.2D, 0.0D);
    }

    @Test public void aFleeingOpponentIsPursuedStraightAhead() {
        establishCombo();
        assertForward(-0.62F, 2.2D, 0.0D);
        assertEquals(1.0F, movement.forwardInput(1.0D, -1.0D, true, false), 0.0F);
        assertEquals(0.0F, movement.strafeInput(0.18D, 1, true, 1.0F), 0.0F);
    }

    @Test public void consumablesOrAReceivedHitDiscardTheWholeChain() {
        establishCombo();
        assertForward(-0.62F, 2.2D, 0.0D);
        movement.suspend();
        movement.recordLandedHit();
        assertForward(1.0F, 2.4D, 0.0D);
        assertEquals(0.18F, movement.strafeInput(0.18D, 1, false, 1.0F), 0.0F);
    }

    private void establishCombo() {
        movement.recordLandedHit();
        movement.recordLandedHit();
    }

    private void assertForward(float expected, double distance, double relativeSpeed) {
        assertEquals(expected, movement.forwardInput(distance, relativeSpeed, false, false), 0.0F);
    }
}
