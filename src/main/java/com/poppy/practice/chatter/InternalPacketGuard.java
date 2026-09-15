package com.poppy.practice.chatter;

final class InternalPacketGuard {
    void mark(PlayerCombatState state) {
        state.internalVelocityPackets++;
    }

    boolean consume(PlayerCombatState state) {
        if (state.internalVelocityPackets <= 0) return false;
        state.internalVelocityPackets--;
        return true;
    }

    void rollback(PlayerCombatState state) {
        state.internalVelocityPackets = Math.max(0, state.internalVelocityPackets - 1);
    }
}
