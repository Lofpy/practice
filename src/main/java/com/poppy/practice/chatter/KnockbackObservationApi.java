package com.poppy.practice.chatter;

import java.util.UUID;

public interface KnockbackObservationApi {
    void recordCombatKnockback(UUID victimId, UUID attackerId, Vec3 finalVelocity,
                               KnockbackCause cause, long serverTick, long nanoTime);

    enum KnockbackCause {
        MELEE,
        PROJECTILE,
        CUSTOM
    }
}
