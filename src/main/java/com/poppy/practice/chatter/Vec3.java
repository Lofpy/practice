package com.poppy.practice.chatter;

public final class Vec3 {
    public static final Vec3 ZERO = new Vec3(0.0D, 0.0D, 0.0D);

    private final double x;
    private final double y;
    private final double z;

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public double horizontalLength() {
        return Math.sqrt(x * x + z * z);
    }

    public double horizontalDot(Vec3 other) {
        return x * other.x + z * other.z;
    }

    public Vec3 multiply(double value) {
        return new Vec3(x * value, y * value, z * value);
    }

    public Vec3 multiplyHorizontal(double value) {
        return new Vec3(x * value, y, z * value);
    }

    public Vec3 withY(double value) {
        return new Vec3(x, value, z);
    }

    public boolean isFinite() {
        return finite(x) && finite(y) && finite(z)
                && Math.abs(x) <= 3.9D && Math.abs(y) <= 3.9D && Math.abs(z) <= 3.9D;
    }

    public double horizontalAngleDegrees(Vec3 other) {
        double first = horizontalLength();
        double second = other.horizontalLength();
        if (first < 1.0E-9D || second < 1.0E-9D) {
            return 0.0D;
        }
        double cosine = horizontalDot(other) / (first * second);
        cosine = Math.max(-1.0D, Math.min(1.0D, cosine));
        return Math.toDegrees(Math.acos(cosine));
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    @Override
    public String toString() {
        return '[' + decimal(x) + ',' + decimal(y) + ',' + decimal(z) + ']';
    }

    private static String decimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }
}
