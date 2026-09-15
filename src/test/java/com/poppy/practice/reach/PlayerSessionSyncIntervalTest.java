package com.poppy.practice.reach;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PlayerSessionSyncIntervalTest {
    private static final long NOW = 1_000_000_000L;
    private final ReachGuardConfig config = ReachGuardConfig.load(null);

    @Test
    public void intervalDefersDirtyStateUntilItsServerTickIsDue() {
        PlayerSession session = session(10);
        UUID targetId = UUID.randomUUID();
        session.spawnTarget(7, targetId, 3.0D, 0.0D, 0.0D,
                10, NOW + 1L, config);

        PlayerSession.SyncRequest first = session.reserveSyncIfDue(10, 3);
        assertEquals(PlayerSession.SyncRequest.Status.RESERVED, first.getStatus());
        assertTrue(first.getActionId() < 0);
        assertEquals(PlayerSession.SyncRequest.Status.NONE,
                session.reserveSyncIfDue(10, 3).getStatus());

        assertTrue(session.moveTarget(7, 0.25D, 0.0D, 0.0D,
                10, NOW + 2L, config));
        assertEquals(PlayerSession.SyncRequest.Status.DEFERRED,
                session.reserveSyncIfDue(10, 3).getStatus());
        assertEquals(PlayerSession.SyncRequest.Status.DEFERRED,
                session.reserveSyncIfDue(12, 3).getStatus());
        assertEquals(PlayerSession.SyncRequest.Status.RESERVED,
                session.reserveSyncIfDue(13, 3).getStatus());
    }

    @Test
    public void failedWriteKeepsCoverageDirtyForDeferredRetry() {
        PlayerSession session = session(20);
        session.spawnTarget(7, UUID.randomUUID(), 3.0D, 0.0D, 0.0D,
                20, NOW + 1L, config);

        PlayerSession.SyncRequest failed = session.reserveSyncIfDue(20, 2);
        assertTrue(failed.isReserved());
        session.discardSyncMarker(failed.getActionId());

        assertTrue(session.reserveSyncIfDue(20, 2).shouldRetry());
        assertTrue(session.reserveSyncIfDue(21, 2).shouldRetry());
        PlayerSession.SyncRequest retry = session.reserveSyncIfDue(22, 2);
        assertTrue(retry.isReserved());
        assertTrue(retry.getActionId() < 0);
    }

    @Test
    public void intervalOneLimitsReservationsAndNextTickCoversTrailingBatch() {
        PlayerSession session = session(30);
        UUID firstTarget = UUID.randomUUID();
        UUID secondTarget = UUID.randomUUID();
        session.spawnTarget(7, firstTarget, 3.0D, 0.0D, 0.0D,
                30, NOW + 1L, config);
        PlayerSession.SyncRequest first = session.reserveSyncIfDue(30, 1);
        session.markSyncSent(first.getActionId(), NOW + 2L);

        session.spawnTarget(8, secondTarget, 4.0D, 0.0D, 0.0D,
                30, NOW + 3L, config);
        assertTrue(session.moveTarget(7, 0.125D, 0.0D, 0.0D,
                30, NOW + 4L, config));
        assertEquals(PlayerSession.SyncRequest.Status.DEFERRED,
                session.reserveSyncIfDue(30, 1).getStatus());

        PlayerSession.SyncRequest trailing = session.reserveSyncIfDue(31, 1);
        assertTrue(trailing.isReserved());
        assertFalse(first.getActionId() == trailing.getActionId());
        session.markSyncSent(trailing.getActionId(), NOW + 5L);
        assertTrue(session.confirmSync(trailing.getActionId(), NOW + 6L));

        assertConfirmed(session, 7, firstTarget);
        assertConfirmed(session, 8, secondTarget);
    }

    @Test
    public void movementResetMakesClearedPendingTargetMarkerRetryable() {
        PlayerSession session = session(40);
        session.spawnTarget(7, UUID.randomUUID(), 3.0D, 0.0D, 0.0D,
                40, NOW + 1L, config);
        PlayerSession.SyncRequest pending = session.reserveSyncIfDue(40, 20);
        assertTrue(pending.isReserved());

        session.resetMovement(new PlayerRuntimeSnapshot(1, UUID.randomUUID(),
                        0.0D, 0.0D, 0.0D, true, false,
                        false, false, 25, 40),
                config.getTeleportGraceTicks(), NOW + 2L);

        assertTrue(session.reserveSyncIfDue(40, 20).isReserved());
    }

    private void assertConfirmed(PlayerSession session, int entityId, UUID targetId) {
        PlayerSession.AttackSnapshot snapshot = session.createAttackSnapshot(
                entityId, targetId, 32, NOW + 7L, config,
                new CandidateStateResolver());
        assertTrue(snapshot.getTargetResolution().hasConfirmed());
        assertEquals(TargetFrameStatus.CONFIRMED,
                snapshot.getTargetResolution().getNewest().getStatus());
    }

    private PlayerSession session(int serverTick) {
        return new PlayerSession(UUID.randomUUID(), 47, config,
                new PlayerRuntimeSnapshot(1, UUID.randomUUID(),
                        0.0D, 0.0D, 0.0D, true, false,
                        false, false, 25, serverTick), NOW);
    }
}
