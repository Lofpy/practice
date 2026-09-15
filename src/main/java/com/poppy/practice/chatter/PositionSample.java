package com.poppy.practice.chatter;

final class PositionSample {
    final double x;
    final double y;
    final double z;
    final boolean onGround;
    final long frame;

    PositionSample(double x, double y, double z, boolean onGround, long frame) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.onGround = onGround;
        this.frame = frame;
    }

    Vec3 deltaFrom(PositionSample previous) {
        if (previous == null) {
            return Vec3.ZERO;
        }
        return new Vec3(x - previous.x, y - previous.y, z - previous.z);
    }
}
