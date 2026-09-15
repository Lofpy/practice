package com.poppy.practice.bot;

/** Certification-only movement inputs; never changes velocity, reach or damage. */
final class BotCertificationMovement {
    private static final int COMBO_WINDOW_TICKS = 8;
    private static final double PREDICTION_TICKS = 2.0D;
    private static final double MAXIMUM_PREDICTED_CHANGE = 0.45D;
    private static final double BACKWARD_RELEASE_MARGIN = 0.15D;

    private final double preferredDistance;
    private final double retreatDistance;
    private int comboTicks;
    private boolean backingOff;

    BotCertificationMovement(BotSettings settings) {
        preferredDistance = settings.getPreferredDistance();
        retreatDistance = settings.getRetreatDistance();
    }

    void advanceTick() {
        if (comboTicks > 0) {
            comboTicks--;
        }
    }

    void recordLandedHit() {
        comboTicks = COMBO_WINDOW_TICKS;
    }

    void suspend() {
        backingOff = false;
        comboTicks = 0;
    }

    /** relativeSpeed is positive when the horizontal gap is opening. */
    float forwardInput(double distance, double relativeSpeed,
                       boolean targetFacingAway, boolean takingKnockback) {
        // Keep chasing a fleeing opponent and counter received KB with W.
        // In particular, never add a scripted retreat to received knockback.
        if (targetFacingAway || takingKnockback) {
            backingOff = false;
            return 1.0F;
        }
        if (Double.isNaN(relativeSpeed) || Double.isInfinite(relativeSpeed)) {
            relativeSpeed = 0.0D;
        }
        double predictedChange = Math.max(-MAXIMUM_PREDICTED_CHANGE,
                Math.min(MAXIMUM_PREDICTED_CHANGE, relativeSpeed * PREDICTION_TICKS));
        double projectedDistance = distance + predictedChange;
        if (distance >= preferredDistance + 0.20D
                || projectedDistance >= preferredDistance + 0.10D) {
            backingOff = false;
            return 1.0F;
        }

        // Hysteresis keeps the S input from flickering at the spacing boundary.
        // Back off only when needed, never for a fixed duration after every hit.
        if (projectedDistance < retreatDistance) {
            backingOff = true;
        } else if (projectedDistance >= retreatDistance + BACKWARD_RELEASE_MARGIN) {
            backingOff = false;
        }
        if (backingOff) {
            return -0.62F;
        }
        if (comboTicks > 0 && projectedDistance < preferredDistance - 0.05D) {
            return 0.0F;
        }
        return projectedDistance > preferredDistance ? 1.0F : 0.28F;
    }

    float strafeInput(double configuredInput, int direction, boolean targetFacingAway,
                      float forward) {
        // During a successful combo, spend movement on spacing along the line
        // to the target. Keep a small amount of strafe between engagements.
        return targetFacingAway || comboTicks > 0 || forward < 0.0F
                ? 0.0F : (float) (configuredInput * direction);
    }
}
