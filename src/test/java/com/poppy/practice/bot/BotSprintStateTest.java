package com.poppy.practice.bot;

import org.junit.Test;

import static com.poppy.practice.bot.BotSprintState.Transition.NONE;
import static com.poppy.practice.bot.BotSprintState.Transition.START;
import static com.poppy.practice.bot.BotSprintState.Transition.STOP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BotSprintStateTest {
    @Test
    public void attackAttemptStopsSprintOnceWithoutReleasingForwardMovement() {
        BotSprintState state = new BotSprintState();
        state.advance(true);

        assertEquals(STOP, state.stopAfterAttack());
        assertFalse(state.isSprinting());
        assertTrue(state.isMovementAllowed());
        assertEquals(NONE, state.stopAfterAttack());
        assertTrue(state.isMovementAllowed());
    }

    @Test
    public void attackWhileNotSprintingDoesNotEmitStop() {
        BotSprintState state = new BotSprintState();

        assertEquals(NONE, state.stopAfterAttack());
        assertFalse(state.isSprinting());
        assertTrue(state.isMovementAllowed());
    }

    @Test
    public void forwardSprintRestartsOnNextTickAfterAttackRelease() {
        BotSprintState state = new BotSprintState();
        state.advance(true);
        state.stopAfterAttack();

        assertEquals(START, state.advance(true));
        assertTrue(state.isSprinting());
        assertEquals(NONE, state.advance(true));
    }

    @Test
    public void stoppedForwardInputDoesNotRestartSprintAfterAttackRelease() {
        BotSprintState state = new BotSprintState();
        state.advance(true);
        state.stopAfterAttack();

        assertEquals(NONE, state.advance(false));
        assertFalse(state.isSprinting());
        assertTrue(state.isMovementAllowed());
    }

    @Test
    public void attackReleasePreservesPendingLandedHitReset() {
        BotSprintState state = new BotSprintState();
        state.advance(true);
        state.scheduleReset(2);

        assertEquals(STOP, state.stopAfterAttack());
        assertTrue(state.isMovementAllowed());
        for (int tick = 0; tick < 2; tick++) {
            assertEquals(NONE, state.advance(true));
            assertFalse(state.isMovementAllowed());
            assertFalse(state.isSprinting());
        }
        assertEquals(START, state.advance(true));
        assertTrue(state.isMovementAllowed());
    }

    @Test
    public void landedHitCanScheduleResetAfterTheClientAttackRelease() {
        BotSprintState state = new BotSprintState();
        state.advance(true);
        state.stopAfterAttack();
        state.scheduleReset(1);

        assertEquals(NONE, state.advance(true));
        assertFalse(state.isSprinting());
        assertFalse(state.isMovementAllowed());
        assertEquals(START, state.advance(true));
        assertTrue(state.isMovementAllowed());
    }

    @Test
    public void attackReleaseDoesNotShortenOrExtendActiveReset() {
        BotSprintState state = new BotSprintState();
        state.advance(true);
        state.scheduleReset(2);
        state.advance(true);

        assertEquals(NONE, state.stopAfterAttack());
        assertFalse(state.isMovementAllowed());
        assertEquals(NONE, state.advance(true));
        assertFalse(state.isMovementAllowed());
        assertEquals(NONE, state.stopAfterAttack());
        assertEquals(START, state.advance(true));
        assertTrue(state.isMovementAllowed());
    }

    @Test
    public void initiallyStationaryInputDoesNotEmitAnUnnecessaryStop() {
        BotSprintState state = new BotSprintState();

        assertEquals(NONE, state.advance(false));
        assertFalse(state.isSprinting());
        assertTrue(state.isMovementAllowed());
    }

    @Test
    public void sustainedForwardInputStartsSprintOnlyOnce() {
        BotSprintState state = new BotSprintState();

        assertEquals(START, state.advance(true));
        for (int tick = 0; tick < 100; tick++) {
            assertEquals(NONE, state.advance(true));
            assertTrue(state.isSprinting());
            assertTrue(state.isMovementAllowed());
        }
    }

    @Test
    public void naturallyReleasingAndPressingForwardEmitsOneTransitionEach() {
        BotSprintState state = new BotSprintState();
        state.advance(true);

        assertEquals(STOP, state.advance(false));
        assertFalse(state.isSprinting());
        assertTrue(state.isMovementAllowed());
        assertEquals(NONE, state.advance(false));
        assertEquals(START, state.advance(true));
        assertEquals(NONE, state.advance(true));
    }

    @Test
    public void schedulingAfterAHitDoesNotChangeTheCurrentMovementTick() {
        BotSprintState state = new BotSprintState();
        state.advance(true);

        state.scheduleReset(2);

        assertTrue(state.isSprinting());
        assertTrue(state.isMovementAllowed());
        assertEquals(STOP, state.advance(true));
        assertFalse(state.isSprinting());
        assertFalse(state.isMovementAllowed());
    }

    @Test
    public void oneTickResetContainsARealStoppedMovementTick() {
        BotSprintState state = new BotSprintState();
        state.advance(true);
        state.scheduleReset(1);

        assertEquals(STOP, state.advance(true));
        assertFalse(state.isSprinting());
        assertFalse(state.isMovementAllowed());
        assertEquals(START, state.advance(true));
        assertTrue(state.isSprinting());
        assertTrue(state.isMovementAllowed());
        assertEquals(NONE, state.advance(true));
    }

    @Test
    public void resetDurationSpansExactlyTheConfiguredNumberOfMovementTicks() {
        BotSprintState state = new BotSprintState();
        state.advance(true);
        state.scheduleReset(3);

        for (int tick = 0; tick < 3; tick++) {
            assertEquals(tick == 0 ? STOP : NONE, state.advance(true));
            assertFalse(state.isSprinting());
            assertFalse(state.isMovementAllowed());
        }
        assertEquals(START, state.advance(true));
        assertTrue(state.isMovementAllowed());
        assertEquals(NONE, state.advance(true));
    }

    @Test
    public void zeroDisablesArtificialResetWithoutRearmingSprint() {
        BotSprintState state = new BotSprintState();
        state.advance(true);

        for (int hit = 0; hit < 10; hit++) {
            state.scheduleReset(0);
            assertEquals(NONE, state.advance(true));
            assertTrue(state.isSprinting());
            assertTrue(state.isMovementAllowed());
        }
    }

    @Test
    public void negativeResetDurationIsIgnored() {
        BotSprintState state = new BotSprintState();
        state.advance(true);
        state.scheduleReset(-1);
        state.scheduleReset(Integer.MIN_VALUE);

        assertEquals(NONE, state.advance(true));
        assertTrue(state.isMovementAllowed());
    }

    @Test
    public void subsequentHitsCannotExtendAPendingReset() {
        BotSprintState state = new BotSprintState();
        state.advance(true);
        state.scheduleReset(1);
        state.scheduleReset(20);

        assertEquals(STOP, state.advance(true));
        assertFalse(state.isMovementAllowed());
        assertEquals(START, state.advance(true));
        assertTrue(state.isMovementAllowed());
    }

    @Test
    public void hitsDuringAnActiveResetCannotExtendItIncludingItsLastTick() {
        BotSprintState state = new BotSprintState();
        state.advance(true);
        state.scheduleReset(3);

        for (int tick = 0; tick < 3; tick++) {
            assertEquals(tick == 0 ? STOP : NONE, state.advance(true));
            assertFalse(state.isMovementAllowed());
            state.scheduleReset(20);
        }
        assertEquals(START, state.advance(true));
        assertTrue(state.isMovementAllowed());
    }

    @Test
    public void expiredResetDoesNotStartSprintWithoutForwardInput() {
        BotSprintState state = new BotSprintState();
        state.advance(true);
        state.scheduleReset(1);
        assertEquals(STOP, state.advance(true));

        assertEquals(NONE, state.advance(false));
        assertFalse(state.isSprinting());
        assertTrue(state.isMovementAllowed());
        assertEquals(START, state.advance(true));
    }

    @Test
    public void naturalForwardReleaseStillConsumesResetMovementTicks() {
        BotSprintState state = new BotSprintState();
        state.advance(true);
        state.scheduleReset(2);

        assertEquals(STOP, state.advance(false));
        assertFalse(state.isMovementAllowed());
        assertEquals(NONE, state.advance(true));
        assertFalse(state.isMovementAllowed());
        assertEquals(START, state.advance(true));
        assertTrue(state.isMovementAllowed());
    }

    @Test
    public void nextLandedHitCanScheduleAnotherResetAfterSprintResumes() {
        BotSprintState state = new BotSprintState();
        state.advance(true);
        state.scheduleReset(1);
        state.advance(true);
        assertEquals(START, state.advance(true));

        state.scheduleReset(1);

        assertEquals(STOP, state.advance(true));
        assertFalse(state.isMovementAllowed());
        assertEquals(START, state.advance(true));
        assertTrue(state.isMovementAllowed());
    }

    @Test
    public void resetWhileAlreadyWalkingDoesNotEmitDuplicateStop() {
        BotSprintState state = new BotSprintState();
        state.advance(false);
        state.scheduleReset(1);

        assertEquals(NONE, state.advance(true));
        assertFalse(state.isMovementAllowed());
        assertEquals(START, state.advance(true));
    }

    @Test
    public void disablingResetDoesNotCancelOneAlreadyInProgress() {
        BotSprintState state = new BotSprintState();
        state.advance(true);
        state.scheduleReset(2);
        assertEquals(STOP, state.advance(true));

        state.scheduleReset(0);

        assertEquals(NONE, state.advance(true));
        assertFalse(state.isMovementAllowed());
        assertEquals(START, state.advance(true));
    }

    @Test
    public void veryLargeDurationsDoNotOverflowIntoAnImmediateRestart() {
        BotSprintState state = new BotSprintState();
        state.advance(true);
        state.scheduleReset(Integer.MAX_VALUE);

        assertEquals(STOP, state.advance(true));
        assertFalse(state.isMovementAllowed());
        assertEquals(NONE, state.advance(true));
        assertFalse(state.isMovementAllowed());
    }
}
