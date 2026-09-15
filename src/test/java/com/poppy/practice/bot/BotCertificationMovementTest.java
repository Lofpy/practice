package com.poppy.practice.bot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BotCertificationMovementTest {
    private final BotCertificationMovement movement =
            new BotCertificationMovement(BotSettings.certificationPreset());

    @Test
    public void approachesTargetsOutsidePreferredSpacing() {
        assertForward(1.0F, 3.4D, 0.0D);
        assertForward(1.0F, 2.7D, 0.0D);
    }

    @Test
    public void movesGentlyForwardBetweenEngagements() {
        assertForward(0.28F, 2.55D, 0.0D);
        assertEquals(0.18F, movement.strafeInput(0.18D, 1, false, 0.28F), 0.0F);
        assertEquals(-0.18F, movement.strafeInput(0.18D, -1, false, 0.28F), 0.0F);
    }

    @Test
    public void backsAwayFromAnOverlappingOpponentWithoutStrafing() {
        assertForward(-0.62F, 2.2D, 0.0D);
        assertEquals(0.0F, movement.strafeInput(0.18D, 1, false, -0.62F), 0.0F);
    }

    @Test
    public void backwardHysteresisPreventsFlickerAtRetreatBoundary() {
        assertForward(-0.62F, 2.34D, 0.0D);
        assertForward(-0.62F, 2.36D, 0.0D);
        assertForward(-0.62F, 2.49D, 0.0D);
        assertForward(0.28F, 2.50D, 0.0D);
        assertForward(0.28F, 2.40D, 0.0D);
    }

    @Test
    public void anticipatesAClosingGapBeforeTheOpponentGetsTooClose() {
        assertForward(-0.62F, 2.55D, -0.20D);
    }

    @Test
    public void immediatelyPursuesAnOpeningGapEvenDuringACombo() {
        movement.recordLandedHit();
        assertForward(-0.62F, 2.2D, 0.0D);
        assertForward(1.0F, 2.4D, 0.20D);
        assertEquals(0.0F, movement.strafeInput(0.18D, 1, false, 1.0F), 0.0F);
    }

    @Test
    public void farAwayOpponentTakesPriorityOverClosingPrediction() {
        movement.recordLandedHit();
        assertForward(1.0F, 3.0D, -10.0D);
    }

    @Test
    public void extremeMovementPredictionIsCappedInsteadOfChasingATeleport() {
        assertForward(0.28F, 2.10D, 1000.0D);
    }

    @Test
    public void nonFiniteRelativeSpeedsAreTreatedAsStationary() {
        assertForward(0.28F, 2.55D, Double.NaN);
        assertForward(0.28F, 2.55D, Double.POSITIVE_INFINITY);
        assertForward(0.28F, 2.55D, Double.NEGATIVE_INFINITY);
    }

    @Test
    public void landedHitHoldsUsefulSpacingAndSuppressesSidewaysMovement() {
        movement.recordLandedHit();
        assertForward(0.0F, 2.55D, 0.0D);
        assertEquals(0.0F, movement.strafeInput(0.18D, -1, false, 0.0F), 0.0F);
    }

    @Test
    public void landedHitDoesNotForceAnOtherwiseUnnecessaryRetreat() {
        movement.recordLandedHit();
        assertForward(0.0F, 2.55D, 0.0D);
        assertForward(1.0F, 2.80D, 0.0D);
    }

    @Test
    public void comboWindowExpiresAfterEightAdvances() {
        movement.recordLandedHit();
        for (int tick = 0; tick < 7; tick++) {
            movement.advanceTick();
            assertForward(0.0F, 2.55D, 0.0D);
            assertEquals(0.0F, movement.strafeInput(0.18D, 1, false, 0.0F), 0.0F);
        }
        movement.advanceTick();
        assertForward(0.28F, 2.55D, 0.0D);
        assertEquals(0.18F, movement.strafeInput(0.18D, 1, false, 0.28F), 0.0F);
        for (int tick = 0; tick < 20; tick++) {
            movement.advanceTick();
        }
        assertForward(0.28F, 2.55D, 0.0D);
    }

    @Test
    public void anotherLandedHitRefreshesOnlyTheSpacingWindow() {
        movement.recordLandedHit();
        for (int tick = 0; tick < 7; tick++) {
            movement.advanceTick();
        }
        movement.recordLandedHit();
        movement.advanceTick();
        assertForward(0.0F, 2.55D, 0.0D);
    }

    @Test
    public void receivingKnockbackHoldsForwardAndClearsOldBackwardState() {
        assertForward(-0.62F, 2.2D, 0.0D);
        assertEquals(1.0F, movement.forwardInput(1.0D, -1.0D, false, true), 0.0F);
        assertForward(0.28F, 2.4D, 0.0D);
    }

    @Test
    public void aFleeingOpponentIsPursuedStraightAheadRegardlessOfSpacing() {
        movement.recordLandedHit();
        assertForward(-0.62F, 2.2D, 0.0D);
        assertEquals(1.0F, movement.forwardInput(1.0D, -1.0D, true, false), 0.0F);
        assertEquals(0.0F, movement.strafeInput(0.18D, 1, true, 1.0F), 0.0F);
        movement.suspend();
        assertForward(0.28F, 2.4D, 0.0D);
    }

    @Test
    public void suspendingForConsumablesClearsBothComboAndBackwardState() {
        movement.recordLandedHit();
        assertForward(-0.62F, 2.2D, 0.0D);
        movement.suspend();
        assertForward(0.28F, 2.4D, 0.0D);
        assertEquals(0.18F, movement.strafeInput(0.18D, 1, false, 0.28F), 0.0F);
    }

    private void assertForward(float expected, double distance, double relativeSpeed) {
        assertEquals(expected, movement.forwardInput(distance, relativeSpeed, false, false), 0.0F);
    }
}
