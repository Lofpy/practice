package com.poppy.practice.reach;

import org.bukkit.configuration.ConfigurationSection;

public final class ReachGuardConfig {
    private final ReachGuardMode mode;
    private final String packetLibrary;
    private final double baseReach;
    private final double hitboxExpansion;
    private final double geometryEpsilon;
    private final double flagThreshold;
    private final double protectHighCancel;
    private final double protectMediumCancel;
    private final double strictHighCancel;
    private final double strictMediumCancel;
    private final int attackerHistorySize;
    private final int targetHistorySize;
    private final int maxTargetsPerViewer;
    private final long historyRetentionMs;
    private final int spawnGraceTicks;
    private final int teleportGraceTicks;
    private final int respawnGraceTicks;
    private final int worldChangeGraceTicks;
    private final int syncIntervalTicks;
    private final long maxRewindMs;
    private final double staleMovementDistance;
    private final int extremePingObserveThreshold;
    private final int maximumJitterMs;
    private final double minimumTpsForStrict;
    private final String lowTpsAction;
    private final long maximumTickTimeMs;
    private final boolean damageGuardEnabled;
    private final boolean cancelWithoutPermit;
    private final long permitExpireMs;
    private final double violationBase;
    private final double excessMultiplier;
    private final double maxExcessAddition;
    private final double decayPerSecond;
    private final double alertVl;
    private final double detailedAlertVl;
    private final double kickVl;
    private final boolean kickEnabled;
    private final String kickCommand;
    private final boolean alertsEnabled;
    private final long alertCooldownMs;
    private final double alertMinimumVl;
    private final boolean loggingEnabled;
    private final int retentionDays;
    private final int asyncQueueSize;
    private final boolean debugEnabled;
    private final String debugTargetPlayer;
    private final boolean logValidAttacks;

