package com.poppy.practice.reach;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.network.PlayerPingService;
import com.poppy.practice.reach.packet.ReachPacketSink;
import com.poppy.practice.reach.packet.PacketBridge;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Coordinates packet-thread reach validation with main-thread Bukkit state.
 * Bukkit objects never cross into the packet callbacks: online state is copied
 * into immutable {@link PlayerRuntimeSnapshot}s once per server tick.
 */
public final class ReachGuardService implements ReachPacketSink {
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;
    private static final long PACKET_ERROR_COOLDOWN_NANOS = 5_000_000_000L;
    private static final long RECENT_STALL_GRACE_NANOS = 1_000_000_000L;
    private static final long DEBUG_MESSAGE_COOLDOWN_NANOS = 100_000_000L;
    private static final long DROP_REPORT_COOLDOWN_NANOS = 5_000_000_000L;
    private static final long OFFLINE_RETENTION_NANOS = 3_600_000_000_000L;
    private static final long RETENTION_CLEANUP_NANOS = 60_000_000_000L;
    private static final long ENTITY_IDENTITY_RETENTION_NANOS =
            1_800_000_000_000L;
    private static final long TARGET_RESYNC_COOLDOWN_NANOS = 1_000_000_000L;
    private static final long TARGET_RESYNC_CLEANUP_NANOS = 1_000_000_000L;
    private static final int MAX_PLAYER_ENTITY_IDENTITIES = 4096;
    private static final int MAX_TARGET_RESYNC_COOLDOWNS = 8192;
    private static final double TARGET_RESYNC_MAX_DISTANCE_SQUARED = 16.0D * 16.0D;
    private static final int INSPECTION_HISTORY_SIZE = 50;

    private final PracticePlugin plugin;
    private final PlayerPingService pingService;
    private final AtomicReference<ReachGuardConfig> configuration;
    private final ConcurrentMap<UUID, PlayerSession> sessions =
            new ConcurrentHashMap<UUID, PlayerSession>();
    private final ConcurrentMap<UUID, PlayerRuntimeSnapshot> runtimeSnapshots =
            new ConcurrentHashMap<UUID, PlayerRuntimeSnapshot>();
    private final ConcurrentMap<UUID, String> playerNames =
            new ConcurrentHashMap<UUID, String>();
    private final ConcurrentMap<UUID, Long> lastSeenNanoTimes =
            new ConcurrentHashMap<UUID, Long>();
    private final ConcurrentMap<Integer, UUID> playerEntityIds =
            new ConcurrentHashMap<Integer, UUID>();
    private final ConcurrentMap<Integer, Long> playerEntityLastSeen =
            new ConcurrentHashMap<Integer, Long>();
    private final ConcurrentMap<ResyncKey, Long> targetResyncCooldowns =
            new ConcurrentHashMap<ResyncKey, Long>();
    private final Set<ResyncKey> pendingTargetResyncs =
            Collections.newSetFromMap(new ConcurrentHashMap<ResyncKey, Boolean>());
    private final Set<UUID> packetActive =
            Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final ConcurrentMap<UUID, Long> temporaryExemptions =
            new ConcurrentHashMap<UUID, Long>();
    private final Set<UUID> alertOptOut =
            Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final ConcurrentMap<UUID, Long> lastAlertAt =
            new ConcurrentHashMap<UUID, Long>();
    private final ConcurrentMap<UUID, Long> lastPacketErrorAt =
            new ConcurrentHashMap<UUID, Long>();
    private final ConcurrentMap<UUID, Long> lastDebugAt =
            new ConcurrentHashMap<UUID, Long>();
    private final ConcurrentMap<UUID, Long> packetFailOpenUntil =
            new ConcurrentHashMap<UUID, Long>();
    private final Set<UUID> bridgeUnavailable =
            Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final ConcurrentMap<UUID, ArrayDeque<EvidenceRecord>> inspectionHistory =
            new ConcurrentHashMap<UUID, ArrayDeque<EvidenceRecord>>();
    private final Set<UUID> punishmentQueued =
            Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final ConcurrentLinkedQueue<MainThreadAction> mainThreadActions =
            new ConcurrentLinkedQueue<MainThreadAction>();
    private final CandidateStateResolver candidateResolver = new CandidateStateResolver();
    private final ReachValidationEngine validationEngine = new ReachValidationEngine();
    private final ViolationTracker violations;
    private final AttackPermitService permits;
    private final AtomicLong processedAttacks = new AtomicLong();
    private final AtomicLong cancelledAttacks = new AtomicLong();
    private final AtomicLong unverifiedAttacks = new AtomicLong();
    private final AtomicLong packetErrors = new AtomicLong();
    private final AtomicLong damageWithoutPermit = new AtomicLong();
    private final BukkitTask tickTask;
    private volatile PacketBridge packetBridge;

    private volatile AsyncEvidenceLogger evidenceLogger;
    private volatile ServerHealthSnapshot serverHealth =
            new ServerHealthSnapshot(0, 20.0D, 50L, false, System.nanoTime());
    private volatile boolean shuttingDown;
    private long lastTickNanoTime;
    private long recentStallUntilNanoTime;
    private double smoothedTps = 20.0D;
    private long lastDropReportNanoTime;
    private long lastReportedDroppedLogs;
    private long lastEvidenceErrorReportNanoTime;
    private long lastReportedEvidenceErrors;
    private long lastRetentionCleanupNanoTime;
    private long lastTargetResyncCleanupNanoTime;

