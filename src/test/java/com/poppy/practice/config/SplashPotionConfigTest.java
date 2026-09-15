package com.poppy.practice.config;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SplashPotionConfigTest {
    @Test
    public void acceptsFiniteSpawnOffsetsWithinSafeRange() {
        assertTrue(SplashPotionConfig.isValidSpawnOffset(-2.0D));
        assertTrue(SplashPotionConfig.isValidSpawnOffset(-0.45D));
        assertTrue(SplashPotionConfig.isValidSpawnOffset(0.0D));
        assertTrue(SplashPotionConfig.isValidSpawnOffset(2.0D));
    }

    @Test
    public void rejectsInvalidOrExcessiveSpawnOffsets() {
        assertFalse(SplashPotionConfig.isValidSpawnOffset(-2.01D));
        assertFalse(SplashPotionConfig.isValidSpawnOffset(2.01D));
        assertFalse(SplashPotionConfig.isValidSpawnOffset(Double.NaN));
        assertFalse(SplashPotionConfig.isValidSpawnOffset(Double.POSITIVE_INFINITY));
    }

    @Test
    public void acceptsWholeTickSelfCollisionDelays() {
        assertTrue(SplashPotionConfig.isValidSelfCollisionDelay(0.0D));
        assertTrue(SplashPotionConfig.isValidSelfCollisionDelay(2.0D));
        assertTrue(SplashPotionConfig.isValidSelfCollisionDelay(4.0D));
    }

    @Test
    public void rejectsInvalidSelfCollisionDelays() {
        assertFalse(SplashPotionConfig.isValidSelfCollisionDelay(-1.0D));
        assertFalse(SplashPotionConfig.isValidSelfCollisionDelay(0.5D));
        assertFalse(SplashPotionConfig.isValidSelfCollisionDelay(5.0D));
        assertFalse(SplashPotionConfig.isValidSelfCollisionDelay(Double.NaN));
    }
}