    private ReachGuardConfig(ConfigurationSection root) {
        String configuredMode = text(root, "mode", "OBSERVE");
        mode = ReachGuardMode.parse(configuredMode, null);
        if (mode == null) {
            throw invalid("mode", configuredMode);
        }
        packetLibrary = text(root, "compatibility.packet-library", "NMS_DIRECT")
                .trim().toUpperCase(java.util.Locale.ROOT);
        if (!"NMS_DIRECT".equals(packetLibrary)) {
            throw invalid("compatibility.packet-library", packetLibrary);
        }
        baseReach = decimal(root, "reach.base-reach", 3.00D, 1.0D, 6.0D);
        hitboxExpansion = decimal(root, "reach.client-hitbox-expansion", 0.0D, 0.0D, 1.0D);
        geometryEpsilon = decimal(root, "reach.geometry-epsilon", 0.0D, 0.0D, 0.1D);
        flagThreshold = decimal(root, "reach.flag-threshold", baseReach, baseReach, 6.0D);
        protectHighCancel = decimal(root, "reach.protect.high-confidence-cancel",
                flagThreshold, flagThreshold, 6.0D);
        protectMediumCancel = decimal(root, "reach.protect.medium-confidence-cancel",
                protectHighCancel, protectHighCancel, 6.0D);
        strictHighCancel = decimal(root, "reach.strict.high-confidence-cancel",
                flagThreshold, flagThreshold, 6.0D);
        strictMediumCancel = decimal(root, "reach.strict.medium-confidence-cancel",
                strictHighCancel, strictHighCancel, 6.0D);
        attackerHistorySize = integer(root, "tracking.attacker-history-size", 10, 2, 64);
        targetHistorySize = integer(root, "tracking.target-history-size", 8, 2, 64);
        maxTargetsPerViewer = integer(root, "tracking.max-targets-per-viewer", 64, 4, 512);
        historyRetentionMs = integer(root, "tracking.history-retention-ms",
                1000, 300, 10000);
        spawnGraceTicks = integer(root, "tracking.spawn-grace-ticks", 5, 0, 100);
        teleportGraceTicks = integer(root, "tracking.teleport-grace-ticks", 5, 0, 100);
        respawnGraceTicks = integer(root, "tracking.respawn-grace-ticks", 10, 0, 200);
        worldChangeGraceTicks = integer(root, "tracking.world-change-grace-ticks", 10, 0, 200);
        syncIntervalTicks = integer(root, "lag-compensation.sync-interval-ticks", 1, 1, 20);
        maxRewindMs = integer(root, "lag-compensation.max-rewind-ms", 300, 50, 1000);
        staleMovementDistance = decimal(root, "lag-compensation.stale-movement-distance",
                0.60D, 0.1D, 10.0D);
        extremePingObserveThreshold = integer(root,
                "lag-compensation.extreme-ping-observe-threshold", 350, 50, 5000);
        maximumJitterMs = integer(root, "lag-compensation.maximum-jitter-ms", 100, 0, 1000);
        minimumTpsForStrict = decimal(root, "server-health.minimum-tps-for-strict",
                18.0D, 1.0D, 20.0D);
        lowTpsAction = text(root, "server-health.low-tps-action", "OBSERVE")
                .trim().toUpperCase(java.util.Locale.ROOT);
        if (!"OBSERVE".equals(lowTpsAction)) {
            throw invalid("server-health.low-tps-action", lowTpsAction);
        }
        maximumTickTimeMs = integer(root, "server-health.maximum-tick-time-ms", 250, 50, 5000);
        damageGuardEnabled = bool(root, "damage-event-guard.enabled", true);
        cancelWithoutPermit = bool(root, "damage-event-guard.cancel-without-permit", true);
        permitExpireMs = integer(root, "damage-event-guard.permit-expire-ms", 150, 20, 1000);
        violationBase = decimal(root, "violation.base", 1.0D, 0.0D, 20.0D);
        excessMultiplier = decimal(root, "violation.excess-multiplier", 10.0D, 0.0D, 100.0D);
        maxExcessAddition = decimal(root, "violation.max-excess-addition", 4.0D, 0.0D, 100.0D);
        decayPerSecond = decimal(root, "violation.decay-per-second", 0.20D, 0.0D, 20.0D);
        alertVl = decimal(root, "violation.alert-vl", 3.0D, 0.0D, 1000.0D);
        detailedAlertVl = decimal(root, "violation.detailed-alert-vl", 8.0D, alertVl, 1000.0D);
        kickVl = decimal(root, "violation.kick-vl", 15.0D, detailedAlertVl, 1000.0D);
        kickEnabled = bool(root, "punishment.kick-enabled", false);
        kickCommand = text(root, "punishment.kick-command",
                "kick %player% Unfair advantage detected");
        alertsEnabled = bool(root, "alerts.enabled", true);
        alertCooldownMs = integer(root, "alerts.cooldown-ms", 500, 0, 60000);
        alertMinimumVl = decimal(root, "alerts.minimum-vl", 3.0D, 0.0D, 1000.0D);
        loggingEnabled = bool(root, "logging.enabled", true);
        retentionDays = integer(root, "logging.retention-days", 30, 1, 3650);
        asyncQueueSize = integer(root, "logging.async-queue-size", 8192, 128, 65536);
        debugEnabled = bool(root, "debug.enabled", false);
        debugTargetPlayer = text(root, "debug.target-player", "");
        logValidAttacks = bool(root, "debug.log-valid-attacks", false);
    }

    public static ReachGuardConfig load(ConfigurationSection pluginConfig) {
        ConfigurationSection root = pluginConfig == null ? null
                : pluginConfig.getConfigurationSection("reachguard");
        return new ReachGuardConfig(root);
    }

    private static String text(ConfigurationSection root, String path, String fallback) {
        return root == null ? fallback : root.getString(path, fallback);
    }

    private static boolean bool(ConfigurationSection root, String path, boolean fallback) {
        return root == null ? fallback : root.getBoolean(path, fallback);
    }

    private static int integer(ConfigurationSection root, String path, int fallback,
                               int minimum, int maximum) {
        int value = root == null || !root.contains(path)
                ? fallback : root.getInt(path, fallback);
        if (value < minimum || value > maximum) throw invalid(path, value);
        return value;
    }

