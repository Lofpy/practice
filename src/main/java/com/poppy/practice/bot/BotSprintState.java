package com.poppy.practice.bot;

/**
 * Tracks player-like sprint input transitions and deliberate W-release ticks.
 * Native sprint state may change after an attack; that does not represent a new
 * START_SPRINTING input and must not re-arm sprint knockback by itself.
 */
public final class BotSprintState {
    public enum Transition {
        NONE,
        START,
        STOP
    }

    private boolean sprinting;
    private boolean movementAllowed = true;
    private int resetTicksRemaining;

    /**
     * Advances once per movement tick, before movement and attacks are applied.
     * Each reset tick releases forward input even if sprint is still requested.
     */
    public Transition advance(boolean forwardSprintRequested) {
        movementAllowed = resetTicksRemaining == 0;
        if (!movementAllowed) {
            resetTicksRemaining--;
        }

        boolean nextSprinting = forwardSprintRequested && movementAllowed;
        if (nextSprinting == sprinting) {
            return Transition.NONE;
        }
        sprinting = nextSprinting;
        return sprinting ? Transition.START : Transition.STOP;
    }

    /**
     * Mirrors the client's sprint release after an eligible attack attempt,
     * even when server-side damage is rejected. This does not release forward
     * input or schedule/extend a landed-hit W reset.
     */
    public Transition stopAfterAttack() {
        if (!sprinting) {
            return Transition.NONE;
        }
        sprinting = false;
        return Transition.STOP;
    }

    /**
     * Schedules a W release beginning next tick. Call only after a landed hit,
     * never after a missed swing or a rejected damage attempt. Pending/active
     * resets cannot be prolonged by further hits, including their final tick.
     * A non-positive duration disables artificial resets.
     */
    public void scheduleReset(int ticks) {
        if (ticks > 0 && resetTicksRemaining == 0 && movementAllowed) {
            resetTicksRemaining = ticks;
        }
    }

    /** Returns the held sprint input, not the entity's mutable native flag. */
    public boolean isSprinting() {
        return sprinting;
    }

    /** Whether forward movement input is allowed during the current tick. */
    public boolean isMovementAllowed() {
        return movementAllowed;
    }
}
