package com.poppy.practice.reach;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ReachGeometryTest {
    private static final double ERROR = 0.0000001D;

    @Test
    public void pointInsideAabbHasZeroDistance() {
        Aabb box = new Aabb(0.0D, 0.0D, 0.0D,
                1.0D, 1.0D, 1.0D);

        assertEquals(0.0D, ReachGeometry.distancePointToAabb(
                new Vec3(0.5D, 0.5D, 0.5D), box), ERROR);
    }

    @Test
    public void xAxisDistanceUsesNearestFace() {
        Aabb box = new Aabb(0.0D, 0.0D, 0.0D,
                1.0D, 1.0D, 1.0D);

        assertEquals(3.0D, ReachGeometry.distancePointToAabb(
                new Vec3(-3.0D, 0.5D, 0.5D), box), ERROR);
    }

    @Test
    public void diagonalDistanceIsEuclidean() {
        Aabb box = new Aabb(0.0D, 0.0D, 0.0D,
                1.0D, 1.0D, 1.0D);

        assertEquals(5.0D, ReachGeometry.distancePointToAabb(
                new Vec3(-3.0D, 0.5D, -4.0D), box), ERROR);
    }

    @Test
    public void expansionIsAppliedToEveryFace() {
        Aabb expanded = new Aabb(0.0D, 0.0D, 0.0D,
                1.0D, 1.0D, 1.0D).expand(0.10D);

        assertEquals(2.90D, ReachGeometry.distancePointToAabb(
                new Vec3(-3.0D, 0.5D, 0.5D), expanded), ERROR);
        assertEquals(-0.10D, expanded.getMinY(), ERROR);
        assertEquals(1.10D, expanded.getMaxY(), ERROR);
    }

    @Test
    public void nonFinitePointOrBoxIsRejected() {
        Aabb valid = Aabb.player(0.0D, 0.0D, 0.0D);
        Aabb invalid = new Aabb(Double.NaN, 0.0D, 0.0D,
                1.0D, 1.0D, 1.0D);

        assertTrue(Double.isInfinite(ReachGeometry.distancePointToAabb(
                new Vec3(Double.POSITIVE_INFINITY, 0.0D, 0.0D), valid)));
        assertTrue(Double.isInfinite(ReachGeometry.distancePointToAabb(
                new Vec3(0.0D, 0.0D, 0.0D), invalid)));
    }

    @Test
    public void playerBoxAndEyeHeightsMatchOneEightGeometry() {
        Aabb box = Aabb.player(4.0D, 10.0D, -2.0D);
        MovementFrame standing = frame(false);
        MovementFrame sneaking = frame(true);

        assertEquals(3.70D, box.getMinX(), ERROR);
        assertEquals(4.30D, box.getMaxX(), ERROR);
        assertEquals(11.80D, box.getMaxY(), ERROR);
        assertEquals(1.62D, standing.eyePosition().getY(), ERROR);
        assertEquals(1.54D, sneaking.eyePosition().getY(), ERROR);
    }

    private static MovementFrame frame(boolean sneaking) {
        return new MovementFrame(1L, 0L, 0, 0.0D, 0.0D, 0.0D,
                0.0F, 0.0F, true, true, true, sneaking, true);
    }
}
