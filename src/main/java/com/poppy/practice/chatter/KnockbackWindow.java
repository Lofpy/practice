package com.poppy.practice.chatter;

import java.util.UUID;

final class KnockbackWindow {
    final long id;
    final UUID attackerId;
    final long openedNs;
    final long openedClientFrame;
    final Vec3 initialVelocity;
    final PositionSample positionAtOpen;
    final boolean onGroundAtOpen;
    Vec3 baselineVelocity;
    Vec3 lastProjectedVelocity;
    int acceptedSlowdownCount;
    int correctionCount;
    long lastCorrectionNs;
    WindowCloseReason closeReason;

    KnockbackWindow(long id, UUID attackerId, long openedNs, long openedClientFrame,
                    Vec3 initialVelocity, PositionSample positionAtOpen,
                    boolean onGroundAtOpen) {
        this.id = id;
        this.attackerId = attackerId;
        this.openedNs = openedNs;
        this.openedClientFrame = openedClientFrame;
        this.initialVelocity = initialVelocity;
        this.baselineVelocity = initialVelocity;
        this.lastProjectedVelocity = initialVelocity;
        this.positionAtOpen = positionAtOpen;
        this.onGroundAtOpen = onGroundAtOpen;
    }

    int ageFrames(long currentFrame) {
        long age = Math.max(0L, currentFrame - openedClientFrame);
        return age > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) age;
    }
}
