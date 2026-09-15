package com.poppy.practice.reach;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshot of the information needed to reproduce one ReachGuard
 * decision.  The builder keeps packet-path call sites readable while the
 * finished record remains safe to hand to the asynchronous logger.
 */
public final class EvidenceRecord {
    private final OffsetDateTime timestamp;
    private final UUID attackerUuid;
    private final String attackerName;
    private final UUID targetUuid;
    private final String targetName;
    private final int protocol;
    private final ReachGuardMode mode;
    private final ReachGuardMode configuredMode;
    private final ReachDecision decision;
    private final String reason;
    private final Reliability reliability;
    private final double measuredReach;
    private final double allowedReach;
    private final double excess;
    private final double violationLevel;
    private final double staleViolationLevel;
    private final double invalidEntityViolationLevel;
    private final int pingMs;
    private final int jitterMs;
    private final double tps;
    private final double tickDurationMs;
    private final long serverTick;
    private final int attackerCandidateCount;
    private final int targetCandidateCount;
    private final long attackSequence;
    private final long attackerFrameSequence;
    private final long targetFrameSequence;
    private final long snapshotAgeMs;
    private final int targetEntityId;
    private final double attackerX;
    private final double attackerY;
    private final double attackerZ;
    private final double attackerEyeX;
    private final double attackerEyeY;
    private final double attackerEyeZ;
    private final boolean attackerSneaking;
    private final double targetMinX;
    private final double targetMinY;
    private final double targetMinZ;
    private final double targetMaxX;
    private final double targetMaxY;
    private final double targetMaxZ;
    private final double selectedExpandedMinX;
    private final double selectedExpandedMinY;
    private final double selectedExpandedMinZ;
    private final double selectedExpandedMaxX;
    private final double selectedExpandedMaxY;
    private final double selectedExpandedMaxZ;
    private final double baseReach;
    private final double hitboxExpansion;
    private final double geometryEpsilon;
    private final double totalExpansion;
    private final double vlExcess;
    private final double vlAdded;
    private final double violationBase;
    private final double excessMultiplier;
    private final double maxExcessAddition;
    private final List<AttackerCandidateEvidence> attackerCandidates;
    private final List<TargetCandidateEvidence> targetCandidates;
    private final TargetCandidateEvidence newestTargetState;
    private final TargetCandidateEvidence staleAnchor;
    private final TargetCandidateEvidence currentRuntimeCandidate;
    private final boolean grace;
    private final boolean targetTeleportGrace;
    private final boolean unsupportedProtocol;
    private final boolean heartbeatStalled;
    private final boolean recentKnockback;
    private final List<String> unverifiedExceptions;
    private final long transactionRttMs;
    private final long pendingSyncDelayMs;
    private final long droppedEvidenceCount;

