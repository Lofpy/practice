package com.poppy.practice.chatter;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class VelocityEstimatorTest {
    private final ChatterKbConfig config = ChatterKbTestConfig.defaults();
    private final VelocityEstimator estimator = new VelocityEstimator();

    @Test
    public void primarySlowdownChangesBaselineExactlyOnce() {
        KnockbackWindow window = window(1000000000L);
        window.baselineVelocity = window.baselineVelocity.multiplyHorizontal(0.6D);

        assertEquals(0.24D, window.baselineVelocity.getX(), 0.000001D);
        assertEquals(-0.12D, window.baselineVelocity.getZ(), 0.000001D);
        assertEquals(0.36D, window.baselineVelocity.getY(), 0.000001D);
    }

    @Test
    public void duplicateCorrectionDoesNotApplyAnotherSlowdown() {
        KnockbackWindow window = window(1000000000L);
        window.baselineVelocity = window.baselineVelocity.multiplyHorizontal(0.6D);
        Vec3 corrected = estimator.correction(window.baselineVelocity,
                window.initialVelocity, config);

        assertEquals(0.24D, window.baselineVelocity.getX(), 0.000001D);
        assertEquals(0.2304D, corrected.getX(), 0.000001D);
        assertEquals(-0.1152D, corrected.getZ(), 0.000001D);
    }

    @Test
    public void windowExpiresAfterFourClientFrames() {
        PlayerCombatState state = new PlayerCombatState(UUID.randomUUID(), 1);
        state.clientFrameSequence = 10L;
        KnockbackWindow window = new KnockbackWindow(1L, null, 1000000000L,
                10L, new Vec3(0.4D, 0.36D, 0.0D), null, false);
        WindowSafety safety = new WindowSafety();

        state.clientFrameSequence = 14L;
        assertFalse(safety.isExpired(state, window, 1100000000L, config));
        state.clientFrameSequence = 15L;
        assertTrue(safety.isExpired(state, window, 1100000000L, config));
    }

    @Test
    public void pingAboveLimitSkipsCorrection() {
        PlayerCombatState state = new PlayerCombatState(UUID.randomUUID(), 1);
        state.pingMs = 221;
        KnockbackWindow window = window(1000000000L);

        assertEquals(SkipReason.PING_TOO_HIGH, new WindowSafety().failure(
                state, window, 1010000000L, 20.0D, config));
    }

    @Test
    public void internalVelocityIsConsumedOnlyOnce() {
        PlayerCombatState state = new PlayerCombatState(UUID.randomUUID(), 1);
        InternalPacketGuard guard = new InternalPacketGuard();
        guard.mark(state);

        assertTrue(guard.consume(state));
        assertFalse(guard.consume(state));
    }

    private KnockbackWindow window(long now) {
        return new KnockbackWindow(1L, null, now, 0L,
                new Vec3(0.4D, 0.36D, -0.2D), null, false);
    }
}
