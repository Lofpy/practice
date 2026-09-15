package com.poppy.practice.chatter;

import java.util.List;

final class ChatterDetector {
    DetectionResult classify(PlayerCombatState state, int protocol, int targetEntityId, long now,
                             boolean slowdownEligible, ChatterKbConfig config) {
        decay(state, now, config);
        AttackSample previous = state.attacks.latest();
        long gap = previous == null ? Long.MAX_VALUE : Math.max(0L, now - previous.arrivalNs);
        int frameDelta = previous == null ? Integer.MAX_VALUE
                : safeFrameDelta(state.clientFrameSequence - previous.clientFrame);
        int indexInFrame = previous != null && frameDelta == 0
                ? previous.attackIndexInFrame + 1 : 1;
        BurstType burstType = burstType(state, frameDelta, gap, now, config);
        AttackClassification classification = AttackClassification.PRIMARY;
        if (burstType != BurstType.NONE) {
            double added = burstType.getScore();
            ChatterBurst previousBurst = state.bursts.latest();
            if (previous != null && previous.targetEntityId == targetEntityId) {
                added += 0.25D;
            }
            if (previousBurst != null && now - previousBurst.timeNs <= 1500000000L) {
                added += 1.5D;
            }
            if (state.packetCountSince(now - 2000000L) >= 4) {
                added *= config.networkBatchPenalty;
            }
            state.chatterScore = Math.min(10.0D, state.chatterScore + added);
            state.lastBurstNs = now;
            state.bursts.add(new ChatterBurst(now, burstType, targetEntityId, added));
            ChatterState before = state.chatterState;
            updateState(state, now, config);
            if (state.chatterState == ChatterState.ACTIVE
                    || (before == ChatterState.ACTIVE && state.chatterState == ChatterState.COOLING)) {
                classification = AttackClassification.DUPLICATE;
            } else {
                classification = AttackClassification.UNRESOLVED;
            }
        } else if (isUnarmedBoundaryCandidate(frameDelta, gap, config)) {
            classification = AttackClassification.UNRESOLVED;
        }

        Long windowId = state.kbWindow == null ? null : state.kbWindow.id;
        AttackSample sample = new AttackSample(now, protocol, state.packetSequence,
                state.clientFrameSequence, targetEntityId, gap, frameDelta,
                indexInFrame, state.predictedSprint, slowdownEligible,
                classification, burstType, windowId);
        state.attacks.add(sample);
        return new DetectionResult(sample, state.chatterState);
    }

    private BurstType burstType(PlayerCombatState state, int frameDelta, long gap,
                                long now, ChatterKbConfig config) {
        if (frameDelta == 0) {
            if (gap <= config.sameStrongNs) return BurstType.SAME_FRAME_STRONG;
            if (gap <= config.sameMediumNs) return BurstType.SAME_FRAME_MEDIUM;
            if (gap <= config.sameWeakNs) return BurstType.SAME_FRAME_WEAK;
            return BurstType.NONE;
        }
        if (frameDelta != 1 || !config.boundaryEnabled) return BurstType.NONE;
        boolean armed = state.chatterState == ChatterState.ARMED
                || state.chatterState == ChatterState.ACTIVE
                || state.chatterState == ChatterState.COOLING;
        if (config.boundaryRequireArmed && !armed) return BurstType.NONE;
        if (!hasRecentStrongSameFrame(state, now, config.historyNs)) return BurstType.NONE;
        if (gap <= config.boundaryStrongNs) return BurstType.BOUNDARY_STRONG;
        if (gap <= config.boundaryWeakNs) return BurstType.BOUNDARY_WEAK;
        return BurstType.NONE;
    }

    private boolean hasRecentStrongSameFrame(PlayerCombatState state, long now, long historyNs) {
        for (ChatterBurst burst : state.bursts.snapshot()) {
            if (now - burst.timeNs <= historyNs && burst.type.isSameFrameStrongOrMedium()) {
                return true;
            }
        }
        return false;
    }

    private boolean isUnarmedBoundaryCandidate(int frameDelta, long gap,
                                                ChatterKbConfig config) {
        return config.boundaryEnabled && frameDelta == 1 && gap <= config.boundaryWeakNs;
    }

    void decay(PlayerCombatState state, long now, ChatterKbConfig config) {
        if (state.lastScoreUpdateNs != 0L && now > state.lastScoreUpdateNs) {
            double seconds = (now - state.lastScoreUpdateNs) / 1000000000.0D;
            state.chatterScore = Math.max(0.0D,
                    state.chatterScore - seconds * config.decayPerSecond);
        }
        state.lastScoreUpdateNs = now;
        updateState(state, now, config);
    }

    private void updateState(PlayerCombatState state, long now, ChatterKbConfig config) {
        int recentBursts = 0;
        for (ChatterBurst burst : state.bursts.snapshot()) {
            if (now - burst.timeNs <= config.activationWindowNs) recentBursts++;
        }
        if (state.chatterScore >= config.activationScore
                && recentBursts >= config.activationMinBursts) {
            state.chatterState = ChatterState.ACTIVE;
            return;
        }
        if (state.chatterState == ChatterState.ACTIVE
                && state.lastBurstNs > 0L && now - state.lastBurstNs >= 3000000000L) {
            state.chatterState = ChatterState.COOLING;
            return;
        }
        if (state.lastBurstNs > 0L && state.chatterScore <= config.deactivationScore
                && now - state.lastBurstNs >= config.deactivationNoBurstNs) {
            state.chatterState = ChatterState.CLEAN;
            return;
        }
        if (state.chatterState == ChatterState.CLEAN && hasArmEvidence(state)) {
            state.chatterState = ChatterState.ARMED;
        }
    }

    private boolean hasArmEvidence(PlayerCombatState state) {
        ChatterBurst latest = state.bursts.latest();
        return latest != null && latest.type.isSameFrameStrongOrMedium();
    }

    private int safeFrameDelta(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, value);
    }

    static final class DetectionResult {
        final AttackSample sample;
        final ChatterState state;

        DetectionResult(AttackSample sample, ChatterState state) {
            this.sample = sample;
            this.state = state;
        }
    }
}
