package com.poppy.practice.reach;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class PlayerSession {
    private final UUID playerId;
    private final int protocol;
    private final MovementFrameBuffer movementFrames;
    private final ViewerEntityTracker targetTracker;
    private final LinkedHashMap<Short, SyncMarker> syncMarkers =
            new LinkedHashMap<Short, SyncMarker>();
    private final LinkedHashMap<Integer, Boolean> knownNonPlayerEntities =
            new LinkedHashMap<Integer, Boolean>(64, 0.75F, true);
    private PlayerRuntimeSnapshot runtime;
    private long movementSequence;
    private long targetSequence;
    private long confirmedTargetSequence;
    private long coveredTargetSequence;
    private long attackSequence;
    private int lastSyncReservationTick;
    private boolean hasSyncReservationTick;
    private boolean sneaking;
    private int graceUntilTick;
    private int knockbackUntilTick;
    private double jitterMs;
    private long lastRttMs = -1L;
    private ReachResult lastResult;

    public PlayerSession(UUID playerId, int protocol, ReachGuardConfig config,
                         PlayerRuntimeSnapshot runtime, long now) {
        this.playerId = playerId;
        this.protocol = protocol;
        this.runtime = runtime;
        this.movementFrames = new MovementFrameBuffer(config.getAttackerHistorySize());
        this.targetTracker = new ViewerEntityTracker(config.getMaxTargetsPerViewer());
        seedMovement(runtime, now, true);
        this.graceUntilTick = runtime == null ? 0 : runtime.getServerTick()
                + config.getSpawnGraceTicks();
    }

    public synchronized void reconfigure(ReachGuardConfig config) {
        movementFrames.setCapacity(config.getAttackerHistorySize());
        targetTracker.setMaximumTargets(config.getMaxTargetsPerViewer());
    }

    public synchronized void updateRuntime(PlayerRuntimeSnapshot snapshot) {
        this.runtime = snapshot;
    }

    public synchronized void handleMovement(boolean hasPosition, double x, double y, double z,
                                            boolean hasRotation, float yaw, float pitch,
                                            boolean onGround, int serverTick, long now) {
        MovementFrame previous = movementFrames.latest();
        double nextX = hasPosition ? x : previous == null ? runtimeX() : previous.getX();
        double nextY = hasPosition ? y : previous == null ? runtimeY() : previous.getY();
        double nextZ = hasPosition ? z : previous == null ? runtimeZ() : previous.getZ();
        float nextYaw = hasRotation ? yaw : previous == null ? 0.0F : previous.getYaw();
        float nextPitch = hasRotation ? pitch : previous == null ? 0.0F : previous.getPitch();
        boolean valid = validPosition(nextX, nextY, nextZ, serverTick)
                && finite(nextYaw) && finite(nextPitch);
        movementFrames.add(new MovementFrame(++movementSequence, now, serverTick,
                nextX, nextY, nextZ, nextYaw, nextPitch, hasPosition, hasRotation,
                onGround, sneaking, valid));
    }

    public synchronized void setSneaking(boolean sneaking, int serverTick, long now) {
        if (this.sneaking == sneaking) return;
        this.sneaking = sneaking;
        MovementFrame previous = movementFrames.latest();
        if (previous != null) {
            movementFrames.add(new MovementFrame(++movementSequence, now, serverTick,
                    previous.getX(), previous.getY(), previous.getZ(),
                    previous.getYaw(), previous.getPitch(), false, false,
                    previous.isOnGround(), sneaking, previous.isValid()));
        }
    }

    public synchronized void spawnTarget(int entityId, UUID targetUuid,
                                         double x, double y, double z,
                                         int serverTick, long now,
                                         ReachGuardConfig config) {
        if (targetUuid == null || targetUuid.equals(playerId)) return;
        knownNonPlayerEntities.remove(entityId);
        TrackedEntityState state = targetTracker.spawn(entityId, targetUuid,
                x, y, z, serverTick);
        TargetFrame frame = state.absolute(++targetSequence, now, serverTick,
                x, y, z, true);
        state.add(frame, config.getTargetHistorySize());
    }

    /**
     * Registers identity only when the original spawn packet was not observed.
     * Relative deltas cannot safely be applied to this synthetic Bukkit
     * position, so the target stays LOW/unverified until an absolute packet is
     * observed.
     */
    public synchronized void seedTarget(int entityId, UUID targetUuid,
                                        double x, double y, double z,
                                        int serverTick) {
        TrackedEntityState existing = targetTracker.get(entityId);
        if (targetUuid == null || targetUuid.equals(playerId)
                || (existing != null && existing.isValid())) return;
        knownNonPlayerEntities.remove(entityId);
        targetTracker.seed(entityId, targetUuid, x, y, z, serverTick);
    }

    public synchronized void markNonPlayerEntity(int entityId) {
        knownNonPlayerEntities.put(entityId, Boolean.TRUE);
        while (knownNonPlayerEntities.size() > 2048) {
            Integer eldest = knownNonPlayerEntities.keySet().iterator().next();
            knownNonPlayerEntities.remove(eldest);
        }
    }

    public synchronized boolean isKnownNonPlayerEntity(int entityId) {
        return knownNonPlayerEntities.containsKey(entityId);
    }

    public synchronized boolean moveTarget(int entityId, double deltaX, double deltaY,
                                           double deltaZ, int serverTick, long now,
                                           ReachGuardConfig config) {
        TrackedEntityState state = targetTracker.get(entityId);
        if (state == null || !state.isValid() || state.isProvisional()) return false;
        TargetFrame frame = state.relative(++targetSequence, now, serverTick,
                deltaX, deltaY, deltaZ);
        state.add(frame, config.getTargetHistorySize());
        return true;
    }

    public synchronized boolean teleportTarget(int entityId, double x, double y, double z,
                                               int serverTick, long now,
                                               ReachGuardConfig config) {
        TrackedEntityState state = targetTracker.get(entityId);
        if (state == null) return false;
        TargetFrame frame = state.absolute(++targetSequence, now, serverTick,
                x, y, z, true);
        state.add(frame, config.getTargetHistorySize());
        return frame.isValid();
    }

    public synchronized void destroyTarget(int entityId) {
        targetTracker.destroy(entityId);
        knownNonPlayerEntities.remove(entityId);
    }

    public synchronized AttackSnapshot createAttackSnapshot(int targetEntityId,
                                                            UUID fallbackTargetUuid,
                                                            int serverTick, long now,
                                                            ReachGuardConfig config,
                                                            CandidateStateResolver resolver) {
        long sequence = ++attackSequence;
        List<MovementFrame> attackers = movementFrames.newestValidCandidates(now,
                config.getMaxRewindMs());
        TrackedEntityState target = targetTracker.get(targetEntityId);
        // Bukkit's current UUID is authoritative for a reused entity ID.  A
        // missed destroy/spawn sequence must fail open for this hit and force
        // a fresh absolute resync instead of measuring the new player against
        // the old player's retained box.
        if (target != null && fallbackTargetUuid != null
                && !fallbackTargetUuid.equals(target.getEntityUuid())) {
            target.invalidate();
            return AttackSnapshot.untracked(sequence, attackers, targetEntityId,
                    fallbackTargetUuid, runtime, protocol,
                    serverTick <= graceUntilTick,
                    serverTick <= knockbackUntilTick, jitterMs, lastRttMs,
                    oldestPendingSyncAgeMs(now));
        }
        if (target == null || !target.isValid()) {
            UUID destroyedUuid = targetTracker.destroyedUuid(targetEntityId);
            if (destroyedUuid != null) {
                if (fallbackTargetUuid != null
                        && !fallbackTargetUuid.equals(destroyedUuid)) {
                    return AttackSnapshot.untracked(sequence, attackers,
                            targetEntityId, fallbackTargetUuid, runtime, protocol,
                            serverTick <= graceUntilTick,
                            serverTick <= knockbackUntilTick, jitterMs, lastRttMs,
                            oldestPendingSyncAgeMs(now));
                }
                return AttackSnapshot.invalid(sequence, attackers, targetEntityId,
                        destroyedUuid, runtime, protocol,
                        serverTick <= graceUntilTick, serverTick <= knockbackUntilTick,
                        jitterMs, lastRttMs, oldestPendingSyncAgeMs(now));
            }
            UUID evictedUuid = targetTracker.evictedUuid(targetEntityId);
            if (evictedUuid != null) {
                UUID currentUuid = fallbackTargetUuid != null
                        && !fallbackTargetUuid.equals(evictedUuid)
                        ? fallbackTargetUuid : evictedUuid;
                return AttackSnapshot.untracked(sequence, attackers, targetEntityId,
                        currentUuid, runtime, protocol,
                        serverTick <= graceUntilTick,
                        serverTick <= knockbackUntilTick, jitterMs, lastRttMs,
                        oldestPendingSyncAgeMs(now));
            }
            return fallbackTargetUuid == null
                    ? AttackSnapshot.unknown(sequence, attackers, targetEntityId,
                    runtime, protocol, serverTick <= graceUntilTick,
                    serverTick <= knockbackUntilTick, jitterMs, lastRttMs,
                    oldestPendingSyncAgeMs(now))
                    : AttackSnapshot.untracked(sequence, attackers, targetEntityId,
                    fallbackTargetUuid, runtime, protocol,
                    serverTick <= graceUntilTick, serverTick <= knockbackUntilTick,
                    jitterMs, lastRttMs, oldestPendingSyncAgeMs(now));
        }
        List<TargetFrame> history = target.historySnapshot(now,
                Math.max(config.getHistoryRetentionMs(), config.getMaxRewindMs()));
        CandidateStateResolver.TargetResolution resolution = resolver.resolve(history,
                now, config.getMaxRewindMs());
        return new AttackSnapshot(sequence, attackers, resolution, targetEntityId,
                target.getEntityUuid(), target.getSpawnTick(), runtime, protocol,
                serverTick <= graceUntilTick,
                withinGrace(serverTick, target.getLastTeleportTick(),
                        config.getTeleportGraceTicks()),
                serverTick <= knockbackUntilTick,
                jitterMs, lastRttMs, oldestPendingSyncAgeMs(now), false);
    }

    private static boolean withinGrace(int serverTick, int eventTick, int graceTicks) {
        return eventTick != Integer.MIN_VALUE
                && (long) serverTick <= (long) eventTick + Math.max(0, graceTicks);
    }

    /**
     * Reserves a marker only when target state is dirty and the configured
     * server-tick interval has elapsed. A deferred result remains dirty and
     * must be retried by the packet bridge.
     */
    public synchronized SyncRequest reserveSyncIfDue(int serverTick,
                                                     int intervalTicks) {
        if (targetSequence <= coveredTargetSequence) return SyncRequest.none();
        int interval = Math.max(1, intervalTicks);
        if (hasSyncReservationTick) {
            long elapsed = (long) serverTick - (long) lastSyncReservationTick;
            if (elapsed >= 0L && elapsed < interval) return SyncRequest.deferred();
        }
        short actionId = reserveSyncIdInternal();
        lastSyncReservationTick = serverTick;
        hasSyncReservationTick = true;
        return SyncRequest.reserved(actionId);
    }

    /** Immediate reservation retained for deterministic engine tests and tools. */
    public synchronized short reserveSyncId() {
        return reserveSyncIdInternal();
    }

    private short reserveSyncIdInternal() {
        short selected = 0;
        boolean found = false;
        for (int attempt = 0; attempt < 16; attempt++) {
            short candidate = (short) ThreadLocalRandom.current().nextInt(
                    Short.MIN_VALUE, 0);
            if (!syncMarkers.containsKey(candidate)) {
                selected = candidate;
                found = true;
                break;
            }
        }
        if (!found) {
            for (int candidate = -1; candidate >= Short.MIN_VALUE; candidate--) {
                short encoded = (short) candidate;
                if (!syncMarkers.containsKey(encoded)) {
                    selected = encoded;
                    found = true;
                    break;
                }
            }
        }
        if (!found) throw new IllegalStateException("No free transaction action ID");
        // Freeze the covered sequence at the exact point where the marker is
        // reserved for writing.  A later target packet may complete before the
        // marker promise, but that packet was not ordered before this marker
        // and must never be acknowledged by its response.
        syncMarkers.put(selected, new SyncMarker(selected, targetSequence, 0L, false));
        coveredTargetSequence = Math.max(coveredTargetSequence, targetSequence);
        while (syncMarkers.size() > 64) {
            Short first = syncMarkers.keySet().iterator().next();
            syncMarkers.remove(first);
        }
        return selected;
    }

    /** Activates a marker only after its outbound write has actually completed. */
    public synchronized void markSyncSent(short actionId, long now) {
        SyncMarker reserved = syncMarkers.get(actionId);
        if (reserved == null) return;
        syncMarkers.put(actionId, new SyncMarker(actionId,
                reserved.targetSequence, now, true));
    }

    public synchronized void discardSyncMarker(short actionId) {
        if (syncMarkers.remove(actionId) != null) recomputeCoveredTargetSequence();
    }

    public synchronized boolean confirmSync(short actionId, long now) {
        SyncMarker marker = syncMarkers.get(actionId);
        // A client can predict the negative action-id range and answer before
        // the outbound marker write completes.  Such a response is not proof
        // of receipt and must not consume the reservation needed by the real
        // response.
        if (marker == null || !marker.sent) return false;
        syncMarkers.remove(actionId);
        targetTracker.confirmThrough(marker.targetSequence, now);
        confirmedTargetSequence = Math.max(confirmedTargetSequence,
                marker.targetSequence);
        recomputeCoveredTargetSequence();
        long rtt = Math.max(0L, (now - marker.sentNanoTime) / 1_000_000L);
        if (lastRttMs >= 0L) {
            double sample = Math.abs(rtt - lastRttMs);
            jitterMs = jitterMs == 0.0D ? sample : jitterMs * 0.75D + sample * 0.25D;
        }
        lastRttMs = rtt;
        return true;
    }

    private void recomputeCoveredTargetSequence() {
        long covered = confirmedTargetSequence;
        for (SyncMarker marker : syncMarkers.values()) {
            covered = Math.max(covered, marker.targetSequence);
        }
        coveredTargetSequence = covered;
    }

    private long oldestPendingSyncAgeMs(long now) {
        long oldestSent = Long.MAX_VALUE;
        for (SyncMarker marker : syncMarkers.values()) {
            if (marker.sent && marker.sentNanoTime > 0L) {
                oldestSent = Math.min(oldestSent, marker.sentNanoTime);
            }
        }
        return oldestSent == Long.MAX_VALUE ? -1L
                : Math.max(0L, (now - oldestSent) / 1_000_000L);
    }

    public synchronized void reset(PlayerRuntimeSnapshot snapshot, int graceTicks, long now) {
        runtime = snapshot;
        movementFrames.clear();
        targetTracker.clear();
        knownNonPlayerEntities.clear();
        syncMarkers.clear();
        confirmedTargetSequence = targetSequence;
        coveredTargetSequence = targetSequence;
        hasSyncReservationTick = false;
        graceUntilTick = (snapshot == null ? 0 : snapshot.getServerTick())
                + Math.max(0, graceTicks);
        seedMovement(snapshot, now, true);
    }

    /** Resets the viewer's own movement without discarding still-visible targets. */
    public synchronized void resetMovement(PlayerRuntimeSnapshot snapshot,
                                           int graceTicks, long now) {
        runtime = snapshot;
        movementFrames.clear();
        syncMarkers.clear();
        recomputeCoveredTargetSequence();
        hasSyncReservationTick = false;
        graceUntilTick = (snapshot == null ? 0 : snapshot.getServerTick())
                + Math.max(0, graceTicks);
        seedMovement(snapshot, now, true);
    }

    public synchronized void markGrace(int serverTick, int graceTicks) {
        graceUntilTick = Math.max(graceUntilTick, serverTick + Math.max(0, graceTicks));
    }

    public synchronized void markKnockback(int serverTick, int durationTicks) {
        knockbackUntilTick = Math.max(knockbackUntilTick,
                serverTick + Math.max(0, durationTicks));
    }

    public synchronized void setLastResult(ReachResult result) {
        lastResult = result;
    }

    public synchronized ReachResult getLastResult() { return lastResult; }
    public UUID getPlayerId() { return playerId; }
    public int getProtocol() { return protocol; }
    public synchronized PlayerRuntimeSnapshot getRuntime() { return runtime; }
    public synchronized int getTrackedEntityCount() { return targetTracker.size(); }
    public synchronized boolean hasTrackedTarget(int entityId) {
        return targetTracker.get(entityId) != null;
    }
    public synchronized boolean needsTargetResync(int entityId) {
        TrackedEntityState state = targetTracker.get(entityId);
        return state == null || !state.isValid() || state.isProvisional();
    }
    public synchronized double getJitterMs() { return jitterMs; }
    public synchronized long getLastRttMs() { return lastRttMs; }

    private void seedMovement(PlayerRuntimeSnapshot snapshot, long now, boolean valid) {
        if (snapshot == null) return;
        movementFrames.add(new MovementFrame(++movementSequence, now,
                snapshot.getServerTick(), snapshot.getX(), snapshot.getY(), snapshot.getZ(),
                0.0F, 0.0F, true, false, true, sneaking, valid));
    }

    private boolean validPosition(double x, double y, double z, int serverTick) {
        if (!finite(x) || !finite(y) || !finite(z)
                || Math.abs(x) > 30_000_000.0D || Math.abs(z) > 30_000_000.0D
                || Math.abs(y) > 4096.0D) {
            return false;
        }
        if (runtime == null || serverTick <= graceUntilTick) return true;
        double dx = x - runtime.getX();
        double dy = y - runtime.getY();
        double dz = z - runtime.getZ();
        return dx * dx + dz * dz <= 32.0D * 32.0D && Math.abs(dy) <= 32.0D;
    }

    private double runtimeX() { return runtime == null ? 0.0D : runtime.getX(); }
    private double runtimeY() { return runtime == null ? 0.0D : runtime.getY(); }
    private double runtimeZ() { return runtime == null ? 0.0D : runtime.getZ(); }
    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static final class SyncMarker {
        private final short id;
        private final long targetSequence;
        private final long sentNanoTime;
        private final boolean sent;

        private SyncMarker(short id, long targetSequence, long sentNanoTime,
                           boolean sent) {
            this.id = id;
            this.targetSequence = targetSequence;
            this.sentNanoTime = sentNanoTime;
            this.sent = sent;
        }
    }

    public static final class SyncRequest {
        public enum Status {
            NONE,
            DEFERRED,
            RESERVED
        }

        private static final SyncRequest NONE = new SyncRequest(Status.NONE, (short) 0);
        private static final SyncRequest DEFERRED = new SyncRequest(
                Status.DEFERRED, (short) 0);

        private final Status status;
        private final short actionId;

        private SyncRequest(Status status, short actionId) {
            this.status = status;
            this.actionId = actionId;
        }

        private static SyncRequest none() { return NONE; }
        private static SyncRequest deferred() { return DEFERRED; }
        private static SyncRequest reserved(short actionId) {
            return new SyncRequest(Status.RESERVED, actionId);
        }

        public Status getStatus() { return status; }
        public boolean isReserved() { return status == Status.RESERVED; }
        public boolean shouldRetry() { return status == Status.DEFERRED; }

        public short getActionId() {
            if (!isReserved()) {
                throw new IllegalStateException("Sync request has no action ID: " + status);
            }
            return actionId;
        }
    }

    public static final class AttackSnapshot {
        private final long attackSequence;
        private final List<MovementFrame> attackerCandidates;
        private final CandidateStateResolver.TargetResolution targetResolution;
        private final int targetEntityId;
        private final UUID targetUuid;
        private final int targetSpawnTick;
        private final PlayerRuntimeSnapshot runtime;
        private final int protocol;
        private final boolean grace;
        private final boolean targetTeleportGrace;
        private final boolean recentKnockback;
        private final double jitterMs;
        private final long transactionRttMs;
        private final boolean invalidTarget;
        private final long pendingSyncDelayMs;

        private AttackSnapshot(long attackSequence, List<MovementFrame> attackerCandidates,
                               CandidateStateResolver.TargetResolution targetResolution,
                               int targetEntityId, UUID targetUuid, int targetSpawnTick,
                               PlayerRuntimeSnapshot runtime, int protocol, boolean grace,
                               boolean recentKnockback, double jitterMs,
                               long transactionRttMs) {
            this(attackSequence, attackerCandidates, targetResolution,
                    targetEntityId, targetUuid, targetSpawnTick, runtime, protocol,
                    grace, false, recentKnockback, jitterMs, transactionRttMs,
                    -1L, false);
        }

        private AttackSnapshot(long attackSequence, List<MovementFrame> attackerCandidates,
                               CandidateStateResolver.TargetResolution targetResolution,
                               int targetEntityId, UUID targetUuid, int targetSpawnTick,
                               PlayerRuntimeSnapshot runtime, int protocol, boolean grace,
                               boolean targetTeleportGrace,
                               boolean recentKnockback, double jitterMs,
                               long transactionRttMs, long pendingSyncDelayMs,
                               boolean invalidTarget) {
            this.attackSequence = attackSequence;
            this.attackerCandidates = new ArrayList<MovementFrame>(attackerCandidates);
            this.targetResolution = targetResolution;
            this.targetEntityId = targetEntityId;
            this.targetUuid = targetUuid;
            this.targetSpawnTick = targetSpawnTick;
            this.runtime = runtime;
            this.protocol = protocol;
            this.grace = grace;
            this.targetTeleportGrace = targetTeleportGrace;
            this.recentKnockback = recentKnockback;
            this.jitterMs = jitterMs;
            this.transactionRttMs = transactionRttMs;
            this.invalidTarget = invalidTarget;
            this.pendingSyncDelayMs = pendingSyncDelayMs;
        }

        static AttackSnapshot unknown(long sequence, List<MovementFrame> attackers,
                                      int targetEntityId,
                                      PlayerRuntimeSnapshot runtime, int protocol,
                                      boolean grace, boolean recentKnockback,
                                      double jitterMs, long transactionRttMs,
                                      long pendingSyncDelayMs) {
            return new AttackSnapshot(sequence, attackers,
                    CandidateStateResolver.TargetResolution.empty(), targetEntityId,
                    null, 0, runtime, protocol, grace, false, recentKnockback,
                    jitterMs, transactionRttMs, pendingSyncDelayMs, true);
        }

        static AttackSnapshot untracked(long sequence, List<MovementFrame> attackers,
                                        int targetEntityId, UUID targetUuid,
                                        PlayerRuntimeSnapshot runtime, int protocol,
                                        boolean grace, boolean recentKnockback,
                                        double jitterMs, long transactionRttMs,
                                        long pendingSyncDelayMs) {
            return new AttackSnapshot(sequence, attackers,
                    CandidateStateResolver.TargetResolution.empty(), targetEntityId,
                    targetUuid, 0, runtime, protocol, grace, false, recentKnockback,
                    jitterMs, transactionRttMs, pendingSyncDelayMs, false);
        }

        static AttackSnapshot invalid(long sequence, List<MovementFrame> attackers,
                                      int targetEntityId, UUID targetUuid,
                                      PlayerRuntimeSnapshot runtime, int protocol,
                                      boolean grace, boolean recentKnockback,
                                      double jitterMs, long transactionRttMs,
                                      long pendingSyncDelayMs) {
            return new AttackSnapshot(sequence, attackers,
                    CandidateStateResolver.TargetResolution.empty(), targetEntityId,
                    targetUuid, 0, runtime, protocol, grace, false, recentKnockback,
                    jitterMs, transactionRttMs, pendingSyncDelayMs, true);
        }

        public boolean hasTarget() { return targetUuid != null; }
        public long getAttackSequence() { return attackSequence; }
        public List<MovementFrame> getAttackerCandidates() {
            return new ArrayList<MovementFrame>(attackerCandidates);
        }
        public CandidateStateResolver.TargetResolution getTargetResolution() {
            return targetResolution;
        }
        public int getTargetEntityId() { return targetEntityId; }
        public UUID getTargetUuid() { return targetUuid; }
        public int getTargetSpawnTick() { return targetSpawnTick; }
        public PlayerRuntimeSnapshot getRuntime() { return runtime; }
        public int getProtocol() { return protocol; }
        public boolean isGrace() { return grace; }
        public boolean isTargetTeleportGrace() { return targetTeleportGrace; }
        public boolean hasRecentKnockback() { return recentKnockback; }
        public double getJitterMs() { return jitterMs; }
        public long getTransactionRttMs() { return transactionRttMs; }
        public boolean isInvalidTarget() { return invalidTarget; }
        public long getPendingSyncDelayMs() { return pendingSyncDelayMs; }
    }
}