    private EvidenceRecord(Builder builder) {
        timestamp = Objects.requireNonNull(builder.timestamp, "timestamp");
        attackerUuid = builder.attackerUuid;
        attackerName = builder.attackerName;
        targetUuid = builder.targetUuid;
        targetName = builder.targetName;
        protocol = builder.protocol;
        mode = builder.mode;
        configuredMode = builder.configuredMode;
        decision = builder.decision;
        reason = builder.reason;
        reliability = builder.reliability;
        measuredReach = builder.measuredReach;
        allowedReach = builder.allowedReach;
        excess = builder.excess;
        violationLevel = builder.violationLevel;
        staleViolationLevel = builder.staleViolationLevel;
        invalidEntityViolationLevel = builder.invalidEntityViolationLevel;
        pingMs = builder.pingMs;
        jitterMs = builder.jitterMs;
        tps = builder.tps;
        tickDurationMs = builder.tickDurationMs;
        serverTick = builder.serverTick;
        attackerCandidateCount = builder.attackerCandidateCount;
        targetCandidateCount = builder.targetCandidateCount;
        attackSequence = builder.attackSequence;
        attackerFrameSequence = builder.attackerFrameSequence;
        targetFrameSequence = builder.targetFrameSequence;
        snapshotAgeMs = builder.snapshotAgeMs;
        targetEntityId = builder.targetEntityId;
        attackerX = builder.attackerX;
        attackerY = builder.attackerY;
        attackerZ = builder.attackerZ;
        attackerEyeX = builder.attackerEyeX;
        attackerEyeY = builder.attackerEyeY;
        attackerEyeZ = builder.attackerEyeZ;
        attackerSneaking = builder.attackerSneaking;
        targetMinX = builder.targetMinX;
        targetMinY = builder.targetMinY;
        targetMinZ = builder.targetMinZ;
        targetMaxX = builder.targetMaxX;
        targetMaxY = builder.targetMaxY;
        targetMaxZ = builder.targetMaxZ;
        selectedExpandedMinX = builder.selectedExpandedMinX;
        selectedExpandedMinY = builder.selectedExpandedMinY;
        selectedExpandedMinZ = builder.selectedExpandedMinZ;
        selectedExpandedMaxX = builder.selectedExpandedMaxX;
        selectedExpandedMaxY = builder.selectedExpandedMaxY;
        selectedExpandedMaxZ = builder.selectedExpandedMaxZ;
        baseReach = builder.baseReach;
        hitboxExpansion = builder.hitboxExpansion;
        geometryEpsilon = builder.geometryEpsilon;
        totalExpansion = builder.totalExpansion;
        vlExcess = builder.vlExcess;
        vlAdded = builder.vlAdded;
        violationBase = builder.violationBase;
        excessMultiplier = builder.excessMultiplier;
        maxExcessAddition = builder.maxExcessAddition;
        attackerCandidates = Collections.unmodifiableList(
                new ArrayList<AttackerCandidateEvidence>(builder.attackerCandidates));
        targetCandidates = Collections.unmodifiableList(
                new ArrayList<TargetCandidateEvidence>(builder.targetCandidates));
        newestTargetState = builder.newestTargetState;
        staleAnchor = builder.staleAnchor;
        currentRuntimeCandidate = builder.currentRuntimeCandidate;
        grace = builder.grace;
        targetTeleportGrace = builder.targetTeleportGrace;
        unsupportedProtocol = builder.unsupportedProtocol;
        heartbeatStalled = builder.heartbeatStalled;
        recentKnockback = builder.recentKnockback;
        unverifiedExceptions = Collections.unmodifiableList(
                new ArrayList<String>(builder.unverifiedExceptions));
        transactionRttMs = builder.transactionRttMs;
        pendingSyncDelayMs = builder.pendingSyncDelayMs;
        droppedEvidenceCount = builder.droppedEvidenceCount;
    }

    public static Builder builder() {
        return new Builder(OffsetDateTime.now());
    }

    public static Builder builder(OffsetDateTime timestamp) {
        return new Builder(timestamp);
    }

