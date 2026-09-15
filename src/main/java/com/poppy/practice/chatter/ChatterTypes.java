package com.poppy.practice.chatter;

enum AttackClassification {
    PRIMARY,
    DUPLICATE,
    UNRESOLVED
}

enum BurstType {
    NONE(0.0D),
    SAME_FRAME_STRONG(3.0D),
    SAME_FRAME_MEDIUM(2.5D),
    SAME_FRAME_WEAK(1.5D),
    BOUNDARY_STRONG(1.25D),
    BOUNDARY_WEAK(0.75D);

    private final double score;

    BurstType(double score) {
        this.score = score;
    }

    double getScore() {
        return score;
    }

    boolean isSameFrameStrongOrMedium() {
        return this == SAME_FRAME_STRONG || this == SAME_FRAME_MEDIUM;
    }
}

enum ChatterState {
    CLEAN,
    ARMED,
    ACTIVE,
    COOLING
}

enum SkipReason {
    NONE,
    MODE_OFF,
    DETECT_ONLY,
    NOT_ACTIVE,
    NOT_DUPLICATE,
    SLOWDOWN_NOT_ELIGIBLE,
    NO_ACTIVE_KB_WINDOW,
    WINDOW_EXPIRED,
    PING_TOO_HIGH,
    POSITION_DISCONTINUITY,
    DIRECTION_MISMATCH,
    CORRECTION_LIMIT,
    CORRECTION_GAP,
    LOW_TPS,
    INTERNAL_PACKET,
    UNSUPPORTED_PROTOCOL,
    ESTIMATOR_INVALID,
    NOT_COMBAT_VELOCITY
}

enum WindowCloseReason {
    EXPIRED,
    SUPERSEDED,
    RESET,
    CORRECTION_LIMIT,
    INVALID
}