    private static double decimal(ConfigurationSection root, String path, double fallback,
                                  double minimum, double maximum) {
        double value = root == null || !root.contains(path)
                ? fallback : root.getDouble(path, fallback);
        if (Double.isNaN(value) || Double.isInfinite(value)
                || value < minimum || value > maximum) {
            throw invalid(path, value);
        }
        return value;
    }

    private static IllegalArgumentException invalid(String path, Object value) {
        return new IllegalArgumentException("Invalid reachguard." + path + ": " + value);
    }

    public ReachGuardMode getMode() { return mode; }
    public String getPacketLibrary() { return packetLibrary; }
    public double getBaseReach() { return baseReach; }
    public double getHitboxExpansion() { return hitboxExpansion; }
    public double getGeometryEpsilon() { return geometryEpsilon; }
    public double getTotalExpansion() { return hitboxExpansion + geometryEpsilon; }
    public double getFlagThreshold() { return flagThreshold; }
    public double getProtectHighCancel() { return protectHighCancel; }
    public double getProtectMediumCancel() { return protectMediumCancel; }
    public double getStrictHighCancel() { return strictHighCancel; }
    public double getStrictMediumCancel() { return strictMediumCancel; }
    public int getAttackerHistorySize() { return attackerHistorySize; }
    public int getTargetHistorySize() { return targetHistorySize; }
    public int getMaxTargetsPerViewer() { return maxTargetsPerViewer; }
    public long getHistoryRetentionMs() { return historyRetentionMs; }
    public int getSpawnGraceTicks() { return spawnGraceTicks; }
    public int getTeleportGraceTicks() { return teleportGraceTicks; }
    public int getRespawnGraceTicks() { return respawnGraceTicks; }
    public int getWorldChangeGraceTicks() { return worldChangeGraceTicks; }
    public int getSyncIntervalTicks() { return syncIntervalTicks; }
    public long getMaxRewindMs() { return maxRewindMs; }
    public double getStaleMovementDistance() { return staleMovementDistance; }
    public int getExtremePingObserveThreshold() { return extremePingObserveThreshold; }
    public int getMaximumJitterMs() { return maximumJitterMs; }
    public double getMinimumTpsForStrict() { return minimumTpsForStrict; }
    public String getLowTpsAction() { return lowTpsAction; }
    public long getMaximumTickTimeMs() { return maximumTickTimeMs; }
    public boolean isDamageGuardEnabled() { return damageGuardEnabled; }
    public boolean isCancelWithoutPermit() { return cancelWithoutPermit; }
    public long getPermitExpireMs() { return permitExpireMs; }
    public double getViolationBase() { return violationBase; }
    public double getExcessMultiplier() { return excessMultiplier; }
    public double getMaxExcessAddition() { return maxExcessAddition; }
    public double getDecayPerSecond() { return decayPerSecond; }
    public double getAlertVl() { return alertVl; }
    public double getDetailedAlertVl() { return detailedAlertVl; }
    public double getKickVl() { return kickVl; }
    public boolean isKickEnabled() { return kickEnabled; }
    public String getKickCommand() { return kickCommand; }
    public boolean isAlertsEnabled() { return alertsEnabled; }
    public long getAlertCooldownMs() { return alertCooldownMs; }
    public double getAlertMinimumVl() { return alertMinimumVl; }
    public boolean isLoggingEnabled() { return loggingEnabled; }
    public int getRetentionDays() { return retentionDays; }
    public int getAsyncQueueSize() { return asyncQueueSize; }
    public boolean isDebugEnabled() { return debugEnabled; }
    public String getDebugTargetPlayer() { return debugTargetPlayer; }
    public boolean isLogValidAttacks() { return logValidAttacks; }

    public boolean supportsProtocol(int protocol) {
        return protocol == 5 || protocol == 47;
    }

    public double cancellationThreshold(ReachGuardMode effectiveMode,
                                        Reliability reliability) {
        if (effectiveMode == ReachGuardMode.STRICT) {
            return reliability == Reliability.HIGH ? strictHighCancel : strictMediumCancel;
        }
        return reliability == Reliability.HIGH ? protectHighCancel : protectMediumCancel;
    }
}