    public OffsetDateTime getTimestamp() { return timestamp; }
    public UUID getAttackerUuid() { return attackerUuid; }
    public String getAttackerName() { return attackerName; }
    public UUID getTargetUuid() { return targetUuid; }
    public String getTargetName() { return targetName; }
    public int getProtocol() { return protocol; }
    public ReachGuardMode getMode() { return mode; }
    public ReachGuardMode getConfiguredMode() { return configuredMode; }
    public ReachDecision getDecision() { return decision; }
    public String getReason() { return reason; }
    public Reliability getReliability() { return reliability; }
    public double getMeasuredReach() { return measuredReach; }
    public double getAllowedReach() { return allowedReach; }
    public double getExcess() { return excess; }
    public double getViolationLevel() { return violationLevel; }
    public double getStaleViolationLevel() { return staleViolationLevel; }
    public double getInvalidEntityViolationLevel() { return invalidEntityViolationLevel; }
    public int getPingMs() { return pingMs; }
    public int getJitterMs() { return jitterMs; }
    public double getTps() { return tps; }
    public double getTickDurationMs() { return tickDurationMs; }
    public long getServerTick() { return serverTick; }
    public int getAttackerCandidateCount() { return attackerCandidateCount; }
    public int getTargetCandidateCount() { return targetCandidateCount; }
    public long getAttackSequence() { return attackSequence; }
    public long getAttackerFrameSequence() { return attackerFrameSequence; }
    public long getTargetFrameSequence() { return targetFrameSequence; }
    public long getSnapshotAgeMs() { return snapshotAgeMs; }
    public int getTargetEntityId() { return targetEntityId; }
    public double getAttackerX() { return attackerX; }
    public double getAttackerY() { return attackerY; }
    public double getAttackerZ() { return attackerZ; }
    public double getAttackerEyeX() { return attackerEyeX; }
    public double getAttackerEyeY() { return attackerEyeY; }
    public double getAttackerEyeZ() { return attackerEyeZ; }
    public boolean isAttackerSneaking() { return attackerSneaking; }
    public double getTargetMinX() { return targetMinX; }
    public double getTargetMinY() { return targetMinY; }
    public double getTargetMinZ() { return targetMinZ; }
    public double getTargetMaxX() { return targetMaxX; }
    public double getTargetMaxY() { return targetMaxY; }
    public double getTargetMaxZ() { return targetMaxZ; }
    public double getSelectedExpandedMinX() { return selectedExpandedMinX; }
    public double getSelectedExpandedMinY() { return selectedExpandedMinY; }
    public double getSelectedExpandedMinZ() { return selectedExpandedMinZ; }
    public double getSelectedExpandedMaxX() { return selectedExpandedMaxX; }
    public double getSelectedExpandedMaxY() { return selectedExpandedMaxY; }
    public double getSelectedExpandedMaxZ() { return selectedExpandedMaxZ; }
    public double getBaseReach() { return baseReach; }
    public double getHitboxExpansion() { return hitboxExpansion; }
    public double getGeometryEpsilon() { return geometryEpsilon; }
    public double getTotalExpansion() { return totalExpansion; }
    public double getVlExcess() { return vlExcess; }
    public double getVlAdded() { return vlAdded; }
    public double getViolationBase() { return violationBase; }
    public double getExcessMultiplier() { return excessMultiplier; }
    public double getMaxExcessAddition() { return maxExcessAddition; }
    public List<AttackerCandidateEvidence> getAttackerCandidates() {
        return attackerCandidates;
    }
    public List<TargetCandidateEvidence> getTargetCandidates() { return targetCandidates; }
    public TargetCandidateEvidence getNewestTargetState() { return newestTargetState; }
    public TargetCandidateEvidence getStaleAnchor() { return staleAnchor; }
    public TargetCandidateEvidence getCurrentRuntimeCandidate() {
        return currentRuntimeCandidate;
    }
    public boolean isGrace() { return grace; }
    public boolean isTargetTeleportGrace() { return targetTeleportGrace; }
    public boolean isUnsupportedProtocol() { return unsupportedProtocol; }
    public boolean isHeartbeatStalled() { return heartbeatStalled; }
    public boolean hasRecentKnockback() { return recentKnockback; }
    public List<String> getUnverifiedExceptions() { return unverifiedExceptions; }
    public long getTransactionRttMs() { return transactionRttMs; }
    public long getPendingSyncDelayMs() { return pendingSyncDelayMs; }
    public long getDroppedEvidenceCount() { return droppedEvidenceCount; }

    public boolean isCancelled() {
        return decision != null && decision.isCancelled();
    }

