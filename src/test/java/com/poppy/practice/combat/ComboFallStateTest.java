package com.poppy.practice.combat;

import org.junit.Test;

import static org.junit.Assert.*;

public class ComboFallStateTest {
    private static final double EPSILON = 0.000000001D;
    private static final double FIRST_FALL = -0.08D;

    @Test
    public void ordinaryJumpDoesNotActivateHeightLimit() {
        ComboFallState state = new ComboFallState(64.0D);
        state.sample(70.0D, false, 3.0D, 1L);

        assertFalse(state.isFalling());
        assertEquals(0.42D, state.limitVertical(0.42D), EPSILON);
    }

    @Test
    public void activatesExactlyAtThresholdNotBefore() {
        ComboFallState state = new ComboFallState(64.0D);
        state.arm();
        state.sample(66.999D, false, 3.0D, 1L);
        assertFalse(state.isFalling());
        assertEquals(0.3D, state.limitVertical(0.3D), EPSILON);

        state.sample(67.0D, false, 3.0D, 2L);
        assertTrue(state.isFalling());
        assertEquals(FIRST_FALL, state.limitVertical(0.3D), EPSILON);
    }

    @Test
    public void negativeWorldCoordinatesUseRelativeStandingHeight() {
        ComboFallState state = new ComboFallState(-64.0D);
        state.arm();
        state.sample(-61.5D, false, 2.5D, 1L);

        assertTrue(state.isFalling());
        assertEquals(FIRST_FALL, state.limitVertical(0.3D), EPSILON);
    }

    @Test
    public void duplicateSamplesAndHitsInSameTickKeepConstantDescent() {
        ComboFallState state = falling();
        for (int index = 0; index < 100; index++) {
            state.arm();
            state.sample(67.0D, false, 3.0D, 10L);
            assertEquals(FIRST_FALL, state.limitVertical(0.3D), EPSILON);
        }
    }

    @Test
    public void descentDoesNotAccelerateWithElapsedTicks() {
        ComboFallState state = falling();
        state.sample(66.9D, false, 3.0D, 11L);
        assertEquals(FIRST_FALL, state.limitVertical(0.3D), EPSILON);

        state.sample(66.9D, false, 3.0D, 11L);
        assertEquals(FIRST_FALL, state.limitVertical(0.3D), EPSILON);

        state.sample(66.7D, false, 3.0D, 12L);
        assertEquals(FIRST_FALL, state.limitVertical(0.3D), EPSILON);
    }

    @Test
    public void skippedSamplesDoNotAffectConstantDescent() {
        ComboFallState sparse = falling();
        ComboFallState everyTick = falling();
        for (long tick = 11L; tick <= 20L; tick++) {
            everyTick.sample(66.0D, false, 3.0D, tick);
        }
        sparse.sample(66.0D, false, 3.0D, 20L);

        assertEquals(everyTick.limitVertical(0.3D), sparse.limitVertical(0.3D), EPSILON);
    }

    @Test
    public void olderTickDoesNotChangeDescent() {
        ComboFallState state = falling();
        state.sample(66.9D, false, 3.0D, 9L);
        assertEquals(FIRST_FALL, state.limitVertical(0.3D), EPSILON);
        state.sample(66.9D, false, 3.0D, 11L);
        assertEquals(FIRST_FALL, state.limitVertical(0.3D), EPSILON);
    }

    @Test
    public void arbitrarilyLargeElapsedTicksRemainFinite() {
        ComboFallState state = new ComboFallState(64.0D);
        state.arm();
        state.sample(67.0D, false, 3.0D, Long.MIN_VALUE);
        state.sample(65.0D, false, 3.0D, Long.MAX_VALUE);

        assertEquals(FIRST_FALL, state.limitVertical(0.3D), EPSILON);
    }

    @Test
    public void continuedHitsCannotLiftParticipantAfterThreshold() {
        ComboFallState state = falling();
        state.arm();
        state.sample(66.5D, false, 3.0D, 11L);

        assertTrue(state.isFalling());
        assertEquals(FIRST_FALL, state.limitVertical(4.0D), EPSILON);
    }

    @Test
    public void existingFasterDescentIsSlowedToConfiguredSpeed() {
        ComboFallState state = falling();

        assertEquals(FIRST_FALL, state.limitVertical(-1.0D), EPSILON);
        assertEquals(FIRST_FALL, state.limitVertical(-0.01D), EPSILON);
    }