    public ReachGuardService(PracticePlugin plugin, PlayerPingService pingService) {
        if (plugin == null) throw new IllegalArgumentException("plugin");
        if (pingService == null) throw new IllegalArgumentException("pingService");
        this.plugin = plugin;
        this.pingService = pingService;
        ReachGuardConfig initial = ReachGuardConfig.load(plugin.getConfig());
        configuration = new AtomicReference<ReachGuardConfig>(initial);
        violations = new ViolationTracker(initial);
        permits = new AttackPermitService(initial.getPermitExpireMs());
        evidenceLogger = createEvidenceLogger(initial);
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 1L, 1L);
    }

    public void setPacketBridge(PacketBridge packetBridge) {
        this.packetBridge = packetBridge;
    }

    /** Main-thread lifecycle hook; packet protocol is learned by the bridge. */
    public void handleJoin(Player player) {
        if (player == null) return;
        lastSeenNanoTimes.put(player.getUniqueId(), System.nanoTime());
        updateRuntime(player, serverHealth.getServerTick());
    }

    public void handleQuit(UUID playerId) {
        if (playerId == null) return;
        lastSeenNanoTimes.put(playerId, System.nanoTime());
        PlayerRuntimeSnapshot removed = runtimeSnapshots.remove(playerId);
        if (removed != null) {
            playerEntityIds.remove(removed.getEntityId(), playerId);
            playerEntityLastSeen.remove(removed.getEntityId());
        }
        sessions.remove(playerId);
        packetActive.remove(playerId);
        temporaryExemptions.remove(playerId);
        alertOptOut.remove(playerId);
        lastAlertAt.remove(playerId);
        lastPacketErrorAt.remove(playerId);
        lastDebugAt.remove(playerId);
        packetFailOpenUntil.remove(playerId);
        bridgeUnavailable.remove(playerId);
        punishmentQueued.remove(playerId);
        removeResyncCooldowns(playerId);
        permits.clearPlayer(playerId);
    }

    public void resetPlayer(Player player, ResetReason reason) {
        if (player == null) return;
        PlayerRuntimeSnapshot runtime = updateRuntime(player, serverHealth.getServerTick());
        PlayerSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        ReachGuardConfig config = configuration.get();
        int grace = graceTicks(reason, config);
        if (reason == ResetReason.RESPAWN || reason == ResetReason.WORLD_CHANGE
                || reason == ResetReason.DEATH) {
            session.reset(runtime, grace, System.nanoTime());
        } else {
            session.resetMovement(runtime, grace, System.nanoTime());
        }
        permits.clearPlayer(player.getUniqueId());
    }

    public void markKnockback(UUID playerId) {
        PlayerSession session = sessions.get(playerId);
        if (session != null) session.markKnockback(serverHealth.getServerTick(), 5);
    }

    @Override
    public void onBridgeInstalled(UUID playerId, int playerEntityId, int protocol) {
        if (shuttingDown || playerId == null) return;
        bridgeUnavailable.remove(playerId);
        packetFailOpenUntil.remove(playerId);
        mainThreadActions.offer(MainThreadAction.seed(playerId, protocol));
    }

    @Override
    public void onMovement(UUID playerId, int playerEntityId, int protocol,
                           boolean hasPosition, double x, double y, double z,
                           boolean hasRotation, float yaw, float pitch,
                           boolean onGround, long receiveNanoTime) {
        if (shuttingDown) return;
        PlayerSession session = session(playerId, protocol, receiveNanoTime);
        if (session == null) return;
        packetActive.add(playerId);
        session.handleMovement(hasPosition, x, y, z, hasRotation, yaw, pitch,
                onGround, serverHealth.getServerTick(), receiveNanoTime);
    }

    @Override
    public void onSneaking(UUID playerId, int playerEntityId, int protocol,
                           boolean sneaking, long receiveNanoTime) {
        if (shuttingDown) return;
        PlayerSession session = session(playerId, protocol, receiveNanoTime);
        if (session == null) return;
        packetActive.add(playerId);
        session.setSneaking(sneaking, serverHealth.getServerTick(), receiveNanoTime);
    }

    @Override
    public boolean onAttack(UUID playerId, int playerEntityId, int protocol,
                            int targetEntityId, long receiveNanoTime) {
        if (shuttingDown) return false;
        PlayerSession session = session(playerId, protocol, receiveNanoTime);
        if (session == null) return false;
        packetActive.add(playerId);
        processedAttacks.incrementAndGet();

        ReachGuardConfig config = configuration.get();
        if (session.isKnownNonPlayerEntity(targetEntityId)) {
            ReachResult result = simpleResult(ReachDecision.BYPASS,
                    "KNOWN_NON_PLAYER_ENTITY", Reliability.LOW, config);
            session.setLastResult(result);
            return false;
        }
        UUID knownTargetId = playerEntityIds.get(targetEntityId);
        if (knownTargetId != null) {
            playerEntityLastSeen.put(targetEntityId, receiveNanoTime);
        }
        PlayerSession.AttackSnapshot attack = session.createAttackSnapshot(
                targetEntityId, knownTargetId,
                serverHealth.getServerTick(),
                receiveNanoTime, config, candidateResolver);
        if (attack.getTargetUuid() != null && !attack.isInvalidTarget()
                && session.needsTargetResync(targetEntityId)) {
            queueTargetResync(playerId, attack.getTargetUuid(), protocol,
                    receiveNanoTime);
        }

        PlayerRuntimeSnapshot attackerRuntime = attack.getRuntime();
        PlayerRuntimeSnapshot targetRuntime = attack.getTargetUuid() == null ? null
                : runtimeSnapshots.get(attack.getTargetUuid());
        Aabb currentTarget = targetRuntime == null ? null : Aabb.player(
                targetRuntime.getX(), targetRuntime.getY(), targetRuntime.getZ());
        if (mustBypass(playerId, attackerRuntime, targetRuntime, receiveNanoTime)) {
            ReachResult result = simpleResult(ReachDecision.BYPASS,
                    bypassReason(playerId, attackerRuntime, targetRuntime,
                            receiveNanoTime), Reliability.LOW, config);
            session.setLastResult(result);
            if (attack.getTargetUuid() != null) {
                permits.issue(playerId, attack.getTargetUuid(),
                        attack.getAttackSequence(), serverHealth.getServerTick(),
                        receiveNanoTime);
            }
            maybeRecordEvidence(playerId, attack.getTargetUuid(), protocol,
                    config, serverHealth, config.getMode(), result, attack,
                    violations.snapshot(playerId, receiveNanoTime), currentTarget,
                    receiveNanoTime);
            return false;
        }

        ServerHealthSnapshot validationHealth = serverHealth;
        ReachGuardMode effectiveMode = effectiveMode(config, protocol, validationHealth);
        ReachResult result = validationEngine.validate(attack, config, effectiveMode,
                validationHealth, currentTarget, receiveNanoTime);
        session.setLastResult(result);

        ViolationSnapshot violation = result.getReliability() == Reliability.LOW
                ? violations.snapshot(playerId, receiveNanoTime)
                : violations.record(playerId, result, config.getBaseReach(),
                receiveNanoTime);
        maybeRecordEvidence(playerId, attack.getTargetUuid(), protocol,
                config, validationHealth, effectiveMode, result, attack, violation,
                currentTarget, receiveNanoTime);

        if (result.getDecision() == ReachDecision.ALLOW_UNVERIFIED) {
            unverifiedAttacks.incrementAndGet();
        }
        if (result.getDecision().isViolation()
                && result.getReliability() != Reliability.LOW) {
            queueAlert(playerId, attack.getTargetUuid(), result, violation,
                    config, receiveNanoTime);
            if (effectiveMode != ReachGuardMode.OBSERVE) {
                queuePunishmentIfNeeded(playerId, result, violation, config);
            }
        }
        if (result.getDecision().isCancelled()) {
            cancelledAttacks.incrementAndGet();
            queueDebugCancellation(playerId, attack.getTargetUuid(), result, config);
            return true;
        }

        if (attack.getTargetUuid() != null) {
            permits.issue(playerId, attack.getTargetUuid(), attack.getAttackSequence(),
                    serverHealth.getServerTick(), receiveNanoTime);
        }
        return false;
    }

    @Override
    public boolean onTransactionResponse(UUID playerId, int playerEntityId,
                                         int protocol, int windowId,
                                         short actionId, long receiveNanoTime) {
        if (shuttingDown || windowId != 0 || actionId >= 0) return false;
        PlayerSession session = sessions.get(playerId);
        return session != null && session.confirmSync(actionId, receiveNanoTime);
    }

    @Override
    public int onTransactionSyncRequested(UUID viewerId, int viewerEntityId,
                                          int protocol, long requestNanoTime) {
        if (shuttingDown) return NO_TRANSACTION_SYNC;
        PlayerSession session = sessions.get(viewerId);
        ReachGuardConfig config = configuration.get();
        if (session == null || !config.supportsProtocol(session.getProtocol())) {
            return NO_TRANSACTION_SYNC;
        }
        PlayerSession.SyncRequest request = session.reserveSyncIfDue(
                serverHealth.getServerTick(), config.getSyncIntervalTicks());
        if (request.isReserved()) return request.getActionId();
        return request.shouldRetry()
                ? DEFER_TRANSACTION_SYNC : NO_TRANSACTION_SYNC;
    }

    @Override
    public void onTransactionSyncSent(UUID viewerId, int viewerEntityId,
                                      int protocol, short actionId,
                                      long sentNanoTime) {
        if (shuttingDown) return;
        PlayerSession session = sessions.get(viewerId);
        if (session != null) session.markSyncSent(actionId, sentNanoTime);
    }

    @Override
    public int onNamedEntitySpawnSent(UUID viewerId, int viewerEntityId,
                                      int protocol, int targetEntityId,
                                      UUID targetUuid, double x, double y,
                                      double z, long sentNanoTime) {
        if (shuttingDown || targetUuid == null) return NO_TRANSACTION_SYNC;
        PlayerSession session = session(viewerId, protocol, sentNanoTime);
        if (session == null) return NO_TRANSACTION_SYNC;
        session.spawnTarget(targetEntityId, targetUuid, x, y, z,
                serverHealth.getServerTick(), sentNanoTime, configuration.get());
        rememberPlayerEntityIdentity(targetEntityId, targetUuid, sentNanoTime,
                runtimeSnapshots.containsKey(targetUuid));
        return syncRequest(session);
    }

    @Override
    public void onNonPlayerEntitySpawnSent(UUID viewerId, int viewerEntityId,
                                           int protocol, int targetEntityId,
                                           long sentNanoTime) {
        if (shuttingDown) return;
        playerEntityIds.remove(targetEntityId);
        playerEntityLastSeen.remove(targetEntityId);
        PlayerSession session = session(viewerId, protocol, sentNanoTime);
        if (session != null) session.markNonPlayerEntity(targetEntityId);
    }

    @Override
    public int onRelativeEntityMoveSent(UUID viewerId, int viewerEntityId,
                                        int protocol, int targetEntityId,
                                        double deltaX, double deltaY, double deltaZ,
                                        boolean onGround, long sentNanoTime) {
        if (shuttingDown) return NO_TRANSACTION_SYNC;
        PlayerSession session = sessions.get(viewerId);
        if (session == null) return NO_TRANSACTION_SYNC;
        if (!session.moveTarget(targetEntityId, deltaX, deltaY, deltaZ,
                serverHealth.getServerTick(), sentNanoTime, configuration.get())) {
            return NO_TRANSACTION_SYNC;
        }
        return syncRequest(session);
    }

    @Override
    public int onEntityTeleportSent(UUID viewerId, int viewerEntityId,
                                    int protocol, int targetEntityId,
                                    double x, double y, double z,
                                    boolean onGround, long sentNanoTime) {
        if (shuttingDown) return NO_TRANSACTION_SYNC;
        PlayerSession session = sessions.get(viewerId);
        if (session == null) return NO_TRANSACTION_SYNC;
        if (!session.teleportTarget(targetEntityId, x, y, z,
                serverHealth.getServerTick(), sentNanoTime, configuration.get())) {
            return NO_TRANSACTION_SYNC;
        }
        return syncRequest(session);
    }

    @Override
    public void onEntityDestroyedSent(UUID viewerId, int viewerEntityId,
                                      int protocol, int targetEntityId,
                                      long sentNanoTime) {
        if (shuttingDown) return;
        PlayerSession session = sessions.get(viewerId);
        if (session != null) session.destroyTarget(targetEntityId);
    }

    @Override
    public void onViewerResetSent(UUID viewerId, int viewerEntityId,
                                  int protocol, String reason,
                                  long sentNanoTime) {
        if (shuttingDown) return;
        PlayerSession session = sessions.get(viewerId);
        if (session == null) return;
        PlayerRuntimeSnapshot runtime = runtimeSnapshots.get(viewerId);
        ReachGuardConfig config = configuration.get();
        if ("RESPAWN".equals(reason)) {
            session.reset(runtime, config.getRespawnGraceTicks(), sentNanoTime);
        } else {
            session.resetMovement(runtime, config.getTeleportGraceTicks(), sentNanoTime);
        }
        permits.clearPlayer(viewerId);
    }

    @Override
    public void onTransactionSyncWriteFailed(UUID viewerId, int viewerEntityId,
                                             int protocol, int windowId,
                                             short actionId,
                                             long failureNanoTime) {
        if (shuttingDown) return;
        PlayerSession session = sessions.get(viewerId);
        if (session != null) {
            session.discardSyncMarker(actionId);
            ReachGuardConfig config = configuration.get();
            ReachResult result = simpleResult(ReachDecision.ALLOW_UNVERIFIED,
                    "SYNC_WRITE_FAILED", Reliability.LOW, config);
            maybeRecordEvidence(viewerId, null, protocol, config, serverHealth,
                    config.getMode(), result, null,
                    violations.snapshot(viewerId, failureNanoTime),
                    null, failureNanoTime);
        }
    }

    @Override
    public void onPacketError(UUID playerId, int playerEntityId, int protocol,
                              String stage, String errorType, String message) {
        if (shuttingDown) return;
        packetErrors.incrementAndGet();
        long now = System.nanoTime();
        if (playerId == null) {
            mainThreadActions.offer(MainThreadAction.log(ChatColor.RED
                    + "[ReachGuard] PacketBridge " + safe(stage)
                    + " global error: " + safe(errorType) + " " + safe(message)));
            return;
        }
        packetFailOpenUntil.put(playerId, now + RECENT_STALL_GRACE_NANOS);
        if ("PACKET_ACCESS_INIT".equals(stage) || "INSTALL".equals(stage)) {
            bridgeUnavailable.add(playerId);
        }
        Long last = lastPacketErrorAt.get(playerId);
        if (last != null && now - last < PACKET_ERROR_COOLDOWN_NANOS) return;
        lastPacketErrorAt.put(playerId, now);
        ReachGuardConfig config = configuration.get();
        ReachResult result = simpleResult(ReachDecision.ALLOW_UNVERIFIED,
                "PACKET_ERROR_" + safe(stage), Reliability.LOW, config);
        maybeRecordEvidence(playerId, null, protocol, config, serverHealth,
                config.getMode(), result, null,
                violations.snapshot(playerId, now), null, now);
        mainThreadActions.offer(MainThreadAction.log(ChatColor.RED
                + "[ReachGuard] PacketBridge " + safe(stage) + " error for "
                + displayName(playerId) + ": " + safe(errorType) + " "
                + safe(message)));
    }

    /**
     * LOWEST-priority Bukkit damage guard. Returns false when the event must be
     * cancelled and its damage set to zero by the listener.
     */
    public boolean allowMeleeDamage(UUID attackerId, UUID targetId,
                                    long nowNanoTime) {
        ReachGuardConfig config = configuration.get();
        if (!config.isDamageGuardEnabled() || !config.isCancelWithoutPermit()
                || config.getMode() == ReachGuardMode.OBSERVE) {
            return true;
        }
        PlayerSession session = sessions.get(attackerId);
        PlayerRuntimeSnapshot runtime = runtimeSnapshots.get(attackerId);
        ServerHealthSnapshot health = serverHealth;
        boolean hasPermit = permits.consume(attackerId, targetId,
                health.getServerTick(), nowNanoTime);
        if (hasPermit) return true;
        String failOpenReason = damageGuardFailOpenReason(attackerId, session,
                runtime, config, health, nowNanoTime);
        if (failOpenReason != null) {
            ReachResult failOpen = simpleResult(ReachDecision.ALLOW_UNVERIFIED,
                    "DAMAGE_GUARD_FAIL_OPEN_" + failOpenReason,
                    Reliability.LOW, config);
            if (session != null) session.setLastResult(failOpen);
            PlayerRuntimeSnapshot targetRuntime = runtimeSnapshots.get(targetId);
            Aabb currentTarget = targetRuntime == null ? null : Aabb.player(
                    targetRuntime.getX(), targetRuntime.getY(), targetRuntime.getZ());
            maybeRecordEvidence(attackerId, targetId,
                    session == null ? -1 : session.getProtocol(), config, health,
                    config.getMode(), failOpen, null,
                    violations.snapshot(attackerId, nowNanoTime), currentTarget,
                    nowNanoTime);
            unverifiedAttacks.incrementAndGet();
            return true;
        }

        damageWithoutPermit.incrementAndGet();
        cancelledAttacks.incrementAndGet();
        ReachResult result = simpleResult(ReachDecision.CANCEL_INVALID_ENTITY,
                "DAMAGE_WITHOUT_PERMIT", Reliability.HIGH, config);
        session.setLastResult(result);
        ViolationSnapshot violation = violations.record(attackerId, result,
                config.getBaseReach(), nowNanoTime);
        maybeRecordEvidence(attackerId, targetId, session.getProtocol(),
                config, serverHealth, config.getMode(), result, null, violation,
                null, nowNanoTime);
        queueAlert(attackerId, targetId, result, violation, config, nowNanoTime);
        queuePunishmentIfNeeded(attackerId, result, violation, config);
        queueDebugCancellation(attackerId, targetId, result, config);
        return false;
    }

    private String damageGuardFailOpenReason(UUID attackerId,
                                             PlayerSession session,
                                             PlayerRuntimeSnapshot runtime,
                                             ReachGuardConfig config,
                                             ServerHealthSnapshot health,
                                             long now) {
        if (session == null) return "NO_SESSION";
        if (!packetActive.contains(attackerId)) return "PACKET_PATH_INACTIVE";
        if (bridgeUnavailable.contains(attackerId)) return "BRIDGE_UNAVAILABLE";
        if (isPacketFailOpen(attackerId, now)) return "RECENT_PACKET_ERROR";
        if (!config.supportsProtocol(session.getProtocol())) {
            return "UNSUPPORTED_PROTOCOL";
        }
        if (runtime == null) return "NO_RUNTIME_SNAPSHOT";
        if (runtime.hasBypass()) return "BYPASS_PERMISSION";
        if (isTemporarilyExempt(attackerId, now)) return "TEMPORARY_EXEMPTION";
        if (health == null) return "NO_SERVER_HEALTH";
        if (health.getTps() < config.getMinimumTpsForStrict()) return "LOW_TPS";
        if (health.hasRecentStall()) return "RECENT_SERVER_STALL";
        if (health.getLastTickDurationMs() >= config.getMaximumTickTimeMs()) {
            return "LONG_SERVER_TICK";
        }
        return heartbeatDelayed(now, config, health)
                ? "HEARTBEAT_DELAYED" : null;
    }

    public void exempt(UUID playerId, long seconds) {
        if (playerId == null) return;
        long duration = Math.max(1L, seconds) * 1_000_000_000L;
        long now = System.nanoTime();
        long expires = duration > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + duration;
        temporaryExemptions.put(playerId, expires);
        permits.clearPlayer(playerId);
    }

    public void unexempt(UUID playerId) {
        if (playerId != null) temporaryExemptions.remove(playerId);
    }

    public boolean isTemporarilyExempt(UUID playerId) {
        return isTemporarilyExempt(playerId, System.nanoTime());
    }

    public void setAlerts(UUID playerId, boolean enabled) {
        if (playerId == null) return;
        if (enabled) alertOptOut.remove(playerId);
        else alertOptOut.add(playerId);
    }

    public boolean hasAlerts(UUID playerId) {
        return playerId != null && !alertOptOut.contains(playerId);
    }

    public void resetViolation(UUID playerId) {
        violations.reset(playerId);
        punishmentQueued.remove(playerId);
    }

    public ViolationSnapshot getViolation(UUID playerId) {
        return violations.snapshot(playerId, System.nanoTime());
    }

    public PlayerStatus getPlayerStatus(UUID playerId) {
        if (playerId == null) return null;
        PlayerSession session = sessions.get(playerId);
        PlayerRuntimeSnapshot runtime = runtimeSnapshots.get(playerId);
        if (runtime == null) return null;
        ViolationSnapshot violation = violations.snapshot(playerId, System.nanoTime());
        return new PlayerStatus(playerId, displayName(playerId),
                session == null ? -1 : session.getProtocol(),
                runtime == null ? 0 : runtime.getPing(),
                session == null ? -1L : session.getLastRttMs(),
                session == null ? 0.0D : session.getJitterMs(),
                session == null ? 0 : session.getTrackedEntityCount(),
                packetActive.contains(playerId), isTemporarilyExempt(playerId),
                violation, session == null ? null : session.getLastResult());
    }

    public List<EvidenceRecord> getInspectionHistory(UUID playerId) {
        ArrayDeque<EvidenceRecord> history = inspectionHistory.get(playerId);
        if (history == null) return Collections.emptyList();
        synchronized (history) {
            return new ArrayList<EvidenceRecord>(history);
        }
    }

    public ReachGuardConfig getConfiguration() { return configuration.get(); }
    public ServerHealthSnapshot getServerHealth() { return serverHealth; }
    public long getProcessedAttacks() { return processedAttacks.get(); }
    public long getCancelledAttacks() { return cancelledAttacks.get(); }
    public long getUnverifiedAttacks() { return unverifiedAttacks.get(); }
    public long getPacketErrors() { return packetErrors.get(); }
    public long getDamageWithoutPermitCount() { return damageWithoutPermit.get(); }
    public int getSessionCount() { return sessions.size(); }
    public int getPendingPermitCount() { return permits.pendingPermitCount(); }
    public long getDroppedLogCount() { return evidenceLogger.getDroppedCount(); }
    public long getEvidenceErrorCount() { return evidenceLogger.getErrorCount(); }
    public long getProcessedLogCount() { return evidenceLogger.getProcessedCount(); }
    public int getQueuedLogCount() { return evidenceLogger.getQueuedCount(); }

    /** Main-thread atomic configuration reload. */
    public void reloadConfiguration() {
        ReachGuardConfig next = ReachGuardConfig.load(plugin.getConfig());
        ReachGuardConfig previous = configuration.get();
        AsyncEvidenceLogger replacement = evidenceLogger;
        boolean replaceLogger = previous.getAsyncQueueSize() != next.getAsyncQueueSize()
                || previous.getRetentionDays() != next.getRetentionDays();
        if (replaceLogger) replacement = createEvidenceLogger(next);

        configuration.set(next);
        violations.updateConfiguration(next);
        permits.setExpireMilliseconds(next.getPermitExpireMs());
        for (PlayerSession session : sessions.values()) session.reconfigure(next);
        if (replaceLogger) {
            AsyncEvidenceLogger old = evidenceLogger;
            long reportedDrops = lastReportedDroppedLogs;
            long reportedErrors = lastReportedEvidenceErrors;
            evidenceLogger = replacement;
            lastReportedDroppedLogs = 0L;
            lastReportedEvidenceErrors = 0L;
            lastDropReportNanoTime = 0L;
            lastEvidenceErrorReportNanoTime = 0L;
            retireLogger(old, reportedDrops, reportedErrors);
        }
    }

    public void shutdown() {
        shuttingDown = true;
        tickTask.cancel();
        permits.clear();
        sessions.clear();
        packetActive.clear();
        mainThreadActions.clear();
        AsyncEvidenceLogger logger = evidenceLogger;
        if (logger != null) {
            logger.shutdown();
            reportRetiredLoggerHealth(logger, lastReportedDroppedLogs,
                    lastReportedEvidenceErrors);
        }
        violations.clear();
    }

    private PlayerSession session(UUID playerId, int protocol, long now) {
        if (shuttingDown || playerId == null) return null;
        PlayerSession current = sessions.get(playerId);
        if (current != null && (current.getProtocol() == protocol || protocol < 0)) {
            return current;
        }
        PlayerRuntimeSnapshot runtime = runtimeSnapshots.get(playerId);
        if (runtime == null) return null;
        ReachGuardConfig config = configuration.get();
        PlayerSession created = new PlayerSession(playerId, protocol, config, runtime, now);
        if (current == null) {
            PlayerSession raced = sessions.putIfAbsent(playerId, created);
            return raced == null ? created : raced;
        }
        // Protocol changes only occur across reconnects; replacement is safer
        // than mixing coordinate histories from different client semantics.
        if (sessions.replace(playerId, current, created)) return created;
        return sessions.get(playerId);
    }

    private int syncRequest(PlayerSession session) {
        return !shuttingDown && configuration.get().supportsProtocol(
                session.getProtocol())
                ? REQUEST_TRANSACTION_SYNC : NO_TRANSACTION_SYNC;
    }

    private ReachGuardMode effectiveMode(ReachGuardConfig config, int protocol,
                                         ServerHealthSnapshot health) {
        ReachGuardMode desired = config.getMode();
        if (health.getTps() < config.getMinimumTpsForStrict()
                || (desired == ReachGuardMode.STRICT
                && !config.supportsProtocol(protocol))) {
            return ReachGuardMode.OBSERVE;
        }
        return desired;
    }

    private boolean mustBypass(UUID playerId, PlayerRuntimeSnapshot attacker,
                               PlayerRuntimeSnapshot target, long now) {
        if (attacker == null || !attacker.isAlive()
                || attacker.isCreativeOrSpectator() || attacker.isInVehicle()
                || attacker.hasBypass() || isTemporarilyExempt(playerId, now)
                || bridgeUnavailable.contains(playerId)
                || isPacketFailOpen(playerId, now)) {
            return true;
        }
        return target != null && (!target.isAlive()
                || target.isCreativeOrSpectator() || target.isInVehicle()
                || !sameWorld(attacker, target));
    }

    private String bypassReason(UUID playerId, PlayerRuntimeSnapshot attacker,
                                PlayerRuntimeSnapshot target, long now) {
        if (isTemporarilyExempt(playerId, now)) return "TEMPORARY_EXEMPTION";
        if (bridgeUnavailable.contains(playerId)) return "PACKET_BRIDGE_UNAVAILABLE";
        if (isPacketFailOpen(playerId, now)) return "RECENT_PACKET_ERROR";
        if (attacker == null) return "NO_RUNTIME_SNAPSHOT";
        if (attacker.hasBypass()) return "BYPASS_PERMISSION";
        if (!attacker.isAlive()) return "ATTACKER_NOT_ALIVE";
        if (attacker.isCreativeOrSpectator()) return "ATTACKER_GAME_MODE";
        if (attacker.isInVehicle()) return "ATTACKER_IN_VEHICLE";
        if (target != null && !sameWorld(attacker, target)) return "WORLD_MISMATCH";
        if (target != null && !target.isAlive()) return "TARGET_NOT_ALIVE";
        if (target != null && target.isCreativeOrSpectator()) return "TARGET_GAME_MODE";
        if (target != null && target.isInVehicle()) return "TARGET_IN_VEHICLE";
        return "EXEMPT_CONTEXT";
    }

    private boolean isTemporarilyExempt(UUID playerId, long now) {
        Long expires = temporaryExemptions.get(playerId);
        if (expires == null) return false;
        if (now <= expires) return true;
        temporaryExemptions.remove(playerId, expires);
        return false;
    }

    private boolean isPacketFailOpen(UUID playerId, long now) {
        Long until = packetFailOpenUntil.get(playerId);
        if (until == null) return false;
        if (now <= until) return true;
        packetFailOpenUntil.remove(playerId, until);
        return false;
    }

    private boolean heartbeatDelayed(long now, ReachGuardConfig config,
                                     ServerHealthSnapshot health) {
        if (health == null) return true;
        long captured = health.getCapturedNanoTime();
        if (captured <= 0L || now <= captured) return false;
        long maximumDelay = config.getPermitExpireMs() * NANOS_PER_MILLISECOND;
        return now - captured >= maximumDelay;
    }

    private void maybeRecordEvidence(UUID attackerId, UUID targetId, int protocol,
                                     ReachGuardConfig config,
                                     ServerHealthSnapshot health,
                                     ReachGuardMode mode, ReachResult result,
                                     PlayerSession.AttackSnapshot attack,
                                     ViolationSnapshot violation,
                                     Aabb decisionCurrentTargetBox, long now) {
        boolean important = mode == ReachGuardMode.OBSERVE
                || result.getDecision().isViolation()
                || result.getDecision().isCancelled()
                || (result.getDecision() == ReachDecision.BYPASS
                && !"NON_PLAYER_OR_UNTRACKED_ENTITY".equals(result.getReason()))
                || (result.getReason() != null
                && (result.getReason().startsWith("PACKET_ERROR_")
                || result.getReason().startsWith("SYNC_")))
                || result.getDecision() == ReachDecision.ALLOW_UNVERIFIED;
        if (!important && !config.isLogValidAttacks()) return;

        PlayerRuntimeSnapshot runtime = attack == null
                ? runtimeSnapshots.get(attackerId) : attack.getRuntime();
        PlayerRuntimeSnapshot targetRuntime = targetId == null ? null
                : runtimeSnapshots.get(targetId);
        Aabb currentRuntimeBox = decisionCurrentTargetBox;
        if (currentRuntimeBox == null && attack == null && targetRuntime != null) {
            currentRuntimeBox = Aabb.player(targetRuntime.getX(),
                    targetRuntime.getY(), targetRuntime.getZ());
        }
        MovementFrame evidenceAttacker = null;
        Aabb evidenceTargetBox = null;
        List<MovementFrame> attackerCandidates = Collections.emptyList();
        List<TargetFrame> targetCandidates = Collections.emptyList();
        TargetFrame newestTargetState = null;
        TargetFrame staleAnchor = null;
        if (attack != null) {
            attackerCandidates = attack.getAttackerCandidates();
            for (MovementFrame frame : attackerCandidates) {
                if (frame.getSequence() == result.getSelectedAttackerSequence()) {
                    evidenceAttacker = frame;
                    break;
                }
            }
            CandidateStateResolver.TargetResolution resolution =
                    attack.getTargetResolution();
            targetCandidates = resolution.getCandidates();
            newestTargetState = resolution.getNewest();
            staleAnchor = resolution.getStale();
            TargetFrame selectedTarget = null;
            for (TargetFrame frame : targetCandidates) {
                if (frame.getStateSequence() == result.getSelectedTargetSequence()) {
                    selectedTarget = frame;
                    break;
                }
            }
            if (selectedTarget == null && staleAnchor != null
                    && staleAnchor.getStateSequence()
                    == result.getSelectedTargetSequence()) {
                selectedTarget = staleAnchor;
            }
            if (selectedTarget == null && newestTargetState != null
                    && newestTargetState.getStateSequence()
                    == result.getSelectedTargetSequence()) {
                selectedTarget = newestTargetState;
            }
            if (selectedTarget != null) {
                evidenceTargetBox = selectedTarget.getBoundingBox();
            } else if (result.getSelectedTargetSequence() == -10L) {
                // staleResult adds this synthetic candidate to the measurement.
                evidenceTargetBox = currentRuntimeBox;
            }
        } else {
            // DamageEventGuard evidence has no packet measurement pair, but the
            // runtime target still explains which Bukkit entity was cancelled.
            evidenceTargetBox = currentRuntimeBox;
        }

        boolean unsupportedProtocol = protocol >= 0
                && !config.supportsProtocol(protocol);
        boolean heartbeatStalled = evidenceHeartbeatStalled(health, config, now);
        List<String> exceptions = evidenceExceptions(result, attack, runtime,
                currentRuntimeBox, config, health, mode, protocol,
                unsupportedProtocol, heartbeatStalled, now);
        double vlExcess = evidenceVlExcess(result, config);
        double vlAdded = evidenceVlAdded(result, config, vlExcess);
        int evidenceTargetEntityId = attack != null ? attack.getTargetEntityId()
                : targetRuntime == null ? -1 : targetRuntime.getEntityId();
        EvidenceRecord record = EvidenceRecord.builder()
                .attacker(attackerId, displayName(attackerId))
                .target(targetId, displayName(targetId))
                .protocol(protocol)
                .mode(mode)
                .configuredMode(config.getMode())
                .result(result)
                .violationLevels(violation.getReachVl(), violation.getStaleVl(),
                        violation.getInvalidEntityVl())
                .network(runtime == null ? -1 : runtime.getPing(),
                        attack == null ? -1 : (int) Math.round(attack.getJitterMs()))
                .server(health == null ? Double.NaN : health.getTps(),
                        health == null ? Double.NaN : health.getLastTickDurationMs(),
                        health == null ? -1L : health.getServerTick())
                .sequences(attack == null ? -1L : attack.getAttackSequence(),
                        result.getSelectedAttackerSequence(),
                        result.getSelectedTargetSequence())
                .geometry(evidenceAttacker, evidenceTargetBox,
                        config.getTotalExpansion())
                .reachConfiguration(config.getBaseReach(),
                        config.getHitboxExpansion(), config.getGeometryEpsilon())
                .violationComputation(vlExcess, vlAdded,
                        config.getViolationBase(), config.getExcessMultiplier(),
                        config.getMaxExcessAddition())
                .attackerCandidates(attackerCandidates, now)
                .targetCandidates(targetCandidates, now)
                .newestTargetState(newestTargetState, now)
                .staleAnchor(staleAnchor, now)
                .currentRuntimeCandidate(evidenceTargetEntityId, targetId,
                        currentRuntimeBox)
                .unverifiedContext(unsupportedProtocol, heartbeatStalled, exceptions)
                .targetTeleportGrace(attack != null
                        && attack.isTargetTeleportGrace())
                .context(evidenceTargetEntityId,
                        attack != null && attack.isGrace(),
                        attack != null && attack.hasRecentKnockback(),
                        attack == null ? -1L : attack.getTransactionRttMs(),
                        attack == null ? -1L : attack.getPendingSyncDelayMs())
                .build();
        if (shouldRetainForInspection(result)) rememberEvidence(attackerId, record);
        if (config.isLoggingEnabled()) evidenceLogger.log(record);
    }

    static boolean shouldRetainForInspection(ReachResult result) {
        return result != null && result.getDecision() != ReachDecision.ALLOW;
    }

    static double evidenceVlExcess(ReachResult result,
                                   ReachGuardConfig config) {
        return ReachViolationPolicy.excess(result, config.getBaseReach());
    }

    static double evidenceVlAdded(ReachResult result,
                                  ReachGuardConfig config,
                                  double vlExcess) {
        if (!ReachViolationPolicy.records(result)) {
            return 0.0D;
        }
        return ReachViolationPolicy.addition(vlExcess, config.getViolationBase(),
                config.getExcessMultiplier(), config.getMaxExcessAddition());
    }

    private static boolean evidenceHeartbeatStalled(ServerHealthSnapshot health,
                                                     ReachGuardConfig config,
                                                     long now) {
        if (health == null) return false;
        long captured = health.getCapturedNanoTime();
        return captured > 0L && now > captured
                && now - captured >= config.getMaximumTickTimeMs()
                * NANOS_PER_MILLISECOND;
    }

    private static List<String> evidenceExceptions(ReachResult result,
            PlayerSession.AttackSnapshot attack, PlayerRuntimeSnapshot runtime,
            Aabb currentRuntimeBox, ReachGuardConfig config,
            ServerHealthSnapshot health, ReachGuardMode effectiveMode, int protocol,
            boolean unsupportedProtocol, boolean heartbeatStalled, long now) {
        List<String> exceptions = new ArrayList<String>();
        if (result != null && result.getDecision() == ReachDecision.ALLOW_UNVERIFIED) {
            addEvidenceException(exceptions, "RESULT_" + safeEvidenceTag(
                    result.getReason()));
        }
        if (unsupportedProtocol) {
            addEvidenceException(exceptions, "UNSUPPORTED_PROTOCOL");
        }
        if (config.getMode() == ReachGuardMode.STRICT
                && !config.supportsProtocol(protocol)
                && effectiveMode == ReachGuardMode.OBSERVE) {
            addEvidenceException(exceptions, "STRICT_PROTOCOL_DOWNGRADE");
        }
        if (health == null) {
            addEvidenceException(exceptions, "NO_SERVER_HEALTH");
        } else {
            if (health.getTps() < config.getMinimumTpsForStrict()) {
                addEvidenceException(exceptions, "LOW_TPS");
            }
            if (health.hasRecentStall()) {
                addEvidenceException(exceptions, "RECENT_SERVER_STALL");
            }
            if (health.getLastTickDurationMs() >= config.getMaximumTickTimeMs()) {
                addEvidenceException(exceptions, "LONG_SERVER_TICK");
            }
        }
        if (heartbeatStalled) {
            addEvidenceException(exceptions, "HEARTBEAT_STALLED");
        }
        if (attack == null) {
            if (result != null
                    && result.getDecision() == ReachDecision.ALLOW_UNVERIFIED) {
                addEvidenceException(exceptions, "NO_ATTACK_SNAPSHOT");
            }
            return exceptions;
        }
        if (attack.isGrace()) {
            addEvidenceException(exceptions, "ATTACKER_TELEPORT_GRACE");
        }
        if (attack.isTargetTeleportGrace()) {
            addEvidenceException(exceptions, "TARGET_TELEPORT_GRACE");
        }
        if (runtime == null) {
            addEvidenceException(exceptions, "NO_RUNTIME_SNAPSHOT");
        } else {
            if (!runtime.isAlive()) {
                addEvidenceException(exceptions, "ATTACKER_NOT_ALIVE");
            }
            if (runtime.getPing() > config.getExtremePingObserveThreshold()) {
                addEvidenceException(exceptions, "EXTREME_PING");
            } else if (runtime.getPing() > 200) {
                addEvidenceException(exceptions, "PING_OVER_200");
            }
        }
        if (health != null && health.getServerTick()
                <= attack.getTargetSpawnTick() + config.getSpawnGraceTicks()) {
            addEvidenceException(exceptions, "TARGET_SPAWN_GRACE");
        }
        if (attack.getAttackerCandidates().isEmpty()) {
            addEvidenceException(exceptions, "NO_VALID_ATTACKER_STATE");
        }
        CandidateStateResolver.TargetResolution target = attack.getTargetResolution();
        if (target.getCandidates().isEmpty()) {
            addEvidenceException(exceptions, "NO_RECENT_TARGET_STATE");
        }
        if (!target.hasConfirmed()) {
            addEvidenceException(exceptions, "NO_CONFIRMED_TARGET_STATE");
        }
        if (target.hasMixedConfirmation()) {
            addEvidenceException(exceptions, "MIXED_TARGET_CONFIRMATION");
        }
        TargetFrame newest = target.getNewest();
        if (!target.isCurrentConfirmed() && newest != null
                && newest.ageMillis(now) > config.getMaxRewindMs()) {
            addEvidenceException(exceptions, "TARGET_STATE_TOO_OLD");
        } else if (!target.isCurrentConfirmed() && newest != null
                && newest.ageMillis(now) > 150L) {
            addEvidenceException(exceptions, "TARGET_STATE_OLDER_THAN_150MS");
        }
        if (target.isCurrentConfirmed() && newest != null
                && currentRuntimeBox != null
                && evidenceBoxesMoved(newest.getBoundingBox(), currentRuntimeBox)
                >= config.getStaleMovementDistance()) {
            addEvidenceException(exceptions, "CURRENT_TARGET_DIVERGENCE");
        }
        if (attack.hasRecentKnockback()) {
            addEvidenceException(exceptions, "RECENT_KNOCKBACK");
        }
        if (attack.getJitterMs() > config.getMaximumJitterMs()) {
            addEvidenceException(exceptions, "EXCESSIVE_JITTER");
        }
        if (attack.getPendingSyncDelayMs() > config.getMaxRewindMs()) {
            addEvidenceException(exceptions, "STALE_PENDING_SYNC");
        }
        return exceptions;
    }

    private static double evidenceBoxesMoved(Aabb first, Aabb second) {
        if (first == null || second == null) return Double.POSITIVE_INFINITY;
        Vec3 a = first.centerAtFeet();
        Vec3 b = second.centerAtFeet();
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static void addEvidenceException(List<String> exceptions, String value) {
        if (value != null && !exceptions.contains(value)) exceptions.add(value);
    }

    private static String safeEvidenceTag(String value) {
        return value == null || value.trim().isEmpty() ? "UNSPECIFIED" : value;
    }

    private void rememberEvidence(UUID attackerId, EvidenceRecord record) {
        if (attackerId == null || record == null) return;
        ArrayDeque<EvidenceRecord> history = inspectionHistory.get(attackerId);
        if (history == null) {
            ArrayDeque<EvidenceRecord> created = new ArrayDeque<EvidenceRecord>();
            ArrayDeque<EvidenceRecord> raced = inspectionHistory.putIfAbsent(
                    attackerId, created);
            history = raced == null ? created : raced;
        }
        synchronized (history) {
            history.addFirst(record);
            while (history.size() > INSPECTION_HISTORY_SIZE) history.removeLast();
        }
    }

    private void queueAlert(UUID attackerId, UUID targetId, ReachResult result,
                            ViolationSnapshot violation, ReachGuardConfig config,
                            long now) {
        ViolationCategory category = violationCategory(result);
        double categoryVl = categoryViolationLevel(violation, category);
        if (!config.isAlertsEnabled()
                || categoryVl < Math.max(config.getAlertMinimumVl(),
                config.getAlertVl())) return;
        Long previous = lastAlertAt.get(attackerId);
        long cooldown = config.getAlertCooldownMs() * NANOS_PER_MILLISECOND;
        if (previous != null && now - previous < cooldown) return;
        lastAlertAt.put(attackerId, now);
        String message = ChatColor.RED + "[ReachGuard] " + ChatColor.WHITE
                + displayName(attackerId) + ChatColor.GRAY + " -> "
                + displayName(targetId) + ChatColor.GRAY + " "
                + result.getDecision() + " reach=" + format(result.getMeasuredReach())
                + "/" + format(result.getAllowedReach()) + " "
                + result.getReliability() + " " + category.name()
                + "-VL=" + format(categoryVl);
        if (categoryVl >= config.getDetailedAlertVl()) {
            PlayerRuntimeSnapshot runtime = runtimeSnapshots.get(attackerId);
            message += ChatColor.DARK_GRAY + " ping="
                    + (runtime == null ? "n/a" : runtime.getPing() + "ms")
                    + " age=" + result.getSnapshotAgeMs() + "ms candidates="
                    + result.getAttackerCandidateCount() + 'x'
                    + result.getTargetCandidateCount();
        }
        mainThreadActions.offer(MainThreadAction.alert(message));
    }

    private void queueDebugCancellation(UUID attackerId, UUID targetId,
                                        ReachResult result,
                                        ReachGuardConfig config) {
        if (!config.isDebugEnabled()) return;
        long now = System.nanoTime();
        Long previous = lastDebugAt.get(attackerId);
        if (previous != null && now - previous < DEBUG_MESSAGE_COOLDOWN_NANOS) return;
        lastDebugAt.put(attackerId, now);
        String filter = config.getDebugTargetPlayer();
        if (filter != null && !filter.trim().isEmpty()
                && !displayName(attackerId).equalsIgnoreCase(filter.trim())) return;
        String message = ChatColor.DARK_RED + "[ReachGuard DEBUG] "
                + ChatColor.RED + "hit disabled: " + displayName(attackerId)
                + " -> " + displayName(targetId) + " reason=" + result.getReason()
                + " reach=" + format(result.getMeasuredReach())
                + "/" + format(result.getAllowedReach());
        mainThreadActions.offer(MainThreadAction.debug(message));
    }

    private void queuePunishmentIfNeeded(UUID playerId, ReachResult result,
                                         ViolationSnapshot violation,
                                         ReachGuardConfig config) {
        if (!config.isKickEnabled()
                || categoryViolationLevel(violation, violationCategory(result))
                < config.getKickVl()
                || !punishmentQueued.add(playerId)) return;
        String command = config.getKickCommand().replace("%player%", displayName(playerId));
        mainThreadActions.offer(MainThreadAction.command(command));
    }

    private static ViolationCategory violationCategory(ReachResult result) {
        return ReachViolationPolicy.category(result);
    }

    private static double categoryViolationLevel(ViolationSnapshot snapshot,
                                                 ViolationCategory category) {
        if (snapshot == null || category == null) return 0.0D;
        switch (category) {
            case STALE:
                return snapshot.getStaleVl();
            case INVALID_ENTITY:
                return snapshot.getInvalidEntityVl();
            case REACH:
            default:
                return snapshot.getReachVl();
        }
    }

    private void tick() {
        if (shuttingDown) return;
        long now = System.nanoTime();
        ReachGuardConfig config = configuration.get();
        int nextTick = serverHealth.getServerTick() + 1;
        long elapsedNanos = lastTickNanoTime == 0L ? 50L * NANOS_PER_MILLISECOND
                : Math.max(0L, now - lastTickNanoTime);
        long elapsedMs = elapsedNanos / NANOS_PER_MILLISECOND;
        lastTickNanoTime = now;
        double sampleTps = elapsedNanos <= 0L ? 20.0D
                : Math.min(20.0D, 1_000_000_000.0D / elapsedNanos);
        smoothedTps = smoothedTps * 0.90D + sampleTps * 0.10D;
        if (elapsedNanos >= config.getMaximumTickTimeMs()
                * NANOS_PER_MILLISECOND) {
            recentStallUntilNanoTime = now + RECENT_STALL_GRACE_NANOS;
        }
        serverHealth = new ServerHealthSnapshot(nextTick, smoothedTps, elapsedMs,
                now <= recentStallUntilNanoTime, now);
        permits.cleanup(nextTick, now);
        cleanupTargetResyncCooldowns(now);
        reportDroppedLogs(now);
        reportEvidenceErrors(now);
        cleanupRetainedOfflineState(now);

        Set<UUID> online = new HashSet<UUID>();
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        for (Player player : players) {
            online.add(player.getUniqueId());
            lastSeenNanoTimes.put(player.getUniqueId(), now);
            PlayerRuntimeSnapshot runtime = updateRuntime(player, nextTick);
            PlayerSession session = sessions.get(player.getUniqueId());
            if (session != null) session.updateRuntime(runtime);
        }
        for (Map.Entry<UUID, PlayerRuntimeSnapshot> entry
                : new ArrayList<Map.Entry<UUID, PlayerRuntimeSnapshot>>(
                runtimeSnapshots.entrySet())) {
            if (!online.contains(entry.getKey())) {
                runtimeSnapshots.remove(entry.getKey(), entry.getValue());
                playerEntityIds.remove(entry.getValue().getEntityId(), entry.getKey());
                playerEntityLastSeen.remove(entry.getValue().getEntityId());
                sessions.remove(entry.getKey());
                packetActive.remove(entry.getKey());
                permits.clearPlayer(entry.getKey());
            }
        }
        drainMainThreadActions();
    }

    private PlayerRuntimeSnapshot updateRuntime(Player player, int tick) {
        Location location = player.getLocation();
        GameMode gameMode = player.getGameMode();
        boolean creativeOrSpectator = gameMode == GameMode.CREATIVE
                || gameMode == GameMode.SPECTATOR;
        boolean alive = player.isOnline() && !player.isDead() && player.getHealth() > 0.0D;
        PlayerRuntimeSnapshot snapshot = new PlayerRuntimeSnapshot(player.getEntityId(),
                player.getWorld().getUID(), location.getX(), location.getY(),
                location.getZ(), alive, creativeOrSpectator,
                player.isInsideVehicle(), player.hasPermission("reachguard.bypass"),
                pingService.getPing(player), tick);
        PlayerRuntimeSnapshot previous = runtimeSnapshots.put(player.getUniqueId(), snapshot);
        if (previous != null && previous.getEntityId() != snapshot.getEntityId()) {
            playerEntityIds.remove(previous.getEntityId(), player.getUniqueId());
            playerEntityLastSeen.remove(previous.getEntityId());
        }
        rememberPlayerEntityIdentity(snapshot.getEntityId(), player.getUniqueId(),
                System.nanoTime(), true);
        playerNames.put(player.getUniqueId(), player.getName());
        return snapshot;
    }

    /**
     * Seeds targets whose initial spawn packet may have preceded the join
     * listener. They remain LOW/unverified until a real absolute spawn or
     * teleport packet establishes a trustworthy client-visible base position.
     */
    private void seedVisibleTargets(UUID viewerId, int protocol) {
        if (shuttingDown) return;
        Player viewer = Bukkit.getPlayer(viewerId);
        if (viewer == null || !viewer.isOnline()) return;
        long now = System.nanoTime();
        PlayerSession session = session(viewerId, protocol, now);
        if (session == null) return;
        int tick = serverHealth.getServerTick();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(viewer) || !target.getWorld().equals(viewer.getWorld())
                    || !viewer.canSee(target)
                    || target.getLocation().distanceSquared(viewer.getLocation())
                    > 256.0D * 256.0D
                    || !session.needsTargetResync(target.getEntityId())) continue;
            Location location = target.getLocation();
            session.seedTarget(target.getEntityId(), target.getUniqueId(),
                    location.getX(), location.getY(), location.getZ(), tick);
            rememberPlayerEntityIdentity(target.getEntityId(),
                    target.getUniqueId(), now, true);
            PacketBridge bridge = packetBridge;
            if (bridge != null) bridge.resyncPlayer(viewer, target);
        }
        for (Entity entity : viewer.getWorld().getEntities()) {
            if (!(entity instanceof Player) && entity.getLocation().distanceSquared(
                    viewer.getLocation()) <= 256.0D * 256.0D) {
                session.markNonPlayerEntity(entity.getEntityId());
            }
        }
    }

    /**
     * Re-establishes an absolute client-visible base after the bounded viewer
     * tracker evicted a still-online player. The triggering hit remains
     * fail-open; later hits become verifiable after the real teleport packet
     * and its Transaction marker are observed.
     */
    private void queueTargetResync(UUID viewerId, UUID targetId, int protocol,
                                   long now) {
        if (viewerId == null || targetId == null || viewerId.equals(targetId)) return;
        ResyncKey key = new ResyncKey(viewerId, targetId);
        if (pendingTargetResyncs.contains(key)
                || pendingTargetResyncs.size() >= MAX_TARGET_RESYNC_COOLDOWNS) {
            return;
        }
        if (!targetResyncCooldowns.containsKey(key)
                && targetResyncCooldowns.size() >= MAX_TARGET_RESYNC_COOLDOWNS) {
            return;
        }
        long nextAllowed = now + TARGET_RESYNC_COOLDOWN_NANOS;
        while (true) {
            Long previous = targetResyncCooldowns.get(key);
            if (previous != null && now < previous) return;
            if (previous == null) {
                if (targetResyncCooldowns.putIfAbsent(key, nextAllowed) == null) break;
            } else if (targetResyncCooldowns.replace(key, previous, nextAllowed)) {
                break;
            }
        }
        if (!pendingTargetResyncs.add(key)) return;
        mainThreadActions.offer(MainThreadAction.resync(viewerId, targetId, protocol));
    }

    private void resyncVisibleTarget(UUID viewerId, UUID targetId, int protocol) {
        if (shuttingDown || viewerId == null || targetId == null) return;
        Player viewer = Bukkit.getPlayer(viewerId);
        Player target = Bukkit.getPlayer(targetId);
        PlayerSession session = sessions.get(viewerId);
        if (viewer == null || target == null || session == null
                || session.getProtocol() != protocol
                || !viewer.isOnline() || !target.isOnline()
                || !viewer.getWorld().equals(target.getWorld())
                || !viewer.canSee(target)) return;
        Location location = target.getLocation();
        if (location.distanceSquared(viewer.getLocation())
                > TARGET_RESYNC_MAX_DISTANCE_SQUARED) return;
        long now = System.nanoTime();
        session.seedTarget(target.getEntityId(), targetId,
                location.getX(), location.getY(), location.getZ(),
                serverHealth.getServerTick());
        rememberPlayerEntityIdentity(target.getEntityId(), targetId, now, true);
        PacketBridge bridge = packetBridge;
        if (bridge == null) return;
        try {
            bridge.resyncPlayer(viewer, target);
        } catch (RuntimeException exception) {
            onPacketError(viewerId, viewer.getEntityId(), protocol,
                    "TARGET_RESYNC", exception.getClass().getName(),
                    exception.getMessage());
        }
    }

    private void drainMainThreadActions() {
        int processed = 0;
        MainThreadAction action;
        while (processed++ < 512 && (action = mainThreadActions.poll()) != null) {
            if (action.resyncViewerId != null) {
                ResyncKey key = new ResyncKey(action.resyncViewerId,
                        action.resyncTargetId);
                try {
                    resyncVisibleTarget(action.resyncViewerId,
                            action.resyncTargetId, action.resyncProtocol);
                } finally {
                    pendingTargetResyncs.remove(key);
                }
                continue;
            }
            if (action.seedViewerId != null) {
                seedVisibleTargets(action.seedViewerId, action.seedProtocol);
                continue;
            }
            if (action.command != null) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), action.command);
                continue;
            }
            if (action.logOnly || action.debug) {
                plugin.getLogger().info(ChatColor.stripColor(action.message));
            }
            if (action.logOnly) continue;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if ((player.hasPermission("reachguard.alerts")
                        || player.hasPermission("reachguard.admin"))
                        && !alertOptOut.contains(player.getUniqueId())) {
                    player.sendMessage(action.message);
                }
            }
        }
    }

    private AsyncEvidenceLogger createEvidenceLogger(ReachGuardConfig config) {
        File directory = new File(plugin.getDataFolder(), "reachguard-logs");
        return new AsyncEvidenceLogger(directory, config.getAsyncQueueSize(),
                config.getRetentionDays());
    }

    private void retireLogger(final AsyncEvidenceLogger logger,
                              final long reportedDrops,
                              final long reportedErrors) {
        if (logger == null) return;
        Thread retirement = new Thread(new Runnable() {
            @Override
            public void run() {
                logger.shutdown();
                reportRetiredLoggerHealth(logger, reportedDrops, reportedErrors);
            }
        }, "Poppy-ReachGuard-Logger-Retire");
        retirement.setDaemon(true);
        retirement.start();
    }

    private void reportRetiredLoggerHealth(AsyncEvidenceLogger logger,
                                           long reportedDrops,
                                           long reportedErrors) {
        long unreportedDrops = Math.max(0L,
                logger.getDroppedCount() - reportedDrops);
        long unreportedErrors = Math.max(0L,
                logger.getErrorCount() - reportedErrors);
        if (unreportedDrops > 0L || unreportedErrors > 0L) {
            plugin.getLogger().warning("ReachGuard retired evidence writer: "
                    + unreportedDrops + " unreported drop(s), "
                    + unreportedErrors + " unreported error(s).");
        }
    }

    private void reportDroppedLogs(long now) {
        AsyncEvidenceLogger logger = evidenceLogger;
        if (logger == null) return;
        long dropped = logger.getDroppedCount();
        if (dropped <= lastReportedDroppedLogs
                || now - lastDropReportNanoTime < DROP_REPORT_COOLDOWN_NANOS) return;
        long delta = dropped - lastReportedDroppedLogs;
        lastReportedDroppedLogs = dropped;
        lastDropReportNanoTime = now;
        plugin.getLogger().warning("ReachGuard evidence queue dropped " + delta
                + " record(s); total=" + dropped + '.');
    }

    private void reportEvidenceErrors(long now) {
        AsyncEvidenceLogger logger = evidenceLogger;
        if (logger == null) return;
        long errors = logger.getErrorCount();
        if (errors <= lastReportedEvidenceErrors
                || now - lastEvidenceErrorReportNanoTime
                < DROP_REPORT_COOLDOWN_NANOS) return;
        long delta = errors - lastReportedEvidenceErrors;
        lastReportedEvidenceErrors = errors;
        lastEvidenceErrorReportNanoTime = now;
        plugin.getLogger().warning("ReachGuard evidence writer encountered "
                + delta + " error(s); total=" + errors + '.');
    }

    private void cleanupRetainedOfflineState(long now) {
        if (now - lastRetentionCleanupNanoTime < RETENTION_CLEANUP_NANOS) return;
        lastRetentionCleanupNanoTime = now;
        for (Map.Entry<UUID, Long> entry : lastSeenNanoTimes.entrySet()) {
            UUID playerId = entry.getKey();
            if (runtimeSnapshots.containsKey(playerId)
                    || now - entry.getValue() <= OFFLINE_RETENTION_NANOS) continue;
            if (!lastSeenNanoTimes.remove(playerId, entry.getValue())) continue;
            playerNames.remove(playerId);
            inspectionHistory.remove(playerId);
            violations.reset(playerId);
        }
        for (Map.Entry<Integer, Long> entry : playerEntityLastSeen.entrySet()) {
            UUID identity = playerEntityIds.get(entry.getKey());
            if (identity == null) {
                playerEntityLastSeen.remove(entry.getKey(), entry.getValue());
                continue;
            }
            if (identity != null && runtimeSnapshots.containsKey(identity)) continue;
            if (now - entry.getValue() <= ENTITY_IDENTITY_RETENTION_NANOS
                    && playerEntityIds.size() <= MAX_PLAYER_ENTITY_IDENTITIES) {
                continue;
            }
            if (playerEntityLastSeen.remove(entry.getKey(), entry.getValue())) {
                playerEntityIds.remove(entry.getKey(), identity);
            }
        }
    }

    private void cleanupTargetResyncCooldowns(long now) {
        if (now - lastTargetResyncCleanupNanoTime
                < TARGET_RESYNC_CLEANUP_NANOS) return;
        lastTargetResyncCleanupNanoTime = now;
        purgeExpiredCooldowns(targetResyncCooldowns, now);
    }

    static <K> int purgeExpiredCooldowns(ConcurrentMap<K, Long> cooldowns,
                                         long now) {
        if (cooldowns == null || cooldowns.isEmpty()) return 0;
        int removed = 0;
        for (Map.Entry<K, Long> entry : cooldowns.entrySet()) {
            Long expiresAt = entry.getValue();
            if (expiresAt != null && now >= expiresAt
                    && cooldowns.remove(entry.getKey(), expiresAt)) {
                removed++;
            }
        }
        return removed;
    }

    private void removeResyncCooldowns(UUID playerId) {
        if (playerId == null) return;
        for (ResyncKey key : targetResyncCooldowns.keySet()) {
            if (playerId.equals(key.viewerId) || playerId.equals(key.targetId)) {
                targetResyncCooldowns.remove(key);
            }
        }
        for (ResyncKey key : pendingTargetResyncs) {
            if (playerId.equals(key.viewerId) || playerId.equals(key.targetId)) {
                pendingTargetResyncs.remove(key);
            }
        }
    }

    private void rememberPlayerEntityIdentity(int entityId, UUID playerId,
                                              long now, boolean authoritative) {
        if (playerId == null) return;
        if (!authoritative && !playerEntityIds.containsKey(entityId)
                && playerEntityIds.size() >= MAX_PLAYER_ENTITY_IDENTITIES) {
            return;
        }
        playerEntityIds.put(entityId, playerId);
        playerEntityLastSeen.put(entityId, now);
    }

    private static ReachResult simpleResult(ReachDecision decision, String reason,
                                            Reliability reliability,
                                            ReachGuardConfig config) {
        return new ReachResult(decision, Double.NaN, config.getFlagThreshold(),
                reliability, reason, 0L, 0L, 0, 0, 0L);
    }

    private static boolean sameWorld(PlayerRuntimeSnapshot first,
                                     PlayerRuntimeSnapshot second) {
        return first.getWorldId() != null && first.getWorldId().equals(second.getWorldId());
    }

    private static int graceTicks(ResetReason reason, ReachGuardConfig config) {
        switch (reason) {
            case RESPAWN:
                return config.getRespawnGraceTicks();
            case WORLD_CHANGE:
                return config.getWorldChangeGraceTicks();
            case TELEPORT:
            case POSITION_CORRECTION:
                return config.getTeleportGraceTicks();
            case DEATH:
            default:
                return config.getRespawnGraceTicks();
        }
    }

    private String displayName(UUID playerId) {
        if (playerId == null) return "unknown";
        String name = playerNames.get(playerId);
        return name == null ? playerId.toString().substring(0, 8) : name;
    }

    private static String format(double value) {
        return Double.isNaN(value) || Double.isInfinite(value)
                ? "n/a" : String.format(Locale.ROOT, "%.3f", value);
    }

    private static String safe(String value) {
        if (value == null) return "";
        String compact = value.replace('\n', ' ').replace('\r', ' ');
        return compact.length() > 240 ? compact.substring(0, 240) : compact;
    }

    public enum ResetReason {
        TELEPORT,
        POSITION_CORRECTION,
        RESPAWN,
        WORLD_CHANGE,
        DEATH
    }

    public static final class PlayerStatus {
        private final UUID playerId;
        private final String name;
        private final int protocol;
        private final int ping;
        private final long transactionRttMs;
        private final double jitterMs;
        private final int trackedEntities;
        private final boolean packetActive;
        private final boolean temporarilyExempt;
        private final ViolationSnapshot violation;
        private final ReachResult lastResult;

        private PlayerStatus(UUID playerId, String name, int protocol, int ping,
                             long transactionRttMs, double jitterMs,
                             int trackedEntities, boolean packetActive,
                             boolean temporarilyExempt,
                             ViolationSnapshot violation, ReachResult lastResult) {
            this.playerId = playerId;
            this.name = name;
            this.protocol = protocol;
            this.ping = ping;
            this.transactionRttMs = transactionRttMs;
            this.jitterMs = jitterMs;
            this.trackedEntities = trackedEntities;
            this.packetActive = packetActive;
            this.temporarilyExempt = temporarilyExempt;
            this.violation = violation;
            this.lastResult = lastResult;
        }

        public UUID getPlayerId() { return playerId; }
        public String getName() { return name; }
        public int getProtocol() { return protocol; }
        public int getPing() { return ping; }
        public long getTransactionRttMs() { return transactionRttMs; }
        public double getJitterMs() { return jitterMs; }
        public int getTrackedEntities() { return trackedEntities; }
        public boolean isPacketActive() { return packetActive; }
        public boolean isTemporarilyExempt() { return temporarilyExempt; }
        public ViolationSnapshot getViolation() { return violation; }
        public ReachResult getLastResult() { return lastResult; }
    }

    private static final class ResyncKey {
        private final UUID viewerId;
        private final UUID targetId;

        private ResyncKey(UUID viewerId, UUID targetId) {
            this.viewerId = viewerId;
            this.targetId = targetId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ResyncKey)) return false;
            ResyncKey key = (ResyncKey) other;
            return viewerId.equals(key.viewerId) && targetId.equals(key.targetId);
        }

        @Override
        public int hashCode() {
            return 31 * viewerId.hashCode() + targetId.hashCode();
        }
    }

    private static final class MainThreadAction {
        private final String message;
        private final boolean debug;
        private final boolean logOnly;
        private final String command;
        private final UUID seedViewerId;
        private final int seedProtocol;
        private final UUID resyncViewerId;
        private final UUID resyncTargetId;
        private final int resyncProtocol;

        private MainThreadAction(String message, boolean debug,
                                 boolean logOnly, String command,
                                 UUID seedViewerId, int seedProtocol,
                                 UUID resyncViewerId, UUID resyncTargetId,
                                 int resyncProtocol) {
            this.message = message;
            this.debug = debug;
            this.logOnly = logOnly;
            this.command = command;
            this.seedViewerId = seedViewerId;
            this.seedProtocol = seedProtocol;
            this.resyncViewerId = resyncViewerId;
            this.resyncTargetId = resyncTargetId;
            this.resyncProtocol = resyncProtocol;
        }

        private static MainThreadAction alert(String message) {
            return new MainThreadAction(message, false, false, null, null, -1,
                    null, null, -1);
        }

        private static MainThreadAction debug(String message) {
            return new MainThreadAction(message, true, false, null, null, -1,
                    null, null, -1);
        }

        private static MainThreadAction log(String message) {
            return new MainThreadAction(message, false, true, null, null, -1,
                    null, null, -1);
        }

        private static MainThreadAction command(String command) {
            return new MainThreadAction(null, false, false, command, null, -1,
                    null, null, -1);
        }

        private static MainThreadAction seed(UUID viewerId, int protocol) {
            return new MainThreadAction(null, false, false, null,
                    viewerId, protocol, null, null, -1);
        }

        private static MainThreadAction resync(UUID viewerId, UUID targetId,
                                               int protocol) {
            return new MainThreadAction(null, false, false, null,
                    null, -1, viewerId, targetId, protocol);
        }
    }
}
