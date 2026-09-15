package com.poppy.practice.reach;

import java.util.UUID;

/**
 * Immutable proof that one player attack packet was accepted by ReachGuard.
 * Consumption state is owned by {@link AttackPermitService}; a permit object
 * itself is deliberately immutable so it can safely cross threads.
 */
public final class AttackPermit {
    private final UUID attacker;
    private final UUID target;
    private final long attackSequence;
    private final int serverTick;
    private final long expiresAtNanoTime;

    AttackPermit(UUID attacker, UUID target, long attackSequence,
                 int serverTick, long expiresAtNanoTime) {
        if (attacker == null) {
            throw new IllegalArgumentException("attacker must not be null");
        }
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        this.attacker = attacker;
        this.target = target;
        this.attackSequence = attackSequence;
        this.serverTick = serverTick;
        this.expiresAtNanoTime = expiresAtNanoTime;
    }

    public UUID getAttacker() {
        return attacker;
    }

    public UUID getTarget() {
        return target;
    }

    public long getAttackSequence() {
        return attackSequence;
    }

    public int getServerTick() {
        return serverTick;
    }

    public long getExpiresAtNanoTime() {
        return expiresAtNanoTime;
    }
}