    @Test
    public void descendingBelowThresholdDoesNotReleaseFallLatch() {
        ComboFallState state = falling();
        state.sample(64.01D, false, 3.0D, 11L);

        assertTrue(state.isFalling());
        assertTrue(state.limitVertical(0.3D) < 0.0D);
    }

    @Test
    public void increasedThresholdDoesNotReleaseAlreadyLatchedFall() {
        ComboFallState state = falling();
        state.sample(66.0D, false, 10.0D, 11L);

        assertTrue(state.isFalling());
        assertTrue(state.limitVertical(0.3D) < 0.0D);
    }

    @Test
    public void landingRestoresNormalVelocityAndClearsArming() {
        ComboFallState state = falling();
        state.sample(64.0D, true, 3.0D, 11L);
        assertFalse(state.isFalling());
        assertEquals(0.3D, state.limitVertical(0.3D), EPSILON);

        state.sample(68.0D, false, 3.0D, 12L);
        assertFalse(state.isFalling());
    }

    @Test
    public void standingOnNewPlatformUpdatesGroundBaseline() {
        ComboFallState state = new ComboFallState(64.0D);
        state.sample(80.0D, true, 3.0D, 1L);
        state.arm();
        state.sample(82.9D, false, 3.0D, 2L);
        assertFalse(state.isFalling());

        state.sample(83.0D, false, 3.0D, 3L);
        assertTrue(state.isFalling());
    }

    @Test
    public void groundedSampleBeforeArmingSupportsGroundedKnockback() {
        ComboFallState state = new ComboFallState(64.0D);
        state.sample(64.0D, true, 3.0D, 1L);
        state.arm();
        state.sample(67.0D, false, 3.0D, 2L);

        assertTrue(state.isFalling());
    }

    @Test
    public void repeatedGroundedSamplesPreserveNewlyArmedKnockbackUntilTakeoff() {
        ComboFallState state = new ComboFallState(64.0D);
        state.arm();
        state.sample(64.0D, true, 3.0D, 1L);
        state.sample(64.0D, true, 3.0D, 1L);
        state.sample(64.0D, true, 3.0D, 2L);
        state.sample(67.0D, false, 3.0D, 3L);

        assertTrue(state.isFalling());
    }

    @Test
    public void landingAfterSubThresholdKnockbackClearsPendingArm() {
        ComboFallState state = new ComboFallState(64.0D);
        state.arm();
        state.sample(65.0D, false, 3.0D, 1L);
        state.sample(64.0D, true, 3.0D, 2L);
        state.sample(68.0D, false, 3.0D, 3L);

        assertFalse(state.isFalling());
    }

    @Test
    public void disablingHeightImmediatelyClearsLatch() {
        ComboFallState state = falling();
        state.sample(67.0D, false, 0.0D, 11L);

        assertFalse(state.isFalling());
        assertEquals(0.3D, state.limitVertical(0.3D), EPSILON);
        state.sample(68.0D, false, 3.0D, 12L);
        assertFalse(state.isFalling());
    }

    @Test
    public void disablingHeightClearsPendingArmAsWell() {
        ComboFallState state = new ComboFallState(64.0D);
        state.arm();
        state.sample(65.0D, false, 0.0D, 1L);
        state.sample(68.0D, false, 3.0D, 2L);

        assertFalse(state.isFalling());
    }

    @Test
    public void disabledAirborneSampleDoesNotInventNewGroundBaseline() {
        ComboFallState state = falling();
        state.sample(67.0D, false, 0.0D, 11L);
        state.arm();
        state.sample(67.0D, false, 3.0D, 12L);

        assertTrue(state.isFalling());
    }

    @Test
    public void resetClearsFallAndUsesTeleportDestinationAsBaseline() {
        ComboFallState state = falling();
        state.reset(100.0D);
        assertFalse(state.isFalling());
        assertEquals(0.3D, state.limitVertical(0.3D), EPSILON);

        state.sample(104.0D, false, 3.0D, 11L);
        assertFalse(state.isFalling());
        state.arm();
        state.sample(103.0D, false, 3.0D, 12L);
        assertTrue(state.isFalling());
    }

