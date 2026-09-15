package com.poppy.practice.bot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class BotHorizontalMotionTest {
    private static final double EPSILON = 0.000000001D;

    @Test
    public void clientMomentumPersistsWithoutLeakingIntoServerMotion() {
        Motion motion = new Motion(0.0D, 0.0D);
        BotHorizontalMotion state = new BotHorizontalMotion(motion);

        state.runClientTick(() -> motion.setXZ(0.27D, -0.19D));
        assertMotion(motion, 0.0D, 0.0D);
        state.runClientTick(() -> assertMotion(motion, 0.27D, -0.19D));
        assertMotion(motion, 0.0D, 0.0D);
    }

    @Test
    public void repeatedKnockbackPacketsReplaceRatherThanAccumulateClientMomentum() {
        Motion motion = new Motion(0.0D, 0.0D);
        BotHorizontalMotion state = new BotHorizontalMotion(motion);

        for (int hit = 0; hit < 20; hit++) {
            // The server computes each new hit from its own retained baseline.
            double packetX = motion.getX() / 2.0D + 0.28D + 0.33D;
            double packetZ = motion.getZ() / 2.0D;
            assertEquals(0.61D, packetX, EPSILON);
            state.runClientTick(() -> {
                motion.setXZ(packetX, packetZ);
                assertMotion(motion, 0.61D, 0.0D);
                // Representative native air drag after receiving the packet.
                motion.setXZ(motion.getX() * 0.91D, motion.getZ() * 0.91D);
            });
            assertMotion(motion, 0.0D, 0.0D);
        }
    }

    @Test
    public void failedClientTickStillRestoresServerMotionAndRetainsClientMomentum() {
        Motion motion = new Motion(0.12D, -0.08D);
        BotHorizontalMotion state = new BotHorizontalMotion(motion);
        RuntimeException failure = new RuntimeException("tick failed");

        try {
            state.runClientTick(() -> {
                motion.setXZ(0.42D, -0.31D);
                throw failure;
            });
            fail("Expected tick failure");
        } catch (RuntimeException actual) {
            assertSame(failure, actual);
        }
        assertMotion(motion, 0.12D, -0.08D);
        state.runClientTick(() -> assertMotion(motion, 0.42D, -0.31D));
        assertMotion(motion, 0.12D, -0.08D);
    }

    @Test
    public void acceptedAttackSlowsServerAndClientMomentumOnceEach() {
        Motion motion = new Motion(0.2D, -0.1D);
        BotHorizontalMotion state = new BotHorizontalMotion(motion);

        state.runClientTick(() -> {
            motion.setXZ(0.5D, -0.4D);
            state.runServerAttack(() -> {
                assertMotion(motion, 0.2D, -0.1D);
                motion.setXZ(motion.getX() * 0.6D, motion.getZ() * 0.6D);
            });
            assertMotion(motion, 0.5D, -0.4D);
            state.applyClientAttackSlowdown();
            assertMotion(motion, 0.3D, -0.24D);
        });
        assertMotion(motion, 0.12D, -0.06D);
        state.runClientTick(() -> assertMotion(motion, 0.3D, -0.24D));
    }

    @Test
    public void rejectedServerAttackStillAllowsClientAttackSlowdown() {
        Motion motion = new Motion(0.0D, 0.0D);
        BotHorizontalMotion state = new BotHorizontalMotion(motion);

        state.runClientTick(() -> {
            motion.setXZ(-0.5D, 0.25D);
            // A target's invulnerability ticks reject NMS damage and its slowdown.
            state.runServerAttack(() -> assertMotion(motion, 0.0D, 0.0D));
            state.applyClientAttackSlowdown();
            assertMotion(motion, -0.3D, 0.15D);
        });
        assertMotion(motion, 0.0D, 0.0D);
    }

    @Test
    public void failedServerAttackRestoresClientAndPreservesNativeServerChanges() {
        Motion motion = new Motion(0.2D, 0.1D);
        BotHorizontalMotion state = new BotHorizontalMotion(motion);
        RuntimeException failure = new RuntimeException("attack failed");

        state.runClientTick(() -> {
            motion.setXZ(0.5D, 0.4D);
            try {
                state.runServerAttack(() -> {
                    motion.setXZ(0.12D, 0.06D);
                    throw failure;
                });
                fail("Expected attack failure");
            } catch (RuntimeException actual) {
                assertSame(failure, actual);
            }
            assertMotion(motion, 0.5D, 0.4D);
        });
        assertMotion(motion, 0.12D, 0.06D);
    }

    @Test
    public void serverChangesBetweenTicksDoNotReplaceCachedClientMomentum() {
        Motion motion = new Motion(0.0D, 0.0D);
        BotHorizontalMotion state = new BotHorizontalMotion(motion);
        state.runClientTick(() -> motion.setXZ(0.4D, -0.3D));

        motion.setXZ(0.06D, 0.02D);
        state.runClientTick(() -> assertMotion(motion, 0.4D, -0.3D));
        assertMotion(motion, 0.06D, 0.02D);
    }

    @Test
    public void groundDragDecaysServerMotionWithoutChangingClientOrVerticalMotion() {
        Motion motion = new Motion(0.2D, -0.1D);
        motion.y = 0.34D;
        BotHorizontalMotion state = new BotHorizontalMotion(motion);
        double groundDrag = (double) (0.6F * 0.91F);

        state.runClientTick(() -> {
            motion.setXZ(0.5D, -0.4D);
            state.advanceServerMotion(groundDrag);
            assertMotion(motion, 0.5D, -0.4D);
            assertEquals(0.34D, motion.y, EPSILON);
        });
        assertMotion(motion, 0.2D * groundDrag, -0.1D * groundDrag);
        state.runClientTick(() -> assertMotion(motion, 0.5D, -0.4D));
    }

    @Test
    public void airDragUsesNativeFloatPrecisionWithoutChangingClientMomentum() {
        Motion motion = new Motion(0.2D, -0.1D);
        BotHorizontalMotion state = new BotHorizontalMotion(motion);
        double airDrag = (double) 0.91F;

        state.runClientTick(() -> {
            motion.setXZ(0.5D, -0.4D);
            state.advanceServerMotion(airDrag);
            assertMotion(motion, 0.5D, -0.4D);
        });
        assertMotion(motion, 0.2D * airDrag, -0.1D * airDrag);
    }

    @Test
    public void nativeSmallMotionCutoffRunsBeforeDragAndKeepsExactBoundary() {
        Motion motion = new Motion(0.004999D, -0.005D);
        BotHorizontalMotion state = new BotHorizontalMotion(motion);
        double airDrag = (double) 0.91F;

        state.runClientTick(() -> state.advanceServerMotion(airDrag));
        assertMotion(motion, 0.0D, -0.005D * airDrag);
        state.runClientTick(() -> state.advanceServerMotion(airDrag));
        assertMotion(motion, 0.0D, 0.0D);

        motion.setXZ(0.005D, -0.004999D);
        state.runClientTick(() -> state.advanceServerMotion(airDrag));
        assertMotion(motion, 0.005D * airDrag, 0.0D);
    }

    @Test
    public void serverAttackSlowdownIsFollowedByOneNativeDragStep() {
        Motion motion = new Motion(0.2D, -0.1D);
        BotHorizontalMotion state = new BotHorizontalMotion(motion);
        double groundDrag = (double) (0.6F * 0.91F);

        state.runClientTick(() -> {
            motion.setXZ(0.5D, -0.4D);
            state.runServerAttack(() ->
                    motion.setXZ(motion.getX() * 0.6D, motion.getZ() * 0.6D));
            state.applyClientAttackSlowdown();
            state.advanceServerMotion(groundDrag);
            assertMotion(motion, 0.3D, -0.24D);
        });
        assertMotion(motion, 0.12D * groundDrag, -0.06D * groundDrag);
    }

    @Test
    public void externalCollisionBaselineDecaysToZeroInsteadOfPersistingAcrossTicks() {
        Motion motion = new Motion(0.4D, -0.25D);
        BotHorizontalMotion state = new BotHorizontalMotion(motion);
        double airDrag = (double) 0.91F;

        for (int tick = 0; tick < 80; tick++) {
            double previousX = motion.getX();
            double previousZ = motion.getZ();
            state.runClientTick(() -> {
                motion.setXZ(0.6D, -0.3D);
                state.advanceServerMotion(airDrag);
            });
            assertMotion(motion,
                    (Math.abs(previousX) < 0.005D ? 0.0D : previousX) * airDrag,
                    (Math.abs(previousZ) < 0.005D ? 0.0D : previousZ) * airDrag);
        }
        assertMotion(motion, 0.0D, 0.0D);
    }

    @Test(expected = IllegalStateException.class)
    public void advancingServerMotionOutsideClientTickIsRejected() {
        new BotHorizontalMotion(new Motion(0.2D, 0.1D)).advanceServerMotion(0.91D);
    }

    @Test
    public void advancingServerMotionInsideNativeAttackIsRejectedWithoutCorruptingState() {
        Motion motion = new Motion(0.2D, -0.1D);
        BotHorizontalMotion state = new BotHorizontalMotion(motion);

        state.runClientTick(() -> {
            motion.setXZ(0.5D, -0.4D);
            try {
                state.runServerAttack(() -> state.advanceServerMotion((double) 0.91F));
                fail("Expected server-attack scope rejection");
            } catch (IllegalStateException expected) {
                assertMotion(motion, 0.5D, -0.4D);
            }
        });
        assertMotion(motion, 0.2D, -0.1D);
    }

    @Test
    public void invalidDragDoesNotPoisonEitherHorizontalState() {
        Motion motion = new Motion(0.2D, -0.1D);
        BotHorizontalMotion state = new BotHorizontalMotion(motion);

        for (double drag : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1.0D}) {
            state.runClientTick(() -> {
                motion.setXZ(0.5D, -0.4D);
                try {
                    state.advanceServerMotion(drag);
                    fail("Expected invalid drag rejection");
                } catch (IllegalArgumentException expected) {
                    assertMotion(motion, 0.5D, -0.4D);
                }
            });
            assertMotion(motion, 0.2D, -0.1D);
        }
    }

    @Test
    public void nativeVerticalMotionIsNeitherRestoredNorReduced() {
        Motion motion = new Motion(0.0D, 0.0D);
        motion.y = 0.34D;
        BotHorizontalMotion state = new BotHorizontalMotion(motion);

        state.runClientTick(() -> {
            motion.setXZ(0.5D, 0.1D);
            state.runServerAttack(() -> assertEquals(0.34D, motion.y, EPSILON));
            state.applyClientAttackSlowdown();
            assertEquals(0.34D, motion.y, EPSILON);
            motion.y = (motion.y - 0.08D) * 0.9800000190734863D;
        });
        assertEquals((0.34D - 0.08D) * 0.9800000190734863D, motion.y, EPSILON);
    }

    @Test
    public void serverAttackOutsideClientTickUsesExistingServerMotion() {
        Motion motion = new Motion(0.2D, 0.1D);
        BotHorizontalMotion state = new BotHorizontalMotion(motion);

        state.runServerAttack(() -> motion.setXZ(0.12D, 0.06D));

        assertMotion(motion, 0.12D, 0.06D);
    }

    @Test
    public void nestedClientTickIsRejectedWithoutLosingOuterMotion() {
        Motion motion = new Motion(0.2D, 0.1D);
        BotHorizontalMotion state = new BotHorizontalMotion(motion);

        state.runClientTick(() -> {
            motion.setXZ(0.5D, 0.4D);
            try {
                state.runClientTick(() -> fail("Nested tick must not run"));
                fail("Expected nested tick rejection");
            } catch (IllegalStateException expected) {
                assertMotion(motion, 0.5D, 0.4D);
            }
        });
        assertMotion(motion, 0.2D, 0.1D);
    }

    @Test(expected = IllegalStateException.class)
    public void clientAttackSlowdownCannotMutateExposedServerMotion() {
        new BotHorizontalMotion(new Motion(0.2D, 0.1D)).applyClientAttackSlowdown();
    }

    private static void assertMotion(Motion motion, double x, double z) {
        assertEquals(x, motion.getX(), EPSILON);
        assertEquals(z, motion.getZ(), EPSILON);
    }

    private static final class Motion implements BotHorizontalMotion.MotionAccess {
        private double x;
        private double y;
        private double z;

        private Motion(double x, double z) {
            setXZ(x, z);
        }

        @Override
        public double getX() {
            return x;
        }

        @Override
        public double getZ() {
            return z;
        }

        @Override
        public void setXZ(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }
}
