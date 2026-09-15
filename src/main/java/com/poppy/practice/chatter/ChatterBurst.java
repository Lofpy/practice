package com.poppy.practice.chatter;

final class ChatterBurst {
    final long timeNs;
    final BurstType type;
    final int targetEntityId;
    final double addedScore;

    ChatterBurst(long timeNs, BurstType type, int targetEntityId, double addedScore) {
        this.timeNs = timeNs;
        this.type = type;
        this.targetEntityId = targetEntityId;
        this.addedScore = addedScore;
    }
}
