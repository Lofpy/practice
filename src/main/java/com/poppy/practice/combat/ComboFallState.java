package com.poppy.practice.combat;

/**
 * Per-participant Combo height-limit state, independent of Bukkit and entity physics.
 * A grounded sample clears a preceding airborne sequence, but preserves newly armed
 * knockback until the entity has actually left the ground.
 */
public final class ComboFallState {
    private static final double DEFAULT_FALL_SPEED = 0.08D;

    private double groundY;
    private boolean armed;
    private boolean airborneSeen;
    private boolean falling;
    private double fallVelocity;

    public ComboFallState(double initialGroundY) {
        reset(initialGroundY);
    }

    /** Marks a qualifying knockback; ordinary jumps do not activate the height limit. */
    public void arm() {
        armed = true;
    }

    /**
     * Samples feet height relative to the last standing height. Once the threshold is
     * reached, falling remains latched until landing, even after descending below it.
     * The default descent is a constant 0.08 blocks per tick without gravity acceleration.
     * A zero height disables and clears the current airborne sequence.
     */
    public void sample(double feetY, boolean grounded, double height, long tick) {
        sample(feetY, grounded, height, DEFAULT_FALL_SPEED, tick);
    }

    /** Samples using the currently configured constant downward speed, including live edits. */
    public void sample(double feetY, boolean grounded, double height, double fallSpeed, long tick) {
        requireFinite(feetY, "Feet height");
        requireFinite(height, "Fall threshold");
        requireFinite(fallSpeed, "Fall speed");
        if (fallSpeed <= 0.0D) {
            throw new IllegalArgumentException("Fall speed must be positive");
        }
        if (height < 0.0D) {
            throw new IllegalArgumentException("Fall threshold must not be negative");
        }
        if (height == 0.0D) {
            if (grounded) {
                groundY = feetY;
            }
            clearAirborneState();
            return;
        }
        if (grounded) {
            if (armed && !airborneSeen && !falling) {
                // Damage and velocity callbacks can precede the first movement packet.
                // This is still the takeoff ground, not a landing after that knockback.
                groundY = feetY;
            } else {
                reset(feetY);
            }
            return;
        }
        airborneSeen = true;
        if (!falling) {
            if (armed && feetY - groundY >= height) {
                falling = true;
                fallVelocity = -fallSpeed;
            }
            return;
        }
        fallVelocity = -fallSpeed;
    }

    public boolean isFalling() {
        return falling;
    }

    /** Replaces vertical motion with the slow-fall speed, including faster downward motion. */
    public double limitVertical(double proposed) {
        requireFinite(proposed, "Vertical velocity");
        return falling ? fallVelocity : proposed;
    }

    /** Clears all state after a teleport, landing, or match lifecycle transition. */
    public void reset(double groundY) {
        requireFinite(groundY, "Ground height");
        this.groundY = groundY;
        clearAirborneState();
    }

    /** Isolated snapshot for transactional live configuration updates. */
    public ComboFallState copy() {
        ComboFallState copy = new ComboFallState(groundY);
        copy.armed = armed;
        copy.airborneSeen = airborneSeen;
        copy.falling = falling;
        copy.fallVelocity = fallVelocity;
        return copy;
    }

    private void clearAirborneState() {
        armed = false;
        airborneSeen = false;
        falling = false;
        fallVelocity = 0.0D;
    }

    private static void requireFinite(double value, String label) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }
}
