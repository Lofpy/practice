package com.poppy.practice.chatter;

final class SprintStateTracker {
    void update(PlayerCombatState state, boolean sprinting, long now) {
        state.serverSprintState = sprinting;
        state.predictedSprint = sprinting;
        state.lastSprintChangeNs = now;
    }

    void advanceClientFrame(PlayerCombatState state, long now) {
        if (!state.predictedSprint && state.serverSprintState) {
            state.predictedSprint = true;
            state.lastSprintChangeNs = now;
        }
    }

    boolean isSlowdownEligible(PlayerCombatState state, ChatterKbConfig config) {
        return state.predictedSprint
                || (config.knockbackEnchantSupport && state.heldKnockbackLevel > 0);
    }

    void applyLocalAttackReset(PlayerCombatState state, boolean eligible, long now) {
        if (!eligible) return;
        state.predictedSprint = false;
        state.lastSprintChangeNs = now;
    }
}
