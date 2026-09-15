package com.poppy.practice.reach;

public enum ReachDecision {
    ALLOW,
    ALLOW_UNVERIFIED,
    FLAG,
    CANCEL_REACH,
    CANCEL_STALE_ATTACK,
    CANCEL_INVALID_ENTITY,
    BYPASS;

    public boolean isCancelled() {
        return this == CANCEL_REACH || this == CANCEL_STALE_ATTACK
                || this == CANCEL_INVALID_ENTITY;
    }

    public boolean isViolation() {
        return this == FLAG || isCancelled();
    }
}
