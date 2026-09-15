package com.poppy.practice.reach;

import java.util.UUID;

/** Immutable, already-decayed view of one player's violation levels. */
public final class ViolationSnapshot {
    private final UUID playerId;
    private final double reachVl;
    private final double staleVl;
    private final double invalidEntityVl;
    private final long snapshotNanoTime;

    ViolationSnapshot(UUID playerId, double reachVl, double staleVl,
                      double invalidEntityVl, long snapshotNanoTime) {
        this.playerId = playerId;
        this.reachVl = reachVl;
        this.staleVl = staleVl;
        this.invalidEntityVl = invalidEntityVl;
        this.snapshotNanoTime = snapshotNanoTime;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public double getReachVl() {
        return reachVl;
    }

    public double getStaleVl() {
        return staleVl;
    }

    public double getInvalidEntityVl() {
        return invalidEntityVl;
    }

    public double getTotalVl() {
        return reachVl + staleVl + invalidEntityVl;
    }

    public long getSnapshotNanoTime() {
        return snapshotNanoTime;
    }
}
