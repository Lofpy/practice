package com.poppy.practice.reach;

public final class ReachGeometry {
    private ReachGeometry() {
    }

    public static double distancePointToAabb(Vec3 point, Aabb box) {
        if (point == null || box == null || !point.isFinite() || !box.isFinite()) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = Math.max(Math.max(box.getMinX() - point.getX(), 0.0D),
                point.getX() - box.getMaxX());
        double dy = Math.max(Math.max(box.getMinY() - point.getY(), 0.0D),
                point.getY() - box.getMaxY());
        double dz = Math.max(Math.max(box.getMinZ() - point.getZ(), 0.0D),
                point.getZ() - box.getMaxZ());
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