    /** Immutable scalar copy of one attacker movement state considered by the validator. */
    public static final class AttackerCandidateEvidence {
        private final long sequence;
        private final long ageMs;
        private final int serverTick;
        private final double x;
        private final double y;
        private final double z;
        private final double eyeX;
        private final double eyeY;
        private final double eyeZ;
        private final float yaw;
        private final float pitch;
        private final boolean hasPosition;
        private final boolean hasRotation;
        private final boolean onGround;
        private final boolean sneaking;
        private final boolean valid;

        private AttackerCandidateEvidence(MovementFrame frame, long nowNanoTime) {
            sequence = frame.getSequence();
            ageMs = Math.max(0L,
                    (nowNanoTime - frame.getReceiveNanoTime()) / 1_000_000L);
            serverTick = frame.getServerTick();
            x = frame.getX();
            y = frame.getY();
            z = frame.getZ();
            Vec3 eye = frame.eyePosition();
            eyeX = eye.getX();
            eyeY = eye.getY();
            eyeZ = eye.getZ();
            yaw = frame.getYaw();
            pitch = frame.getPitch();
            hasPosition = frame.hasPosition();
            hasRotation = frame.hasRotation();
            onGround = frame.isOnGround();
            sneaking = frame.isSneaking();
            valid = frame.isValid();
        }

