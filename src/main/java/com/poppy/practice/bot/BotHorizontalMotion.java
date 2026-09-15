package com.poppy.practice.bot;

import java.util.Objects;

/**
 * Keeps simulated client movement out of the server motion used by knockback.
 *
 * <p>A real player's movement packets update position without copying their
 * client-side running or knockback momentum into the server's horizontal motion.
 * A bot needs that momentum for its one native movement tick, but must not leave
 * it exposed for the next incoming hit. Vertical motion remains entirely native.
 */
final class BotHorizontalMotion {
    private static final double CLIENT_ATTACK_SLOWDOWN = 0.6D;

    interface MotionAccess {
        double getX();

        double getZ();

        void setXZ(double x, double z);
    }

    private final MotionAccess motion;
    private double clientX;
    private double clientZ;
    private double serverX;
    private double serverZ;
    private boolean clientTickActive;
    private boolean serverAttackActive;

    BotHorizontalMotion(MotionAccess motion) {
        this.motion = Objects.requireNonNull(motion, "motion");
    }

    void runClientTick(Runnable tick) {
        Objects.requireNonNull(tick, "tick");
        if (clientTickActive) {
            throw new IllegalStateException("A bot client tick is already active");
        }
        serverX = motion.getX();
        serverZ = motion.getZ();
        clientTickActive = true;
        motion.setXZ(clientX, clientZ);
        try {
            // Pending velocity packets replace client momentum inside this scope.
            tick.run();
        } finally {
            clientX = motion.getX();
            clientZ = motion.getZ();
            motion.setXZ(serverX, serverZ);
            clientTickActive = false;
        }
    }

    void runServerAttack(Runnable attack) {
        Objects.requireNonNull(attack, "attack");
        if (!clientTickActive || serverAttackActive) {
            attack.run();
            return;
        }
        double currentClientX = motion.getX();
        double currentClientZ = motion.getZ();
        serverAttackActive = true;
        motion.setXZ(serverX, serverZ);
        try {
            attack.run();
        } finally {
            // Keep native server-side attack effects, without applying them to
            // the simulated client a second time.
            serverX = motion.getX();
            serverZ = motion.getZ();
            motion.setXZ(currentClientX, currentClientZ);
            serverAttackActive = false;
        }
    }

    /** Advances the server's retained horizontal motion once per native movement tick. */
    void advanceServerMotion(double horizontalDrag) {
        requireClientMotion("Server motion advancement");
        if (Double.isNaN(horizontalDrag) || Double.isInfinite(horizontalDrag)
                || horizontalDrag < 0.0D) {
            throw new IllegalArgumentException("Horizontal drag must be finite and non-negative");
        }
        // Match EntityLiving's small-motion cutoff followed by native travel drag.
        // Otherwise a prior external impulse or player collision could remain in
        // the server baseline indefinitely after the client/server split.
        serverX = (Math.abs(serverX) < 0.005D ? 0.0D : serverX) * horizontalDrag;
        serverZ = (Math.abs(serverZ) < 0.005D ? 0.0D : serverZ) * horizontalDrag;
    }

    /** Called once per sprint/enchanted client attack, even if NMS rejects damage. */
    void applyClientAttackSlowdown() {
        requireClientMotion("Attack slowdown");
        motion.setXZ(motion.getX() * CLIENT_ATTACK_SLOWDOWN,
                motion.getZ() * CLIENT_ATTACK_SLOWDOWN);
    }

    private void requireClientMotion(String operation) {
        if (!clientTickActive || serverAttackActive) {
            throw new IllegalStateException(operation + " requires client motion");
        }
    }
}
