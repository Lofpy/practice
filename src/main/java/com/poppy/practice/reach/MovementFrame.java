package com.poppy.practice.reach;

public final class MovementFrame {
    private final long sequence;
    private final long receiveNanoTime;
    private final int serverTick;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final boolean hasPosition;
    private final boolean hasRotation;
    private final boolean onGround;
    private final boolean sneaking;
    private final boolean valid;

    public MovementFrame(long sequence, long receiveNanoTime, int serverTick,
                         double x, double y, double z, float yaw, float pitch,
                         boolean hasPosition, boolean hasRotation, boolean onGround,
                         boolean sneaking, boolean valid) {
        this.sequence = sequence;
        this.receiveNanoTime = receiveNanoTime;
        this.serverTick = serverTick;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.hasPosition = hasPosition;
        this.hasRotation = hasRotation;
        this.onGround = onGround;
        this.sneaking = sneaking;
        this.valid = valid;
    }

    public Vec3 eyePosition() {
        return new Vec3(x, y + (sneaking ? 1.54D : 1.62D), z);
    }

    public boolean hasFiniteCoordinates() {
        return eyePosition().isFinite() && !Float.isNaN(yaw) && !Float.isInfinite(yaw)
                && !Float.isNaN(pitch) && !Float.isInfinite(pitch);
    }

    public long getSequence() { return sequence; }
    public long getReceiveNanoTime() { return receiveNanoTime; }
    public int getServerTick() { return serverTick; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public boolean hasPosition() { return hasPosition; }
    public boolean hasRotation() { return hasRotation; }
    public boolean isOnGround() { return onGround; }
    public boolean isSneaking() { return sneaking; }
    public boolean isValid() { return valid; }
}