        public long getSequence() { return sequence; }
        public long getAgeMs() { return ageMs; }
        public int getServerTick() { return serverTick; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public double getEyeX() { return eyeX; }
        public double getEyeY() { return eyeY; }
        public double getEyeZ() { return eyeZ; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
        public boolean hasPosition() { return hasPosition; }
        public boolean hasRotation() { return hasRotation; }
        public boolean isOnGround() { return onGround; }
        public boolean isSneaking() { return sneaking; }
        public boolean isValid() { return valid; }
    }

    /** Immutable scalar copy of one target state used by the validator. */
    public static final class TargetCandidateEvidence {
        private static final long CURRENT_RUNTIME_SEQUENCE = -10L;

        private final long sequence;
        private final String status;
        private final long ageMs;
        private final int serverTick;
        private final int entityId;
        private final UUID entityUuid;
        private final double minX;
        private final double minY;
        private final double minZ;
        private final double maxX;
        private final double maxY;
        private final double maxZ;
        private final boolean teleport;
        private final boolean valid;
        private final long teleportEpoch;

        private TargetCandidateEvidence(long sequence, String status, long ageMs,
                                        int serverTick, int entityId, UUID entityUuid,
                                        Aabb box, boolean teleport, boolean valid,
                                        long teleportEpoch) {
            this.sequence = sequence;
            this.status = status;
            this.ageMs = ageMs;
            this.serverTick = serverTick;
            this.entityId = entityId;
            this.entityUuid = entityUuid;
            minX = box == null ? Double.NaN : box.getMinX();
            minY = box == null ? Double.NaN : box.getMinY();
            minZ = box == null ? Double.NaN : box.getMinZ();
            maxX = box == null ? Double.NaN : box.getMaxX();
            maxY = box == null ? Double.NaN : box.getMaxY();
            maxZ = box == null ? Double.NaN : box.getMaxZ();
            this.teleport = teleport;
            this.valid = valid;
            this.teleportEpoch = teleportEpoch;
        }

        private static TargetCandidateEvidence fromFrame(TargetFrame frame,
                                                         long nowNanoTime) {
            return new TargetCandidateEvidence(frame.getStateSequence(),
                    frame.getStatus() == null ? null : frame.getStatus().name(),
                    frame.ageMillis(nowNanoTime), frame.getServerTick(),
                    frame.getEntityId(), frame.getEntityUuid(), frame.getBoundingBox(),
                    frame.isTeleport(), frame.isValid(), frame.getTeleportEpoch());
        }

        private static TargetCandidateEvidence currentRuntime(int entityId,
                                                              UUID entityUuid,
                                                              Aabb box) {
            return new TargetCandidateEvidence(CURRENT_RUNTIME_SEQUENCE,
                    "CURRENT_RUNTIME", 0L, -1, entityId, entityUuid, box,
                    false, box != null && box.isFinite(), 0L);
        }

        public long getSequence() { return sequence; }
        public String getStatus() { return status; }
        public long getAgeMs() { return ageMs; }
        public int getServerTick() { return serverTick; }
        public int getEntityId() { return entityId; }
        public UUID getEntityUuid() { return entityUuid; }
        public double getMinX() { return minX; }
        public double getMinY() { return minY; }
        public double getMinZ() { return minZ; }
        public double getMaxX() { return maxX; }
        public double getMaxY() { return maxY; }
        public double getMaxZ() { return maxZ; }
        public boolean isTeleport() { return teleport; }
        public boolean isValid() { return valid; }
        public long getTeleportEpoch() { return teleportEpoch; }
    }

    public static final class Builder {
        private final OffsetDateTime timestamp;
        private UUID attackerUuid;
        private String attackerName;
        private UUID targetUuid;
        private String targetName;
        private int protocol = -1;
        private ReachGuardMode mode;
        private ReachGuardMode configuredMode;
        private ReachDecision decision;
        private String reason;
        private Reliability reliability;
        private double measuredReach = Double.NaN;
        private double allowedReach = Double.NaN;
        private double excess = Double.NaN;
        private double violationLevel = Double.NaN;
        private double staleViolationLevel = Double.NaN;
        private double invalidEntityViolationLevel = Double.NaN;
        private int pingMs = -1;
        private int jitterMs = -1;
        private double tps = Double.NaN;
        private double tickDurationMs = Double.NaN;
        private long serverTick = -1L;
        private int attackerCandidateCount;
        private int targetCandidateCount;
        private long attackSequence = -1L;
        private long attackerFrameSequence = -1L;
        private long targetFrameSequence = -1L;
        private long snapshotAgeMs = -1L;
        private int targetEntityId = -1;
        private double attackerX = Double.NaN;
        private double attackerY = Double.NaN;
        private double attackerZ = Double.NaN;
        private double attackerEyeX = Double.NaN;
        private double attackerEyeY = Double.NaN;
        private double attackerEyeZ = Double.NaN;
        private boolean attackerSneaking;
        private double targetMinX = Double.NaN;
        private double targetMinY = Double.NaN;
        private double targetMinZ = Double.NaN;
        private double targetMaxX = Double.NaN;
        private double targetMaxY = Double.NaN;
        private double targetMaxZ = Double.NaN;
        private double selectedExpandedMinX = Double.NaN;
        private double selectedExpandedMinY = Double.NaN;
        private double selectedExpandedMinZ = Double.NaN;
        private double selectedExpandedMaxX = Double.NaN;
        private double selectedExpandedMaxY = Double.NaN;
        private double selectedExpandedMaxZ = Double.NaN;
        private double baseReach = Double.NaN;
        private double hitboxExpansion = Double.NaN;
        private double geometryEpsilon = Double.NaN;
        private double totalExpansion = Double.NaN;
        private double vlExcess = Double.NaN;
        private double vlAdded = Double.NaN;
        private double violationBase = Double.NaN;
        private double excessMultiplier = Double.NaN;
        private double maxExcessAddition = Double.NaN;
        private final List<AttackerCandidateEvidence> attackerCandidates =
                new ArrayList<AttackerCandidateEvidence>();
        private final List<TargetCandidateEvidence> targetCandidates =
                new ArrayList<TargetCandidateEvidence>();
        private TargetCandidateEvidence newestTargetState;
        private TargetCandidateEvidence staleAnchor;
        private TargetCandidateEvidence currentRuntimeCandidate;
        private boolean grace;
        private boolean targetTeleportGrace;
        private boolean unsupportedProtocol;
        private boolean heartbeatStalled;
        private boolean recentKnockback;
        private final List<String> unverifiedExceptions = new ArrayList<String>();
        private long transactionRttMs = -1L;
        private long pendingSyncDelayMs = -1L;
        private long droppedEvidenceCount;

        private Builder(OffsetDateTime timestamp) {
            this.timestamp = Objects.requireNonNull(timestamp, "timestamp");
        }

        public Builder attacker(UUID uuid, String name) {
            attackerUuid = uuid;
            attackerName = name;
            return this;
        }

        public Builder target(UUID uuid, String name) {
            targetUuid = uuid;
            targetName = name;
            return this;
        }

        public Builder protocol(int value) {
            protocol = value;
            return this;
        }

        public Builder mode(ReachGuardMode value) {
            mode = value;
            return this;
        }

        public Builder configuredMode(ReachGuardMode value) {
            configuredMode = value;
            return this;
        }

        public Builder decision(ReachDecision value) {
            decision = value;
            return this;
        }

        public Builder reason(String value) {
            reason = value;
            return this;
        }

        public Builder reliability(Reliability value) {
            reliability = value;
            return this;
        }

        public Builder reach(double measured, double allowed, double reachExcess) {
            measuredReach = measured;
            allowedReach = allowed;
            excess = reachExcess;
            return this;
        }

        public Builder result(ReachResult result) {
            if (result == null) return this;
            decision = result.getDecision();
            reason = result.getReason();
            reliability = result.getReliability();
            measuredReach = result.getMeasuredReach();
            allowedReach = result.getAllowedReach();
            excess = result.getExcess();
            attackerFrameSequence = result.getSelectedAttackerSequence();
            targetFrameSequence = result.getSelectedTargetSequence();
            attackerCandidateCount = result.getAttackerCandidateCount();
            targetCandidateCount = result.getTargetCandidateCount();
            snapshotAgeMs = result.getSnapshotAgeMs();
            return this;
        }

        public Builder violationLevels(double reachVl, double staleVl) {
            violationLevel = reachVl;
            staleViolationLevel = staleVl;
            return this;
        }

        public Builder violationLevels(double reachVl, double staleVl,
                                       double invalidEntityVl) {
            violationLevel = reachVl;
            staleViolationLevel = staleVl;
            invalidEntityViolationLevel = invalidEntityVl;
            return this;
        }

        public Builder network(int ping, int jitter) {
            pingMs = ping;
            jitterMs = jitter;
            return this;
        }

        public Builder server(double currentTps, double currentTickDurationMs,
                              long currentServerTick) {
            tps = currentTps;
            tickDurationMs = currentTickDurationMs;
            serverTick = currentServerTick;
            return this;
        }

        public Builder candidateCounts(int attackerCount, int targetCount) {
            attackerCandidateCount = attackerCount;
            targetCandidateCount = targetCount;
            return this;
        }

        public Builder sequences(long attack, long attackerFrame, long targetFrame) {
            attackSequence = attack;
            attackerFrameSequence = attackerFrame;
            targetFrameSequence = targetFrame;
            return this;
        }

        public Builder snapshotAgeMs(long value) {
            snapshotAgeMs = value;
            return this;
        }

        public Builder geometry(MovementFrame attacker, Aabb targetBox) {
            return geometry(attacker, targetBox, 0.0D);
        }

        public Builder geometry(MovementFrame attacker, Aabb targetBox,
                                double expansion) {
            if (attacker != null) {
                attackerX = attacker.getX();
                attackerY = attacker.getY();
                attackerZ = attacker.getZ();
                Vec3 eye = attacker.eyePosition();
                attackerEyeX = eye.getX();
                attackerEyeY = eye.getY();
                attackerEyeZ = eye.getZ();
                attackerSneaking = attacker.isSneaking();
            }
            if (targetBox != null) {
                targetMinX = targetBox.getMinX();
                targetMinY = targetBox.getMinY();
                targetMinZ = targetBox.getMinZ();
                targetMaxX = targetBox.getMaxX();
                targetMaxY = targetBox.getMaxY();
                targetMaxZ = targetBox.getMaxZ();
                Aabb expanded = targetBox.expand(expansion);
                selectedExpandedMinX = expanded.getMinX();
                selectedExpandedMinY = expanded.getMinY();
                selectedExpandedMinZ = expanded.getMinZ();
                selectedExpandedMaxX = expanded.getMaxX();
                selectedExpandedMaxY = expanded.getMaxY();
                selectedExpandedMaxZ = expanded.getMaxZ();
            }
            return this;
        }

        public Builder reachConfiguration(double configuredBaseReach,
                                          double configuredHitboxExpansion,
                                          double configuredGeometryEpsilon) {
            baseReach = configuredBaseReach;
            hitboxExpansion = configuredHitboxExpansion;
            geometryEpsilon = configuredGeometryEpsilon;
            totalExpansion = configuredHitboxExpansion + configuredGeometryEpsilon;
            return this;
        }

        public Builder violationComputation(double computedExcess,
                                            double computedAddition,
                                            double configuredBase,
                                            double configuredMultiplier,
                                            double configuredMaximumAddition) {
            vlExcess = computedExcess;
            vlAdded = computedAddition;
            violationBase = configuredBase;
            excessMultiplier = configuredMultiplier;
            maxExcessAddition = configuredMaximumAddition;
            return this;
        }

        public Builder targetCandidates(List<TargetFrame> frames, long nowNanoTime) {
            targetCandidates.clear();
            if (frames != null) {
                for (TargetFrame frame : frames) {
                    if (frame != null) {
                        targetCandidates.add(TargetCandidateEvidence.fromFrame(
                                frame, nowNanoTime));
                    }
                }
            }
            return this;
        }

        public Builder attackerCandidates(List<MovementFrame> frames,
                                          long nowNanoTime) {
            attackerCandidates.clear();
            if (frames != null) {
                for (MovementFrame frame : frames) {
                    if (frame != null) {
                        attackerCandidates.add(new AttackerCandidateEvidence(
                                frame, nowNanoTime));
                    }
                }
            }
            return this;
        }

        public Builder staleAnchor(TargetFrame frame, long nowNanoTime) {
            staleAnchor = frame == null ? null
                    : TargetCandidateEvidence.fromFrame(frame, nowNanoTime);
            return this;
        }

        public Builder newestTargetState(TargetFrame frame, long nowNanoTime) {
            newestTargetState = frame == null ? null
                    : TargetCandidateEvidence.fromFrame(frame, nowNanoTime);
            return this;
        }

        public Builder currentRuntimeCandidate(int entityId, UUID entityUuid,
                                               Aabb box) {
            currentRuntimeCandidate = box == null ? null
                    : TargetCandidateEvidence.currentRuntime(entityId, entityUuid, box);
            return this;
        }

        public Builder unverifiedContext(boolean unsupported,
                                         boolean stalledHeartbeat,
                                         List<String> exceptions) {
            unsupportedProtocol = unsupported;
            heartbeatStalled = stalledHeartbeat;
            unverifiedExceptions.clear();
            if (exceptions != null) {
                for (String exception : exceptions) {
                    if (exception != null) unverifiedExceptions.add(exception);
                }
            }
            return this;
        }

        public Builder targetTeleportGrace(boolean value) {
            targetTeleportGrace = value;
            return this;
        }

        public Builder context(int entityId, boolean inGrace,
                               boolean knockback, long rttMs,
                               long pendingDelayMs) {
            targetEntityId = entityId;
            grace = inGrace;
            recentKnockback = knockback;
            transactionRttMs = rttMs;
            pendingSyncDelayMs = pendingDelayMs;
            return this;
        }

        public Builder droppedEvidenceCount(long value) {
            droppedEvidenceCount = Math.max(0L, value);
            return this;
        }

        public EvidenceRecord build() {
            return new EvidenceRecord(this);
        }
    }
}
