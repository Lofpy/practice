package com.poppy.practice.reach;

import java.util.UUID;

public final class TargetFrame {
    private final long stateSequence;
    private final long sentNanoTime;
    private final long confirmedNanoTime;
    private final int serverTick;
    private final int entityId;
    private final UUID entityUuid;
    private final Aabb boundingBox;
    private final TargetFrameStatus status;
    private final boolean teleport;
    private final boolean valid;
    private final long teleportEpoch;

    public TargetFrame(long stateSequence, long sentNanoTime, long confirmedNanoTime,
                       int serverTick, int entityId, UUID entityUuid, Aabb boundingBox,
                       TargetFrameStatus status, boolean teleport, boolean valid) {
        this(stateSequence, sentNanoTime, confirmedNanoTime, serverTick,
                entityId, entityUuid, boundingBox, status, teleport, valid, 0L);
    }

    public TargetFrame(long stateSequence, long sentNanoTime, long confirmedNanoTime,
                       int serverTick, int entityId, UUID entityUuid, Aabb boundingBox,
                       TargetFrameStatus status, boolean teleport, boolean valid,
                       long teleportEpoch) {
        this.stateSequence = stateSequence;
        this.sentNanoTime = sentNanoTime;
        this.confirmedNanoTime = confirmedNanoTime;
        this.serverTick = serverTick;
        this.entityId = entityId;
        this.entityUuid = entityUuid;
        this.boundingBox = boundingBox;
        this.status = status;
        this.teleport = teleport;
        this.valid = valid;
        this.teleportEpoch = teleportEpoch;
    }

    public TargetFrame withStatus(TargetFrameStatus replacement, long confirmedAt) {
        return new TargetFrame(stateSequence, sentNanoTime, confirmedAt, serverTick,
                entityId, entityUuid, boundingBox, replacement, teleport, valid,
                teleportEpoch);
    }

    public TargetFrame interpolate(TargetFrame other, double factor, long sequence) {
        return new TargetFrame(sequence, Math.max(sentNanoTime, other.sentNanoTime),
                Math.max(confirmedNanoTime, other.confirmedNanoTime),
                Math.max(serverTick, other.serverTick), entityId, entityUuid,
                boundingBox.interpolate(other.boundingBox, factor),
                TargetFrameStatus.INTERPOLATED, false, valid && other.valid,
                teleportEpoch);
    }

    public long ageMillis(long now) {
        return Math.max(0L, (now - sentNanoTime) / 1_000_000L);
    }

    public long getStateSequence() { return stateSequence; }
    public long getSentNanoTime() { return sentNanoTime; }
    public long getConfirmedNanoTime() { return confirmedNanoTime; }
    public int getServerTick() { return serverTick; }
    public int getEntityId() { return entityId; }
    public UUID getEntityUuid() { return entityUuid; }
    public Aabb getBoundingBox() { return boundingBox; }
    public TargetFrameStatus getStatus() { return status; }
    public boolean isTeleport() { return teleport; }
    public boolean isValid() { return valid; }
    public long getTeleportEpoch() { return teleportEpoch; }
}
