package com.poppy.practice.chatter;

final class WindowSafety {
    boolean isExpired(PlayerCombatState state, KnockbackWindow window,
                      long now, ChatterKbConfig config) {
        return window.ageFrames(state.clientFrameSequence) > config.maxClientFrames
                || now - window.openedNs > config.maxWindowNs;
    }

    SkipReason failure(PlayerCombatState state, KnockbackWindow window, long now,
                       double currentTps, ChatterKbConfig config) {
        if (window == null) return SkipReason.NO_ACTIVE_KB_WINDOW;
        if (isExpired(state, window, now, config)) return SkipReason.WINDOW_EXPIRED;
        if (state.pingMs > config.maxPingMs) return SkipReason.PING_TOO_HIGH;
        if (config.skipOnPositionDiscontinuity && state.positionDiscontinuity) {
            return SkipReason.POSITION_DISCONTINUITY;
        }
        if (currentTps < config.minTps) return SkipReason.LOW_TPS;
        if (window.correctionCount >= config.maxCorrections) return SkipReason.CORRECTION_LIMIT;
        if (window.lastCorrectionNs > 0L
                && now - window.lastCorrectionNs < config.minCorrectionGapNs) {
            return SkipReason.CORRECTION_GAP;
        }
        if (!window.baselineVelocity.isFinite()) return SkipReason.ESTIMATOR_INVALID;
        return SkipReason.NONE;
    }
}
