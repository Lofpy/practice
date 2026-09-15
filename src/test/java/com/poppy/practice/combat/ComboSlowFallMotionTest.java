package com.poppy.practice.combat;

import org.bukkit.entity.Player;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ComboSlowFallMotionTest {
    private static final double EPSILON = 0.000000001D;
    private static final double DRAG = 0.91D;

    @Test
    public void doesNotInventHorizontalVelocityWithoutHistory() {
        Fixture fixture = new Fixture();
        assertFalse(fixture.motion.apply(fixture.player, -0.12D, 10L));
        assertEquals(0, fixture.sent);
    }

    @Test
    public void acceptedMovementPreservesHorizontalMomentumAndAppliesAirDrag() {
        Fixture fixture = new Fixture();
        fixture.motion.recordMove(fixture.id, 0.5D, -0.25D, 10L);

        assertTrue(fixture.motion.apply(fixture.player, -0.12D, 10L));
        fixture.assertPacket(0.5D * DRAG, -0.12D, -0.25D * DRAG);
        verify(fixture.player, never()).getVelocity();
        verify(fixture.player, never()).setVelocity(any());
    }

    @Test
    public void missingMovementTicksDecayExistingMomentumWithoutReinjectingIt() {
        Fixture fixture = new Fixture();
        fixture.motion.recordMove(fixture.id, 0.5D, -0.25D, 10L);
        fixture.motion.apply(fixture.player, -0.12D, 13L);
        fixture.assertPacket(0.5D * Math.pow(DRAG, 4), -0.12D, -0.25D * Math.pow(DRAG, 4));
    }

    @Test
    public void correctionDoesNotConsumeDragAgainWhenAppliedTwiceInOneTick() {
        Fixture fixture = new Fixture();
        fixture.motion.recordMove(fixture.id, 0.5D, -0.25D, 10L);
        fixture.motion.apply(fixture.player, -0.12D, 10L);
        fixture.motion.apply(fixture.player, -0.12D, 10L);
        fixture.assertPacket(0.5D * DRAG, -0.12D, -0.25D * DRAG);
    }

    @Test
    public void sameTickKnockbackWinsOverPreHitMovementWithoutImmediateExtraDrag() {
        Fixture fixture = new Fixture();
        fixture.motion.recordMove(fixture.id, 0.1D, 0.1D, 10L);
        fixture.motion.recordVelocity(fixture.id, 0.7D, -0.4D, 10L, 0);
        fixture.motion.recordMove(fixture.id, 0.2D, 0.2D, 10L);
        fixture.motion.apply(fixture.player, -0.12D, 10L);
        fixture.assertPacket(0.7D, -0.12D, -0.4D);
    }

    @Test
    public void delayedMovementCannotOverwriteNewHitBeforePingGraceExpires() {
        Fixture fixture = new Fixture();
        fixture.motion.recordVelocity(fixture.id, 0.7D, -0.4D, 10L, 100);
        fixture.motion.recordMove(fixture.id, 0.1D, 0.1D, 11L);
        fixture.motion.apply(fixture.player, -0.12D, 12L);
        fixture.assertPacket(0.7D * DRAG * DRAG, -0.12D, -0.4D * DRAG * DRAG);
    }

    @Test
    public void usesAcceptedMovementAgainWhenPingGraceExpires() {
        Fixture fixture = new Fixture();
        fixture.motion.recordVelocity(fixture.id, 0.7D, -0.4D, 10L, 100);
        fixture.motion.recordMove(fixture.id, 0.2D, 0.3D, 13L);
        fixture.motion.apply(fixture.player, -0.12D, 13L);
        fixture.assertPacket(0.2D * DRAG, -0.12D, 0.3D * DRAG);
    }

    @Test
    public void predictionRetainsHitWithoutAnyPostHitMovement() {
        Fixture fixture = new Fixture();
        fixture.motion.recordMove(fixture.id, 0.1D, 0.1D, 9L);
        fixture.motion.recordVelocity(fixture.id, 0.7D, -0.4D, 10L, 0);
        fixture.motion.apply(fixture.player, -0.12D, 15L);
        fixture.assertPacket(0.7D * Math.pow(DRAG, 5), -0.12D, -0.4D * Math.pow(DRAG, 5));
    }

    @Test
    public void veryHighPingDoesNotBlockMovementForMoreThanTwentyTicks() {
        Fixture fixture = new Fixture();
        fixture.motion.recordVelocity(fixture.id, 0.7D, -0.4D, 10L, Integer.MAX_VALUE);
        fixture.motion.recordMove(fixture.id, 0.2D, 0.3D, 30L);
        fixture.motion.apply(fixture.player, -0.12D, 30L);
        fixture.assertPacket(0.2D * DRAG, -0.12D, 0.3D * DRAG);
    }

    @Test
    public void negativePingHasOneTickGrace() {
        Fixture fixture = new Fixture();
        fixture.motion.recordVelocity(fixture.id, 0.7D, -0.4D, 10L, -100);
        fixture.motion.recordMove(fixture.id, 0.2D, 0.3D, 11L);
        fixture.motion.apply(fixture.player, -0.12D, 11L);
        fixture.assertPacket(0.2D * DRAG, -0.12D, 0.3D * DRAG);
    }

    @Test
    public void olderMovementAndVelocityCannotReplaceNewerHistory() {
        Fixture fixture = new Fixture();
        fixture.motion.recordMove(fixture.id, 0.2D, 0.3D, 10L);
        fixture.motion.recordMove(fixture.id, 1.0D, 1.0D, 9L);
        fixture.motion.apply(fixture.player, -0.12D, 10L);
        fixture.assertPacket(0.2D * DRAG, -0.12D, 0.3D * DRAG);
        fixture.motion.recordVelocity(fixture.id, 0.7D, -0.4D, 12L, 0);
        fixture.motion.recordVelocity(fixture.id, 1.0D, 1.0D, 11L, 0);
        fixture.motion.apply(fixture.player, -0.12D, 12L);
        fixture.assertPacket(0.7D, -0.12D, -0.4D);
    }

    @Test
    public void latestSameTickVelocityWins() {
        Fixture fixture = new Fixture();
        fixture.motion.recordVelocity(fixture.id, 0.7D, -0.4D, 10L, 0);
        fixture.motion.recordVelocity(fixture.id, -0.6D, 0.5D, 10L, 0);
        fixture.motion.apply(fixture.player, -0.12D, 10L);
        fixture.assertPacket(-0.6D, -0.12D, 0.5D);
    }

    @Test
    public void invalidInputsDoNotPoisonPreviouslyValidHistory() {
        Fixture fixture = new Fixture();
        fixture.motion.recordMove(fixture.id, 0.2D, 0.3D, 10L);
        fixture.motion.recordMove(fixture.id, Double.NaN, 1.0D, 11L);
        fixture.motion.recordVelocity(fixture.id, 1.0D, Double.POSITIVE_INFINITY, 11L, 100);
        assertFalse(fixture.motion.apply(fixture.player, Double.NaN, 10L));
        fixture.motion.apply(fixture.player, -0.12D, 10L);
        fixture.assertPacket(0.2D * DRAG, -0.12D, 0.3D * DRAG);
    }

    @Test
    public void packetComponentsRemainInsideVanillaBounds() {
        Fixture fixture = new Fixture();
        fixture.motion.recordMove(fixture.id, 20.0D, -20.0D, 10L);
        fixture.motion.apply(fixture.player, -10.0D, 10L);
        fixture.assertPacket(3.9D, -3.9D, -3.9D);
    }

    @Test
    public void futureTimestampNeverAmplifiesHorizontalVelocity() {
        Fixture fixture = new Fixture();
        fixture.motion.recordVelocity(fixture.id, 0.7D, -0.4D, 11L, 0);
        fixture.motion.apply(fixture.player, -0.12D, 10L);
        fixture.assertPacket(0.7D, -0.12D, -0.4D);
    }

    @Test
    public void forgetRemovesBothMovementAndVelocityFromNextMatch() {
        Fixture fixture = new Fixture();
        fixture.motion.recordMove(fixture.id, 0.2D, 0.3D, 10L);
        fixture.motion.recordVelocity(fixture.id, 0.7D, -0.4D, 10L, 100);
        fixture.motion.forget(fixture.id);
        assertFalse(fixture.motion.apply(fixture.player, -0.12D, 11L));
    }

    @Test
    public void retainDropsEndedParticipantsOnly() {
        Fixture fixture = new Fixture();
        UUID otherId = UUID.randomUUID();
        Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(otherId);
        fixture.motion.recordMove(fixture.id, 0.2D, 0.3D, 10L);
        fixture.motion.recordMove(otherId, 0.4D, 0.5D, 10L);
        fixture.motion.retain(Collections.singleton(fixture.id));
        assertTrue(fixture.motion.apply(fixture.player, -0.12D, 11L));
        assertFalse(fixture.motion.apply(other, -0.12D, 11L));
    }

    @Test
    public void shutdownClearDropsAllHistory() {
        Fixture fixture = new Fixture();
        fixture.motion.recordMove(fixture.id, 0.2D, 0.3D, 10L);
        fixture.motion.clear();
        assertFalse(fixture.motion.apply(fixture.player, -0.12D, 11L));
    }

    @Test
    public void nativeSenderDoesNotRequireNmsForNonCraftPlayer() {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        ComboSlowFallMotion motion = new ComboSlowFallMotion();
        motion.recordMove(id, 0.2D, 0.3D, 10L);
        assertFalse(motion.apply(player, -0.12D, 10L));
    }

    private static final class Fixture {
        private final UUID id = UUID.randomUUID();
        private final Player player = mock(Player.class);
        private final ComboSlowFallMotion motion;
        private int sent;
        private double x;
        private double y;
        private double z;

        private Fixture() {
            when(player.getUniqueId()).thenReturn(id);
            motion = new ComboSlowFallMotion((recipient, packetX, packetY, packetZ) -> {
                assertSame(player, recipient);
                sent++;
                x = packetX;
                y = packetY;
                z = packetZ;
                return true;
            });
        }

        private void assertPacket(double expectedX, double expectedY, double expectedZ) {
            assertTrue(sent > 0);
            assertEquals(expectedX, x, EPSILON);
            assertEquals(expectedY, y, EPSILON);
            assertEquals(expectedZ, z, EPSILON);
        }
    }
}
