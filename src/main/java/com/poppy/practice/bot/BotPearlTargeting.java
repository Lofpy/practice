package com.poppy.practice.bot;

final class BotPearlTargeting {
    private static final double FACING_AWAY_DEGREES = 100.0D;

    private BotPearlTargeting() {
    }

    static boolean isFacingAway(float playerYaw, double playerX, double playerZ,
                                double botX, double botZ) {
        float yawToBot = BotMovement.yawTo(botX - playerX, botZ - playerZ);
        return BotMovement.angleDifference(playerYaw, yawToBot) >= FACING_AWAY_DEGREES;
    }

    static boolean becameAirborne(boolean wasOnGround, boolean isOnGround) {
        return wasOnGround && !isOnGround;
    }

    static Target beside(double playerX, double playerZ, float playerYaw,
                         int sideDirection, double distance) {
        double radians = Math.toRadians(playerYaw);
        double rightX = Math.cos(radians);
        double rightZ = Math.sin(radians);
        int side = sideDirection < 0 ? -1 : 1;
        return new Target(playerX + rightX * distance * side,
                playerZ + rightZ * distance * side);
    }

    static boolean reachedOrPassed(double pearlX, double pearlZ,
                                   double motionX, double motionZ,
                                   double targetX, double targetZ,
                                   double tolerance) {
        double remainingX = targetX - pearlX;
        double remainingZ = targetZ - pearlZ;
        double toleranceSquared = Math.max(0.0D, tolerance) * Math.max(0.0D, tolerance);
        if (remainingX * remainingX + remainingZ * remainingZ <= toleranceSquared) {
            return true;
        }
        double motionSquared = motionX * motionX + motionZ * motionZ;
        return motionSquared > 1.0E-8D
                && remainingX * motionX + remainingZ * motionZ <= 0.0D;
    }

    static int cooldownTicks(int seconds) {
        return Math.max(0, seconds) * 20;
    }

    static final class Target {
        private final double x;
        private final double z;

        private Target(double x, double z) {
            this.x = x;
            this.z = z;
        }

        double getX() { return x; }
        double getZ() { return z; }
    }
}
