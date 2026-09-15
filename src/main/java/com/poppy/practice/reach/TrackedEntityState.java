package com.poppy.practice.reach;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class TrackedEntityState {
    private final int entityId;
    private final UUID entityUuid;
    private final List<TargetFrame> history = new ArrayList<TargetFrame>();
    private double x;
    private double y;
    private double z;
    private int spawnTick;
    private boolean valid = true;
    private TargetFrame lastConfirmed;
    private TargetFrame latestSent;
    private long teleportEpoch;
    private int lastTeleportTick = Integer.MIN_VALUE;
    private boolean provisional;

    TrackedEntityState(int entityId, UUID entityUuid, double x, double y, double z,
                       int spawnTick) {
        this(entityId, entityUuid, x, y, z, spawnTick, false);
    }

    TrackedEntityState(int entityId, UUID entityUuid, double x, double y, double z,
                       int spawnTick, boolean provisional) {
        this.entityId = entityId;
        this.entityUuid = entityUuid;
        this.x = x;
        this.y = y;
        this.z = z;
        this.spawnTick = spawnTick;
        this.provisional = provisional;
    }

    TargetFrame absolute(long sequence, long now, int tick,
                         double x, double y, double z, boolean teleport) {
        if (teleport) {
            teleportEpoch++;
            lastTeleportTick = tick;
        }
        provisional = false;
        this.x = x;
        this.y = y;
        this.z = z;
        // An absolute spawn/teleport packet is an authoritative replacement
        // for the previous coordinates.  A malformed relative/absolute frame
        // must not poison this entity ID forever; only another finite,
        // in-bounds absolute frame is allowed to restore validity.
        valid = validPosition(x, y, z);
        return frame(sequence, now, tick, teleport);
    }

    TargetFrame relative(long sequence, long now, int tick,
                         double deltaX, double deltaY, double deltaZ) {
        this.x += deltaX;
        this.y += deltaY;
        this.z += deltaZ;
        return frame(sequence, now, tick, false);
    }

    void add(TargetFrame frame, int maximumHistory) {
        history.add(frame);
        if (latestSent == null || frame.getStateSequence()
                >= latestSent.getStateSequence()) latestSent = frame;
        while (history.size() > Math.max(2, maximumHistory)) history.remove(0);
    }

    void confirmThrough(long sequence, long now) {
        for (int index = 0; index < history.size(); index++) {
            TargetFrame frame = history.get(index);
            if (frame.getStateSequence() <= sequence
                    && frame.getStatus() == TargetFrameStatus.SENT_UNCONFIRMED) {
                TargetFrame confirmed = frame.withStatus(
                        TargetFrameStatus.CONFIRMED, now);
                history.set(index, confirmed);
                if (lastConfirmed == null || confirmed.getStateSequence()
                        > lastConfirmed.getStateSequence()) {
                    lastConfirmed = confirmed;
                }
                if (latestSent != null && latestSent.getStateSequence()
                        == confirmed.getStateSequence()) latestSent = confirmed;
            }
        }
        if (latestSent != null && latestSent.getStateSequence() <= sequence
                && latestSent.getStatus() == TargetFrameStatus.SENT_UNCONFIRMED) {
            latestSent = latestSent.withStatus(TargetFrameStatus.CONFIRMED, now);
            if (lastConfirmed == null || latestSent.getStateSequence()
                    > lastConfirmed.getStateSequence()) lastConfirmed = latestSent;
        }
    }

    List<TargetFrame> historySnapshot(long now, long historyLimitMs) {
        List<TargetFrame> result = new ArrayList<TargetFrame>();
        for (TargetFrame frame : history) {
            if (frame.isValid() && frame.ageMillis(now) <= historyLimitMs) result.add(frame);
        }
        // One confirmed anchor per target is retained independently from the
        // ordinary history window. Otherwise delaying Transaction responses
        // just beyond history-retention-ms would erase the only stale anchor.
        insertBySequenceIfMissing(result, lastConfirmed);
        // Preserve the fact that a confirmed state was superseded even after
        // ordinary history eviction; this prevents FakeLag from making the old
        // confirmed position look current again.
        insertBySequenceIfMissing(result, latestSent);
        return result;
    }

    private static void insertBySequenceIfMissing(List<TargetFrame> frames,
                                                  TargetFrame candidate) {
        if (candidate == null || !candidate.isValid()) return;
        for (TargetFrame frame : frames) {
            if (frame.getStateSequence() == candidate.getStateSequence()) return;
        }
        int index = 0;
        while (index < frames.size() && frames.get(index).getStateSequence()
                < candidate.getStateSequence()) index++;
        frames.add(index, candidate);
    }

    TargetFrame latest() {
        return history.isEmpty() ? null : history.get(history.size() - 1);
    }

    private TargetFrame frame(long sequence, long now, int tick, boolean teleport) {
        Aabb box = Aabb.player(x, y, z);
        boolean frameValid = valid && box.isFinite() && validPosition(x, y, z);
        if (!frameValid) valid = false;
        return new TargetFrame(sequence, now, 0L, tick, entityId, entityUuid,
                box, frameValid ? TargetFrameStatus.SENT_UNCONFIRMED
                : TargetFrameStatus.INVALID, teleport, frameValid, teleportEpoch);
    }

    private static boolean validPosition(double x, double y, double z) {
        return !Double.isNaN(x) && !Double.isInfinite(x)
                && !Double.isNaN(y) && !Double.isInfinite(y)
                && !Double.isNaN(z) && !Double.isInfinite(z)
                && Math.abs(x) <= 30_000_000.0D
                && Math.abs(z) <= 30_000_000.0D
                && Math.abs(y) <= 4096.0D;
    }

    int getEntityId() { return entityId; }
    UUID getEntityUuid() { return entityUuid; }
    int getSpawnTick() { return spawnTick; }
    double getX() { return x; }
    double getY() { return y; }
    double getZ() { return z; }
    int getLastTeleportTick() { return lastTeleportTick; }
    boolean isValid() { return valid; }
    boolean isProvisional() { return provisional; }
    void invalidate() { valid = false; }
}
