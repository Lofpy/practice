package com.poppy.practice.chatter;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.service.DamageDebugService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.time.OffsetDateTime;

public final class ChatterKbService implements KnockbackObservationApi {
    private static final long COMBAT_CORRELATION_NS = 50000000L;

    private final PracticePlugin plugin;
    private final DamageDebugService damageDebugService;
    private final PlayerStateRegistry registry = new PlayerStateRegistry();
    private final ChatterDetector detector = new ChatterDetector();
    private final SprintStateTracker sprintTracker = new SprintStateTracker();
    private final VelocityEstimator estimator = new VelocityEstimator();
    private final InternalPacketGuard internalPacketGuard = new InternalPacketGuard();
    private final WindowSafety windowSafety = new WindowSafety();
    private final AtomicLong windowIds = new AtomicLong();
    private final TelemetryService telemetry;
    private volatile ChatterKbConfig config;
    private volatile ChatterKbMode mode;
    private volatile double currentTps = 20.0D;
    private long previousTickNs;

    public ChatterKbService(PracticePlugin plugin, DamageDebugService damageDebugService) {
        this.plugin = plugin;
        this.damageDebugService = damageDebugService;
        this.config = ChatterKbConfig.load(plugin.getConfig());
        this.mode = config.mode;
        this.telemetry = new TelemetryService(plugin, config);
        Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                updateMainThreadSnapshots();
            }
        }, 1L, 1L);
    }

    public void handleJoin(UUID playerId, int entityId) {
        registry.getOrCreate(playerId, entityId);
    }

    public void handleQuit(UUID playerId) {
        registry.remove(playerId);
    }

    public void reset(UUID playerId) {
        PlayerCombatState state = registry.get(playerId);
        if (state == null) return;
        synchronized (state) {
            state.resetCombat();
        }
    }

    public void handleMovement(UUID playerId, int entityId, Integer protocol,
                               Double x, Double y, Double z, boolean onGround, long now) {
        PlayerCombatState state = registry.getOrCreate(playerId, entityId);
        synchronized (state) {
            state.recordPacket(now);
            state.clientProtocol = protocol == null ? -1 : protocol.intValue();
            state.clientFrameSequence++;
            state.lastMovementNs = now;
            sprintTracker.advanceClientFrame(state, now);
            if (x != null && y != null && z != null) {
                state.positionDiscontinuity = false;
                state.previousPosition = state.currentPosition;
                state.currentPosition = new PositionSample(x, y, z, onGround,
                        state.clientFrameSequence);
                if (state.previousPosition != null) {
                    Vec3 delta = state.currentPosition.deltaFrom(state.previousPosition);
                    state.positionDiscontinuity = delta.horizontalLength() > 1.5D
                            || Math.abs(delta.getY()) > 1.5D;
                }
            } else if (state.currentPosition != null) {
                state.previousPosition = state.currentPosition;
                state.currentPosition = new PositionSample(state.currentPosition.x,
                        state.currentPosition.y, state.currentPosition.z, onGround,
                        state.clientFrameSequence);
            }
            detector.decay(state, now, config);
            KnockbackWindow window = state.kbWindow;
            if (window != null) {
                if (isExpired(state, window, now)) {
                    state.closeWindow(WindowCloseReason.EXPIRED);
                    state.lastSkipReason = SkipReason.WINDOW_EXPIRED;
                } else {
                    Vec3 advanced = estimator.advance(window.baselineVelocity, onGround, config);
                    if (state.currentPosition != null && state.previousPosition != null
                            && !state.positionDiscontinuity) {
                        advanced = estimator.blendObservation(advanced,
                                state.currentPosition.deltaFrom(state.previousPosition),
                                window.initialVelocity, config);
                    }
                    window.baselineVelocity = advanced;
                }
            }
        }
    }

    public void handleSprint(UUID playerId, int entityId, boolean sprinting, long now) {
        PlayerCombatState state = registry.getOrCreate(playerId, entityId);
        synchronized (state) {
            state.recordPacket(now);
            sprintTracker.update(state, sprinting, now);
        }
    }

    public AttackDecision handleAttack(UUID playerId, int entityId, int protocol,
                                       int targetEntityId, long now,
                                       CorrectionTransport transport) {
        PlayerCombatState state = registry.getOrCreate(playerId, entityId);
        synchronized (state) {
            state.recordPacket(now);
            state.clientProtocol = protocol;
            ChatterKbConfig current = config;
            ChatterKbMode currentMode = mode;
            if (!current.supportsProtocol(protocol)) {
                state.lastSkipReason = SkipReason.UNSUPPORTED_PROTOCOL;
                if (state.debugEnabled) {
                    record(state, "ATTACK_SKIPPED",
                            "reason=UNSUPPORTED_PROTOCOL,protocol=" + protocol, false);
                }
                return AttackDecision.allowed(SkipReason.UNSUPPORTED_PROTOCOL);
            }
            if (currentMode == ChatterKbMode.OFF) {
                state.lastSkipReason = SkipReason.MODE_OFF;
                return AttackDecision.allowed(SkipReason.MODE_OFF);
            }

            boolean eligible = sprintTracker.isSlowdownEligible(state, current);
            ChatterDetector.DetectionResult detection = detector.classify(state,
                    protocol, targetEntityId, now, eligible, current);
            AttackSample sample = detection.sample;
            sprintTracker.applyLocalAttackReset(state, eligible, now);
            boolean confirmedDuplicate = sample.classification
                    == AttackClassification.DUPLICATE
                    && state.chatterState == ChatterState.ACTIVE;
            boolean disableHit = shouldDisableConfirmedDuplicate(currentMode,
                    current.cancelConfirmedDuplicates, confirmedDuplicate);

            if (current.includeAttackSamples || state.debugEnabled) {
                record(state, "ATTACK", attackDetails(state, sample, "OBSERVED"), false);
            }

            KnockbackWindow window = state.kbWindow;
            if (window != null && isExpired(state, window, now)) {
                state.closeWindow(WindowCloseReason.EXPIRED);
                state.lastSkipReason = SkipReason.WINDOW_EXPIRED;
                window = null;
            }

            if (currentMode == ChatterKbMode.STRICT_NORMALIZE && eligible) {
                CorrectionResult result = attemptCorrection(state, sample, now,
                        transport, currentMode);
                if (disableHit) {
                    return disabledHit(state, sample, result.reason);
                }
                return AttackDecision.allowed(result.reason);
            }

            if (sample.classification == AttackClassification.PRIMARY
                    || sample.classification == AttackClassification.UNRESOLVED) {
                if (eligible && window != null) {
                    window.baselineVelocity = window.baselineVelocity.multiplyHorizontal(0.6D);
                    window.acceptedSlowdownCount++;
                }
                state.lastSkipReason = sample.classification == AttackClassification.UNRESOLVED
                        ? SkipReason.NOT_ACTIVE : SkipReason.NOT_DUPLICATE;
                return AttackDecision.allowed(state.lastSkipReason);
            }

            if (!eligible && current.requireSlowdownEligible) {
                state.lastSkipReason = SkipReason.SLOWDOWN_NOT_ELIGIBLE;
                record(state, "CORRECTION_SKIPPED",
                        attackDetails(state, sample, "SLOWDOWN_NOT_ELIGIBLE"), true);
                if (disableHit) {
                    return disabledHit(state, sample, SkipReason.SLOWDOWN_NOT_ELIGIBLE);
                }
                return AttackDecision.allowed(SkipReason.SLOWDOWN_NOT_ELIGIBLE);
            }
            if (state.chatterState != ChatterState.ACTIVE) {
                state.lastSkipReason = SkipReason.NOT_ACTIVE;
                return AttackDecision.allowed(SkipReason.NOT_ACTIVE);
            }

            CorrectionResult result = attemptCorrection(state, sample, now,
                    transport, currentMode);
            if (disableHit) {
                return disabledHit(state, sample, result.reason);
            }
            return AttackDecision.allowed(result.reason);
        }
    }

    public void handleVelocity(UUID playerId, int entityId, int protocol,
                               Vec3 velocity, long now) {
        PlayerCombatState state = registry.getOrCreate(playerId, entityId);
        synchronized (state) {
            state.recordPacket(now);
            state.clientProtocol = protocol;
            if (internalPacketGuard.consume(state)) {
                state.lastSkipReason = SkipReason.INTERNAL_PACKET;
                record(state, "VELOCITY_SKIPPED", "reason=INTERNAL_PACKET", true);
                return;
            }
            ChatterKbConfig current = config;
            if (!current.supportsProtocol(protocol)) {
                state.lastSkipReason = SkipReason.UNSUPPORTED_PROTOCOL;
                return;
            }
            if (velocity == null || !velocity.isFinite()
                    || velocity.horizontalLength() < current.minHorizontalVelocity) {
                state.lastSkipReason = SkipReason.ESTIMATOR_INVALID;
                return;
            }
            boolean correlated = state.lastCombatDamageNs > 0L
                    && now - state.lastCombatDamageNs >= 0L
                    && now - state.lastCombatDamageNs <= COMBAT_CORRELATION_NS;
            if (current.requireCombatCorrelation && !correlated) {
                state.lastSkipReason = SkipReason.NOT_COMBAT_VELOCITY;
                return;
            }
            openWindow(state, state.lastCombatAttackerId, velocity, now);
            state.lastCombatDamageNs = 0L;
            record(state, "KB_WINDOW_OPEN", "id=" + state.kbWindow.id
                    + ",initial=" + velocity, true);
        }
    }

    public void recordCombatDamage(UUID victimId, UUID attackerId, long now) {
        PlayerCombatState state = registry.get(victimId);
        if (state == null) return;
        state.lastCombatAttackerId = attackerId;
        state.lastCombatDamageNs = now;
    }

    @Override
    public void recordCombatKnockback(UUID victimId, UUID attackerId,
                                      Vec3 finalVelocity, KnockbackCause cause,
                                      long serverTick, long nanoTime) {
        if (victimId == null || finalVelocity == null || !finalVelocity.isFinite()) return;
        PlayerCombatState state = registry.get(victimId);
        if (state == null) return;
        synchronized (state) {
            openWindow(state, attackerId, finalVelocity, nanoTime);
            record(state, "KB_WINDOW_API", "cause=" + cause + ",initial=" + finalVelocity, true);
        }
    }

    public void reloadConfiguration() {
        ChatterKbConfig loaded = ChatterKbConfig.load(plugin.getConfig());
        config = loaded;
        mode = loaded.mode;
        telemetry.reload(loaded);
        registry.resetAll();
    }

    public void setMode(ChatterKbMode mode) {
        if (mode == null) throw new IllegalArgumentException("mode");
        this.mode = mode;
        registry.resetAll();
    }

    public ChatterKbMode getMode() {
        return mode;
    }

    public String getSupportedProtocolsDescription() {
        return config.targetProtocols.toString();
    }

    public StatusSnapshot status(UUID playerId) {
        PlayerCombatState state = registry.get(playerId);
        if (state == null) return null;
        synchronized (state) {
            KnockbackWindow window = state.kbWindow;
            return new StatusSnapshot(mode, state.clientProtocol, state.chatterScore,
                    state.chatterState.name(),
                    state.pingMs, window == null ? null : window.id,
                    state.lastSkipReason.name(), state.debugEnabled,
                    state.debugRing == null ? 0 : state.debugRing.size(),
                    telemetry.getDroppedCount());
        }
    }

    public boolean setDebug(UUID playerId, boolean enabled) {
        PlayerCombatState state = registry.get(playerId);
        if (state == null) return false;
        synchronized (state) {
            state.setDebug(enabled, config.debugRingSize);
        }
        return true;
    }

    public boolean export(final UUID playerId, String playerName, int seconds,
                          final TelemetryService.ExportCallback callback) {
        PlayerCombatState state = registry.get(playerId);
        if (state == null) return false;
        List<String> all;
        synchronized (state) {
            all = state.debugSnapshot();
        }
        long cutoff = System.currentTimeMillis() - Math.max(1, seconds) * 1000L;
        List<String> selected = new ArrayList<String>();
        for (String line : all) {
            String[] fields = line.split(",", 2);
            try {
                if (fields.length > 0
                        && OffsetDateTime.parse(fields[0]).toInstant().toEpochMilli() >= cutoff) {
                    selected.add(line);
                }
            } catch (RuntimeException ignored) {
                selected.add(line);
            }
        }
        telemetry.export(playerId, playerName, selected, new TelemetryService.ExportCallback() {
            @Override
            public void complete(final File file, final String error) {
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        callback.complete(file, error);
                    }
                });
            }
        });
        return true;
    }

    public void shutdown() {
        telemetry.shutdown();
        registry.clear();
    }

    private CorrectionResult attemptCorrection(PlayerCombatState state, AttackSample sample,
                                               long now, CorrectionTransport transport,
                                               ChatterKbMode currentMode) {
        KnockbackWindow window = state.kbWindow;
        SkipReason failure = safetyFailure(state, window, now);
        if (failure != SkipReason.NONE) {
            state.lastSkipReason = failure;
            record(state, "CORRECTION_SKIPPED", attackDetails(state, sample, failure.name()), true);
            return new CorrectionResult(false, failure);
        }
        int projectedFrames = estimator.estimatedOneWayFrames(state.pingMs, config);
        boolean onGround = state.currentPosition != null && state.currentPosition.onGround;
        Vec3 expected = estimator.project(window.baselineVelocity, onGround,
                projectedFrames, config);
        Vec3 corrected = estimator.correction(expected, window.initialVelocity, config);
        if (corrected == null) {
            state.lastSkipReason = SkipReason.DIRECTION_MISMATCH;
            record(state, "CORRECTION_SKIPPED",
                    attackDetails(state, sample, "DIRECTION_MISMATCH"), true);
            return new CorrectionResult(false, SkipReason.DIRECTION_MISMATCH);
        }
        window.lastProjectedVelocity = expected;
        if (currentMode == ChatterKbMode.DETECT_ONLY) {
            state.lastSkipReason = SkipReason.DETECT_ONLY;
            record(state, "SHADOW_CORRECTION", attackDetails(state, sample,
                    "DETECT_ONLY,expected=" + expected + ",wouldSend=" + corrected), true);
            return new CorrectionResult(false, SkipReason.DETECT_ONLY);
        }
        internalPacketGuard.mark(state);
        boolean sent;
        try {
            sent = transport.send(state.entityId, corrected);
        } catch (RuntimeException exception) {
            sent = false;
        }
        if (!sent) {
            internalPacketGuard.rollback(state);
            state.lastSkipReason = SkipReason.ESTIMATOR_INVALID;
            return new CorrectionResult(false, SkipReason.ESTIMATOR_INVALID);
        }
        window.correctionCount++;
        window.lastCorrectionNs = now;
        state.lastSkipReason = SkipReason.NONE;
        record(state, "CORRECTION_SENT", attackDetails(state, sample,
                "sent=" + corrected + ",baseline=" + expected), true);
        if (window.correctionCount >= config.maxCorrections) {
            state.closeWindow(WindowCloseReason.CORRECTION_LIMIT);
        }
        return new CorrectionResult(true, SkipReason.NONE);
    }

    private SkipReason safetyFailure(PlayerCombatState state, KnockbackWindow window, long now) {
        return windowSafety.failure(state, window, now, currentTps, config);
    }

    private boolean isExpired(PlayerCombatState state, KnockbackWindow window, long now) {
        return windowSafety.isExpired(state, window, now, config);
    }

    private void openWindow(PlayerCombatState state, UUID attackerId, Vec3 velocity, long now) {
        if (state.kbWindow != null) state.kbWindow.closeReason = WindowCloseReason.SUPERSEDED;
        boolean onGround = state.currentPosition != null && state.currentPosition.onGround;
        state.kbWindow = new KnockbackWindow(windowIds.incrementAndGet(), attackerId,
                now, state.clientFrameSequence, velocity, state.currentPosition, onGround);
    }

    private String attackDetails(PlayerCombatState state, AttackSample sample, String result) {
        return "mode=" + mode + ",protocol=" + sample.protocol + ",result=" + result
                + ",class=" + sample.classification
                + ",burst=" + sample.burstType + ",gapMs="
                + formatMs(sample.gapFromPreviousNs) + ",frameDelta=" + sample.frameDelta
                + ",sprintBefore=" + sample.predictedSprintBefore
                + ",eligible=" + sample.slowdownEligible + ",score="
                + String.format(Locale.ROOT, "%.2f", state.chatterScore)
                + ",state=" + state.chatterState + ",target=" + sample.targetEntityId
                + ",kb=" + (sample.kbWindowId == null ? "none" : sample.kbWindowId)
                + ",ping=" + state.pingMs;
    }

    private AttackDecision disabledHit(PlayerCombatState state, AttackSample sample,
                                       SkipReason correctionReason) {
        String details = attackDetails(state, sample,
                "HIT_DISABLED_ACTIVE_DUPLICATE,correction=" + correctionReason);
        record(state, "HIT_DISABLED", details, true);
        sendDebugChat(state.playerId, details);
        return AttackDecision.cancelled(correctionReason);
    }

    private String formatMs(long nanoseconds) {
        if (nanoseconds == Long.MAX_VALUE) return "none";
        return String.format(Locale.ROOT, "%.3f", nanoseconds / 1000000.0D);
    }

    private void record(PlayerCombatState state, String event, String details, boolean important) {
        telemetry.record(state, event, details, important);
    }

    private void sendDebugChat(final UUID playerId, final String details) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                PlayerCombatState state = registry.get(playerId);
                boolean playerDebug = state != null && state.debugEnabled;
                if (!playerDebug && !damageDebugService.isEnabled()) return;
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.GOLD
                            + "ChatterKB" + ChatColor.DARK_GRAY + "] " + ChatColor.RED
                            + "Hit disabled " + ChatColor.GRAY + details);
                }
            }
        });
    }

    private void updateMainThreadSnapshots() {
        long now = System.nanoTime();
        if (previousTickNs != 0L) {
            double instant = Math.min(20.0D, 1000000000.0D / Math.max(1L, now - previousTickNs));
            currentTps = currentTps * 0.9D + instant * 0.1D;
        }
        previousTickNs = now;
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerCombatState state = registry.getOrCreate(player.getUniqueId(), player.getEntityId());
            state.playerName = player.getName();
            state.pingMs = Math.max(0,
                    ((org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer) player).getHandle().ping);
            ItemStack held = player.getItemInHand();
            state.heldKnockbackLevel = held == null ? 0
                    : held.getEnchantmentLevel(Enchantment.KNOCKBACK);
        }
    }

    public KnockbackObservationApi getObservationApi() {
        return this;
    }

    static boolean shouldDisableConfirmedDuplicate(ChatterKbMode mode,
                                                    boolean cancellationEnabled,
                                                    boolean confirmedDuplicate) {
        return (mode == ChatterKbMode.CHATTER_ONLY
                || mode == ChatterKbMode.STRICT_NORMALIZE)
                && cancellationEnabled && confirmedDuplicate;
    }

    public interface CorrectionTransport {
        boolean send(int entityId, Vec3 velocity);
    }

    public static final class AttackDecision {
        private final boolean cancel;
        private final SkipReason reason;

        private AttackDecision(boolean cancel, SkipReason reason) {
            this.cancel = cancel;
            this.reason = reason;
        }

        static AttackDecision allowed(SkipReason reason) {
            return new AttackDecision(false, reason);
        }

        static AttackDecision cancelled(SkipReason reason) {
            return new AttackDecision(true, reason);
        }

        public boolean shouldCancel() {
            return cancel;
        }

        public String getReason() {
            return reason.name();
        }
    }

    private static final class CorrectionResult {
        final boolean sent;
        final SkipReason reason;

        private CorrectionResult(boolean sent, SkipReason reason) {
            this.sent = sent;
            this.reason = reason;
        }
    }

    public static final class StatusSnapshot {
        public final ChatterKbMode mode;
        public final int protocol;
        public final double score;
        public final String state;
        public final int pingMs;
        public final Long windowId;
        public final String lastSkipReason;
        public final boolean debug;
        public final int bufferedDebugEvents;
        public final long telemetryDropped;

        StatusSnapshot(ChatterKbMode mode, int protocol, double score,
                       String state, int pingMs,
                       Long windowId, String lastSkipReason, boolean debug,
                       int bufferedDebugEvents, long telemetryDropped) {
            this.mode = mode;
            this.protocol = protocol;
            this.score = score;
            this.state = state;
            this.pingMs = pingMs;
            this.windowId = windowId;
            this.lastSkipReason = lastSkipReason;
            this.debug = debug;
            this.bufferedDebugEvents = bufferedDebugEvents;
            this.telemetryDropped = telemetryDropped;
        }
    }
}
