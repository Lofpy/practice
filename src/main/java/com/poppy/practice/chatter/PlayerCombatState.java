package com.poppy.practice.chatter;

import java.util.List;
import java.util.UUID;

final class PlayerCombatState {
    final UUID playerId;
    volatile String playerName;
    volatile int entityId;
    int clientProtocol = -1;
    long clientFrameSequence;
    long packetSequence;
    long lastMovementNs;
    PositionSample previousPosition;
    PositionSample currentPosition;
    boolean positionDiscontinuity;
    boolean predictedSprint;
    boolean serverSprintState;
    long lastSprintChangeNs;
    final FixedRingBuffer<AttackSample> attacks = new FixedRingBuffer<AttackSample>(64);
    final FixedRingBuffer<ChatterBurst> bursts = new FixedRingBuffer<ChatterBurst>(16);
    final FixedRingBuffer<Long> packetTimes = new FixedRingBuffer<Long>(128);
    double chatterScore;
    ChatterState chatterState = ChatterState.CLEAN;
    long lastScoreUpdateNs;
    long lastBurstNs;
    KnockbackWindow kbWindow;
    int pingMs = -1;
    volatile int heldKnockbackLevel;
    boolean debugEnabled;
    FixedRingBuffer<String> debugRing;
    SkipReason lastSkipReason = SkipReason.NONE;
    volatile long lastCombatDamageNs;
    volatile UUID lastCombatAttackerId;
    int internalVelocityPackets;

    PlayerCombatState(UUID playerId, int entityId) {
        this.playerId = playerId;
        this.entityId = entityId;
    }

    void recordPacket(long now) {
        packetSequence++;
        packetTimes.add(now);
    }

    int packetCountSince(long minimumNs) {
        int count = 0;
        for (Long time : packetTimes.snapshot()) {
            if (time >= minimumNs) count++;
        }
        return count;
    }

    void setDebug(boolean enabled, int ringSize) {
        debugEnabled = enabled;
        debugRing = enabled ? new FixedRingBuffer<String>(ringSize) : null;
    }

    void addDebug(String line) {
        if (debugRing != null) debugRing.add(line);
    }

    List<String> debugSnapshot() {
        return debugRing == null ? java.util.Collections.<String>emptyList() : debugRing.snapshot();
    }

    void closeWindow(WindowCloseReason reason) {
        if (kbWindow != null) kbWindow.closeReason = reason;
        kbWindow = null;
    }

    void resetCombat() {
        clientFrameSequence = 0L;
        clientProtocol = -1;
        packetSequence = 0L;
        lastMovementNs = 0L;
        previousPosition = null;
        currentPosition = null;
        positionDiscontinuity = false;
        predictedSprint = false;
        serverSprintState = false;
        lastSprintChangeNs = 0L;
        attacks.clear();
        bursts.clear();
        packetTimes.clear();
        chatterScore = 0.0D;
        chatterState = ChatterState.CLEAN;
        lastScoreUpdateNs = 0L;
        lastBurstNs = 0L;
        closeWindow(WindowCloseReason.RESET);
        lastSkipReason = SkipReason.NONE;
        lastCombatDamageNs = 0L;
        lastCombatAttackerId = null;
        internalVelocityPackets = 0;
        if (debugRing != null) debugRing.clear();
    }
}