    @Test
    public void copiedFallRetainsSpeedAndIsIndependent() {
        ComboFallState original = falling();
        ComboFallState copied = original.copy();
        assertNotSame(original, copied);
        assertTrue(copied.isFalling());
        assertEquals(FIRST_FALL, copied.limitVertical(-1.0D), EPSILON);
        copied.sample(66.0D, false, 3.0D, 0.04D, 12L);
        assertEquals(-0.04D, copied.limitVertical(0.3D), EPSILON);
        assertEquals(FIRST_FALL, original.limitVertical(0.3D), EPSILON);

        original.reset(100.0D);
        assertTrue(copied.isFalling());
        assertFalse(original.isFalling());
    }

    @Test
    public void copiedPendingArmAndBaselineAreIndependent() {
        ComboFallState original = new ComboFallState(64.0D);
        original.arm();
        ComboFallState copied = original.copy();
        original.reset(100.0D);
        copied.sample(67.0D, false, 3.0D, 1L);

        assertTrue(copied.isFalling());
        assertFalse(original.isFalling());
    }

    @Test
    public void invalidSampleDoesNotMutateExistingFall() {
        ComboFallState state = falling();
        expectInvalid(() -> state.sample(Double.NaN, true, 3.0D, 11L));
        expectInvalid(() -> state.sample(Double.POSITIVE_INFINITY, false, 3.0D, 11L));
        expectInvalid(() -> state.sample(64.0D, true, -1.0D, 11L));
        expectInvalid(() -> state.sample(64.0D, true, Double.NaN, 11L));
        expectInvalid(() -> state.sample(64.0D, true, Double.POSITIVE_INFINITY, 11L));

        assertTrue(state.isFalling());
        assertEquals(FIRST_FALL, state.limitVertical(0.3D), EPSILON);
    }

    @Test
    public void invalidConstructionResetAndVelocityAreRejected() {
        expectInvalid(() -> new ComboFallState(Double.NaN));
        expectInvalid(() -> new ComboFallState(Double.NEGATIVE_INFINITY));
        ComboFallState state = falling();
        expectInvalid(() -> state.reset(Double.NaN));
        expectInvalid(() -> state.limitVertical(Double.NaN));
        expectInvalid(() -> state.limitVertical(Double.NEGATIVE_INFINITY));

        assertTrue(state.isFalling());
        assertEquals(FIRST_FALL, state.limitVertical(0.3D), EPSILON);
    }

    @Test
    public void configuredDescentAppliesFromFirstThresholdSample() {
        ComboFallState state = new ComboFallState(64.0D);
        state.arm();
        state.sample(67.0D, false, 3.0D, 0.04D, 10L);

        assertTrue(state.isFalling());
        assertEquals(-0.04D, state.limitVertical(0.4D), EPSILON);
        assertEquals(-0.04D, state.limitVertical(-2.0D), EPSILON);
    }

    @Test
    public void liveSpeedChangeAdjustsAlreadyLatchedDescentInSameTick() {
        ComboFallState state = falling();
        state.sample(66.9D, false, 3.0D, 0.04D, 10L);
        assertEquals(-0.04D, state.limitVertical(0.3D), EPSILON);
        state.sample(66.9D, false, 3.0D, 0.2D, 10L);
        assertEquals(-0.2D, state.limitVertical(-2.0D), EPSILON);
    }

    @Test
    public void invalidSpeedsDoNotMutateLatchedDescent() {
        ComboFallState state = falling();
        for (double invalid : new double[] {0.0D, -0.1D, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            expectInvalid(() -> state.sample(64.0D, true, 3.0D, invalid, 11L));
            assertTrue(state.isFalling());
            assertEquals(FIRST_FALL, state.limitVertical(-2.0D), EPSILON);
        }
    }

    @Test
    public void landingAndNewKnockbackUsesTheLatestConfiguredSpeed() {
        ComboFallState state = falling();
        state.sample(64.0D, true, 3.0D, 0.04D, 11L);
        assertEquals(-1.0D, state.limitVertical(-1.0D), EPSILON);
        state.arm();
        state.sample(67.0D, false, 3.0D, 0.12D, 12L);
        assertEquals(-0.12D, state.limitVertical(0.3D), EPSILON);
    }

    private static ComboFallState falling() {
        ComboFallState state = new ComboFallState(64.0D);
        state.arm();
        state.sample(67.0D, false, 3.0D, 10L);
        return state;
    }

    private static void expectInvalid(Runnable operation) {
        try {
            operation.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
