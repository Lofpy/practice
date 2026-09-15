package com.poppy.practice.listener;

import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SplashPotionListenerTest {
    @Test
    public void calculatesForwardAndHeightOffsetWithoutChangingLaunchVelocity() {
        Vector launchVelocity = new Vector(3.0D, 4.0D, 0.0D);

        Vector offset = SplashPotionListener.calculateSpawnOffset(
                launchVelocity, 0.25D, 0.10D);

        assertEquals(0.15D, offset.getX(), 0.0001D);
        assertEquals(0.30D, offset.getY(), 0.0001D);
        assertEquals(0.0D, offset.getZ(), 0.0001D);
        assertEquals(3.0D, launchVelocity.getX(), 0.0001D);
        assertEquals(4.0D, launchVelocity.getY(), 0.0001D);
    }

    @Test
    public void zeroVelocityStillAppliesHeightOffset() {
        Vector offset = SplashPotionListener.calculateSpawnOffset(
                new Vector(), 0.25D, -0.20D);

        assertEquals(0.0D, offset.getX(), 0.0001D);
        assertEquals(-0.20D, offset.getY(), 0.0001D);
        assertEquals(0.0D, offset.getZ(), 0.0001D);
    }
}
