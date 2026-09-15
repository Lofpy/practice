package com.poppy.practice.reach;

import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MovementFrameBufferTest {
    private static final long NOW = 1_000_000_000L;
    private static final double ERROR = 0.0000001D;

    @Test
    public void positionBeforeAttackUsesOnlyTheNewPosition() {
        PlayerSession session = session();
        session.handleMovement(true, 5.0D, 0.0D, 0.0D,
                false, 0.0F, 0.0F, true, 2, NOW + 1L);

        List<MovementFrame> candidates = attack(session, NOW + 2L)
                .getAttackerCandidates();

        assertEquals(1, candidates.size());
        assertEquals(5.0D, candidates.get(0).getX(), ERROR);
    }

    @Test
    public void attackBeforePositionKeepsTheOldPositionInItsSnapshot() {
        PlayerSession session = session();
        PlayerSession.AttackSnapshot attack = attack(session, NOW + 1L);
        session.handleMovement(true, 5.0D, 0.0D, 0.0D,
                false, 0.0F, 0.0F, true, 2, NOW + 2L);

        List<MovementFrame> candidates = attack.getAttackerCandidates();

        assertEquals(1, candidates.size());
        assertEquals(0.0D, candidates.get(0).getX(), ERROR);
    }

    @Test
    public void syntheticTargetSeedIgnoresRelativeDeltasUntilAbsolutePacket() {
        PlayerSession session = session();
        UUID targetId = UUID.randomUUID();
        ReachGuardConfig config = ReachGuardConfig.load(null);
        session.seedTarget(7, targetId, 3.0D, 0.0D, 0.0D, 1);
        assertTrue(session.needsTargetResync(7));

        assertFalse(session.moveTarget(7, 1.0D, 0.0D, 0.0D,
                2, NOW + 1L, config));
        PlayerSession.AttackSnapshot unverified = session.createAttackSnapshot(
                7, targetId, 10, NOW + 2L, config,
                new CandidateStateResolver());
        assertTrue(unverified.getTargetResolution().getCandidates().isEmpty());

        assertTrue(session.teleportTarget(7, 8.0D, 0.0D, 0.0D,
                3, NOW + 3L, config));
        assertFalse(session.needsTargetResync(7));
        PlayerSession.AttackSnapshot recovered = session.createAttackSnapshot(
                7, targetId, 10, NOW + 4L, config,
                new CandidateStateResolver());
        assertEquals(8.0D, recovered.getTargetResolution().getCandidates().get(0)
                .getBoundingBox().centerAtFeet().getX(), ERROR);
    }

    @Test
    public void invalidTargetStateRecoversOnlyFromAValidAbsolutePacket() {
        PlayerSession session = session();
        UUID targetId = UUID.randomUUID();
        ReachGuardConfig config = ReachGuardConfig.load(null);
        session.spawnTarget(7, targetId, 3.0D, 0.0D, 0.0D,
                1, NOW + 1L, config);

        assertFalse(session.teleportTarget(7, Double.NaN, 0.0D, 0.0D,
                2, NOW + 2L, config));
        assertTrue(session.needsTargetResync(7));
        assertFalse(session.moveTarget(7, 1.0D, 0.0D, 0.0D,
                3, NOW + 3L, config));

        assertTrue(session.teleportTarget(7, 8.0D, 0.0D, 0.0D,
                4, NOW + 4L, config));
        assertFalse(session.needsTargetResync(7));
        PlayerSession.AttackSnapshot recovered = session.createAttackSnapshot(
                7, targetId, 10, NOW + 5L, config,
                new CandidateStateResolver());

        assertFalse(recovered.isInvalidTarget());
        assertEquals(8.0D, recovered.getTargetResolution().getNewest()
                .getBoundingBox().centerAtFeet().getX(), ERROR);
    }

    private static PlayerSession session() {
        PlayerRuntimeSnapshot runtime = new PlayerRuntimeSnapshot(1,
                UUID.randomUUID(), 0.0D, 0.0D, 0.0D,
                true, false, false, false, 0, 0);
        return new PlayerSession(UUID.randomUUID(), 47,
                ReachGuardConfig.load(null), runtime, NOW);
    }

    private static PlayerSession.AttackSnapshot attack(PlayerSession session,
                                                        long now) {
        return session.createAttackSnapshot(7, UUID.randomUUID(), 10, now,
                ReachGuardConfig.load(null), new CandidateStateResolver());
    }
}
