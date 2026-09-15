package com.poppy.practice.bot;

public final class BotMovement {
    private BotMovement() {
    }

    public static float yawTo(double deltaX, double deltaZ) {
        return (float) Math.toDegrees(Math.atan2(-deltaX, deltaZ));
    }

    public static float strafeInput(boolean enabled, boolean targetFacingAway,
                                    double configuredInput, int direction) {
        // Disable only deliberate sideways input; native knockback is unaffected.
        return enabled && !targetFacingAway ? (float) (configuredInput * direction) : 0.0F;
    }

    public static float pitchTo(double deltaY, double horizontalDistance) {
        return (float) -Math.toDegrees(Math.atan2(deltaY, horizontalDistance));
    }

    public static float rotateToward(float current, float target, double maximumChange) {
        double difference = wrapDegrees(target - current);
        double limited = Math.max(-maximumChange, Math.min(maximumChange, difference));
        return (float) wrapDegrees(current + limited);
    }

    public static double angleDifference(float first, float second) {
        return Math.abs(wrapDegrees(first - second));
    }

    public static float oppositeYaw(float yaw) {
        return (float) wrapDegrees(yaw + 180.0D);
    }

    public static float forwardInput(double distance, double preferredDistance, double retreatDistance) {
        if (distance < retreatDistance) {
            return -0.62F;
        }
        if (distance > preferredDistance) {
            return 1.0F;
        }
        return 0.28F;
    }

    public static float forwardInput(double distance, double preferredDistance,
                                     double retreatDistance, boolean takingKnockback) {
        // Counter a hit by holding W, not by cancelling the received velocity.
        // Normal spacing must not turn into weak input or retreat during a combo.
        return takingKnockback ? 1.0F : forwardInput(distance, preferredDistance, retreatDistance);
    }

    static float itemUseInput(float input, boolean usingItem) {
        return usingItem ? input * 0.2F : input;
    }

    private static double wrapDegrees(double degrees) {
        degrees %= 360.0D;
        if (degrees >= 180.0D) {
            degrees -= 360.0D;
        }
        if (degrees < -180.0D) {
            degrees += 360.0D;
        }
        return degrees;
    }
}
