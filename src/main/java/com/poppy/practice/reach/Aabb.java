package com.poppy.practice.reach;

public final class Aabb {
    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;

    public Aabb(double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public static Aabb player(double x, double y, double z) {
        return new Aabb(x - 0.3D, y, z - 0.3D,
                x + 0.3D, y + 1.8D, z + 0.3D);
    }

    public Aabb expand(double amount) {
        double safe = Math.max(0.0D, amount);
        return new Aabb(minX - safe, minY - safe, minZ - safe,
                maxX + safe, maxY + safe, maxZ + safe);
    }

    public Aabb interpolate(Aabb other, double factor) {
        double t = Math.max(0.0D, Math.min(1.0D, factor));
        return new Aabb(lerp(minX, other.minX, t), lerp(minY, other.minY, t),
                lerp(minZ, other.minZ, t), lerp(maxX, other.maxX, t),
                lerp(maxY, other.maxY, t), lerp(maxZ, other.maxZ, t));
    }

    public Vec3 centerAtFeet() {
        return new Vec3((minX + maxX) * 0.5D, minY, (minZ + maxZ) * 0.5D);
    }

    public boolean isFinite() {
        return finite(minX) && finite(minY) && finite(minZ)
                && finite(maxX) && finite(maxY) && finite(maxZ);
    }

    public double getMinX() { return minX; }
    public double getMinY() { return minY; }
    public double getMinZ() { return minZ; }
    public double getMaxX() { return maxX; }
    public double getMaxY() { return maxY; }
    public double getMaxZ() { return maxZ; }

    private static double lerp(double first, double second, double factor) {
        return first + (second - first) * factor;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
