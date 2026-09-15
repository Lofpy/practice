package com.poppy.practice.chatter;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ChatterDetectorTest {
    private final ChatterKbConfig config = ChatterKbTestConfig.defaults();
    private final ChatterDetector detector = new ChatterDetector();

    @Test
    public void normalFifteenCpsClicksRemainPrimary() {
        PlayerCombatState state = state();
        long now = 1000000000L;
        for (int index = 0; index < 10; index++) {
            state.clientFrameSequence++;
            ChatterDetector.DetectionResult result = attack(state, 4,
                    now + index * 67000000L, true);
            assertEquals(AttackClassification.PRIMARY, result.sample.classification);
        }
        assertEquals(0.0D, state.chatterScore, 0.0001D);
        assertEquals(ChatterState.CLEAN, state.chatterState);
    }

    @Test
    public void oneThreeMillisecondSameFramePairOnlyArms() {
        PlayerCombatState state = state();
        state.clientFrameSequence = 20L;
        attack(state, 4, 1000000000L, true);
        ChatterDetector.DetectionResult duplicateCandidate =
                attack(state, 4, 1003000000L, false);

        assertEquals(BurstType.SAME_FRAME_STRONG, duplicateCandidate.sample.burstType);
        assertEquals(AttackClassification.UNRESOLVED,
                duplicateCandidate.sample.classification);
        assertEquals(ChatterState.ARMED, state.chatterState);
    }

    @Test
    public void repeatedSameFrameBurstActivatesDetector() {
        PlayerCombatState state = state();
        state.clientFrameSequence = 20L;
        attack(state, 4, 1000000000L, true);
        attack(state, 4, 1003000000L, false);
        state.clientFrameSequence = 30L;
        attack(state, 4, 1800000000L, true);
        ChatterDetector.DetectionResult result = attack(state, 4, 1803000000L, false);

        assertEquals(AttackClassification.DUPLICATE, result.sample.classification);
        assertEquals(ChatterState.ACTIVE, state.chatterState);
        assertTrue(state.chatterScore >= config.activationScore);
    }

    @Test
    public void cleanBoundaryPairIsUnresolved() {
        PlayerCombatState state = state();
        state.clientFrameSequence = 40L;
        attack(state, 4, 1000000000L, true);
        state.clientFrameSequence++;
        ChatterDetector.DetectionResult result = attack(state, 4,
                1006000000L, true);

        assertEquals(BurstType.NONE, result.sample.burstType);
        assertEquals(AttackClassification.UNRESOLVED, result.sample.classification);
        assertEquals(ChatterState.CLEAN, state.chatterState);
    }

    @Test
    public void armedBoundaryPairCanBecomeDuplicate() {
        PlayerCombatState state = state();
        state.clientFrameSequence = 50L;
        attack(state, 4, 1000000000L, true);
        attack(state, 4, 1003000000L, false);
        assertEquals(ChatterState.ARMED, state.chatterState);

        state.clientFrameSequence++;
        ChatterDetector.DetectionResult result = attack(state, 4,
                1009000000L, true);

        assertEquals(BurstType.BOUNDARY_STRONG, result.sample.burstType);
        assertEquals(AttackClassification.DUPLICATE, result.sample.classification);
        assertEquals(ChatterState.ACTIVE, state.chatterState);
    }

    @Test
    public void sprintResetMakesSameFrameSecondAttackIneligible() {
        PlayerCombatState state = state();
        SprintStateTracker sprint = new SprintStateTracker();
        sprint.update(state, true, 1000000000L);
        boolean firstEligible = sprint.isSlowdownEligible(state, config);
        sprint.applyLocalAttackReset(state, firstEligible, 1000000000L);

        assertTrue(firstEligible);
        assertFalse(sprint.isSlowdownEligible(state, config));
    }

    @Test
    public void startSprintingMakesBoundaryDuplicateEligibleAgain() {
        PlayerCombatState state = state();
        SprintStateTracker sprint = new SprintStateTracker();
        sprint.update(state, true, 1000000000L);
        sprint.applyLocalAttackReset(state, true, 1001000000L);
        sprint.update(state, true, 1005000000L);

        assertTrue(sprint.isSlowdownEligible(state, config));
    }

    @Test
    public void nextClientFrameRestoresServerSprintStateAfterLocalReset() {
        PlayerCombatState state = state();
        SprintStateTracker sprint = new SprintStateTracker();
        sprint.update(state, true, 1000000000L);
        sprint.applyLocalAttackReset(state, true, 1001000000L);

        assertFalse(sprint.isSlowdownEligible(state, config));

        sprint.advanceClientFrame(state, 1050000000L);

        assertTrue(sprint.isSlowdownEligible(state, config));
    }

    private ChatterDetector.DetectionResult attack(PlayerCombatState state,
                                                    int target, long now,
                                                    boolean eligible) {
        state.recordPacket(now);
        return detector.classify(state, 47, target, now, eligible, config);
    }

    private PlayerCombatState state() {
        return new PlayerCombatState(UUID.randomUUID(), 1);
    }
}
