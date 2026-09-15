package com.poppy.practice.reach;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class CandidateStateResolverTest {
    private static final long NOW = 1000000000L;
    private static final double ERROR = 0.0000001D;
    private static final UUID TARGET = UUID.randomUUID();

    private final CandidateStateResolver resolver = new CandidateStateResolver();

    @Test
    public void confirmedAndUnconfirmedFramesProduceFiveCandidates() {
        TargetFrame confirmed = frame(1L, 0.0D, TargetFrameStatus.CONFIRMED,
                false, NOW - 10000000L);
        TargetFrame unconfirmed = frame(2L, 4.0D,
                TargetFrameStatus.SENT_UNCONFIRMED, false, NOW - 5000000L);

        CandidateStateResolver.TargetResolution result = resolver.resolve(
                Arrays.asList(confirmed, unconfirmed), NOW, 300L);

        List<TargetFrame> candidates = result.getCandidates();
        assertEquals(5, candidates.size());
        assertEquals(0.0D, candidates.get(0).getBoundingBox().centerAtFeet().getX(), ERROR);
        assertEquals(4.0D, candidates.get(1).getBoundingBox().centerAtFeet().getX(), ERROR);
        assertEquals(1.0D, candidates.get(2).getBoundingBox().centerAtFeet().getX(), ERROR);
        assertEquals(2.0D, candidates.get(3).getBoundingBox().centerAtFeet().getX(), ERROR);
        assertEquals(3.0D, candidates.get(4).getBoundingBox().centerAtFeet().getX(), ERROR);
        assertTrue(result.hasConfirmed());
        assertTrue(result.hasMixedConfirmation());
    }

    @Test
    public void teleportBoundaryNeverProducesInterpolation() {
        TargetFrame confirmed = frame(1L, 0.0D, TargetFrameStatus.CONFIRMED,
                false, NOW - 10000000L);
        TargetFrame teleport = frame(2L, 10.0D,
                TargetFrameStatus.SENT_UNCONFIRMED, true, NOW - 5000000L, 1L);

        CandidateStateResolver.TargetResolution result = resolver.resolve(
                Arrays.asList(confirmed, teleport), NOW, 300L);

        assertEquals(2, result.getCandidates().size());
        for (TargetFrame candidate : result.getCandidates()) {
            assertFalse(candidate.getStatus() == TargetFrameStatus.INTERPOLATED);
        }
    }

    @Test
    public void relativeMoveAfterTeleportDoesNotHideTheTeleportBoundary() {
        TargetFrame confirmed = frame(1L, 0.0D, TargetFrameStatus.CONFIRMED,
                false, NOW - 10000000L);
        TargetFrame teleport = frame(2L, 10.0D,
                TargetFrameStatus.SENT_UNCONFIRMED, true, NOW - 7000000L, 1L);
        TargetFrame relativeAfterTeleport = frame(3L, 11.0D,
                TargetFrameStatus.SENT_UNCONFIRMED, false, NOW - 5000000L, 1L);

        CandidateStateResolver.TargetResolution result = resolver.resolve(
                Arrays.asList(confirmed, teleport, relativeAfterTeleport), NOW, 300L);

        assertEquals(2, result.getCandidates().size());
        for (TargetFrame candidate : result.getCandidates()) {
            assertFalse(candidate.getStatus() == TargetFrameStatus.INTERPOLATED);
        }
    }

    @Test
    public void confirmedTeleportCanInterpolateToLaterMoveInSameEpoch() {
        TargetFrame confirmedTeleport = frame(2L, 10.0D,
                TargetFrameStatus.CONFIRMED, true, NOW - 7000000L, 1L);
        TargetFrame relativeAfterTeleport = frame(3L, 14.0D,
                TargetFrameStatus.SENT_UNCONFIRMED, false, NOW - 5000000L, 1L);

        CandidateStateResolver.TargetResolution result = resolver.resolve(
                Arrays.asList(confirmedTeleport, relativeAfterTeleport), NOW, 300L);

        assertEquals(5, result.getCandidates().size());
        assertEquals(11.0D, result.getCandidates().get(2)
                .getBoundingBox().centerAtFeet().getX(), ERROR);
        assertEquals(12.0D, result.getCandidates().get(3)
                .getBoundingBox().centerAtFeet().getX(), ERROR);
        assertEquals(13.0D, result.getCandidates().get(4)
                .getBoundingBox().centerAtFeet().getX(), ERROR);
    }

    @Test
    public void evictedTeleportStillSeparatesMovementEpochs() {
        TargetFrame oldConfirmed = new TargetFrame(1L, NOW - 10000000L,
                NOW - 9000000L, 0, 7, TARGET,
                Aabb.player(0.0D, 0.0D, 0.0D), TargetFrameStatus.CONFIRMED,
                false, true, 0L);
        TargetFrame newestAfterTeleport = new TargetFrame(4L, NOW - 5000000L,
                0L, 0, 7, TARGET, Aabb.player(11.0D, 0.0D, 0.0D),
                TargetFrameStatus.SENT_UNCONFIRMED, false, true, 1L);

        CandidateStateResolver.TargetResolution result = resolver.resolve(
                Arrays.asList(oldConfirmed, newestAfterTeleport), NOW, 300L);

        assertEquals(2, result.getCandidates().size());
        for (TargetFrame candidate : result.getCandidates()) {
            assertFalse(candidate.getStatus() == TargetFrameStatus.INTERPOLATED);
        }
    }

    @Test
    public void historyOlderThanMaximumRewindIsOnlyExposedAsStale() {
        TargetFrame old = frame(1L, 0.0D, TargetFrameStatus.CONFIRMED,
                false, NOW - 400000000L);
        TargetFrame newest = frame(2L, 1.0D,
                TargetFrameStatus.SENT_UNCONFIRMED, false, NOW - 350000000L);

        CandidateStateResolver.TargetResolution result = resolver.resolve(
                Arrays.asList(old, newest), NOW, 300L);

        assertTrue(result.getCandidates().isEmpty());
        assertNotNull(result.getStale());
        assertEquals(1L, result.getStale().getStateSequence());
        assertEquals(2L, result.getNewest().getStateSequence());
    }

    @Test
    public void invalidFramesAreIgnored() {
        TargetFrame invalid = new TargetFrame(1L, NOW, 0L, 0, 7, TARGET,
                Aabb.player(0.0D, 0.0D, 0.0D), TargetFrameStatus.INVALID,
                false, false);

        CandidateStateResolver.TargetResolution result = resolver.resolve(
                Arrays.asList(invalid), NOW, 300L);

        assertTrue(result.getCandidates().isEmpty());
        assertFalse(result.hasConfirmed());
    }

    @Test
    public void expiredConfirmedFrameDoesNotMakeFreshUnconfirmedStateReliable() {
        TargetFrame oldConfirmed = frame(1L, 0.0D,
                TargetFrameStatus.CONFIRMED, false, NOW - 400000000L);
        TargetFrame freshUnconfirmed = frame(2L, 1.0D,
                TargetFrameStatus.SENT_UNCONFIRMED, false, NOW - 10000000L);

        CandidateStateResolver.TargetResolution result = resolver.resolve(
                Arrays.asList(oldConfirmed, freshUnconfirmed), NOW, 300L);

        assertEquals(1, result.getCandidates().size());
        assertFalse(result.hasConfirmed());
        assertFalse(result.hasMixedConfirmation());
    }

    @Test
    public void staleConfirmedAnchorSurvivesFreshUnconfirmedCandidates() {
        TargetFrame oldConfirmed = frame(1L, 0.0D,
                TargetFrameStatus.CONFIRMED, false, NOW - 400000000L);
        TargetFrame freshUnconfirmed = frame(2L, 4.0D,
                TargetFrameStatus.SENT_UNCONFIRMED, false, NOW - 10000000L);

        CandidateStateResolver.TargetResolution result = resolver.resolve(
                Arrays.asList(oldConfirmed, freshUnconfirmed), NOW, 300L);

        assertEquals(1, result.getCandidates().size());
        assertNotNull(result.getStale());
        assertEquals(1L, result.getStale().getStateSequence());
    }

    private static TargetFrame frame(long sequence, double x,
                                     TargetFrameStatus status,
                                     boolean teleport, long sentAt) {
        return frame(sequence, x, status, teleport, sentAt, 0L);
    }

    private static TargetFrame frame(long sequence, double x,
                                     TargetFrameStatus status,
                                     boolean teleport, long sentAt,
                                     long teleportEpoch) {
        return new TargetFrame(sequence, sentAt,
                status == TargetFrameStatus.CONFIRMED ? sentAt + 1L : 0L,
                0, 7, TARGET, Aabb.player(x, 0.0D, 0.0D), status,
                teleport, true, teleportEpoch);
    }
}
