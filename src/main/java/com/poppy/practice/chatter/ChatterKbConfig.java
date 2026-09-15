package com.poppy.practice.chatter;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ChatterKbConfig {
    private static final String ROOT = "chatter-kb.";

    final ChatterKbMode mode;
    final Set<Integer> targetProtocols;
    final boolean requireCombatCorrelation;
    final long historyNs;
    final long sameStrongNs;
    final long sameMediumNs;
    final long sameWeakNs;
    final long boundaryStrongNs;
    final long boundaryWeakNs;
    final boolean boundaryEnabled;
    final boolean boundaryRequireArmed;
    final double activationScore;
    final int activationMinBursts;
    final long activationWindowNs;
    final double deactivationScore;
    final long deactivationNoBurstNs;
    final double decayPerSecond;
    final double networkBatchPenalty;
    final boolean cancelConfirmedDuplicates;
    final int maxClientFrames;
    final long maxWindowNs;
    final double minHorizontalVelocity;
    final double correctionScale;
    final int maxCorrections;
    final long minCorrectionGapNs;
    final double maxDirectionDegrees;
    final boolean pingProjection;
    final int maxPingMs;
    final boolean requireSlowdownEligible;
    final boolean knockbackEnchantSupport;
    final double airDrag;
    final double groundSlipperiness;
    final double gravity;
    final double verticalDrag;
    final double observationAlpha;
    final double minTps;
    final boolean disableOnUnsupportedProtocol;
    final boolean skipOnPositionDiscontinuity;
    final boolean loggingEnabled;
    final boolean includeAttackSamples;
    final boolean includeCorrections;
    final int debugRingSize;
    final int asyncQueueSize;
    final long rotateBytes;
    final int retainFiles;

    private ChatterKbConfig(FileConfiguration config) {
        mode = ChatterKbMode.parse(config.getString(ROOT + "mode", "CHATTER_ONLY"));
        targetProtocols = targetProtocols(config);
        requireCombatCorrelation = config.getBoolean(ROOT + "target.require-combat-correlation", true);
        historyNs = millis(positive(config.getLong(ROOT + "detection.history-ms", 3000L), "detection.history-ms"));
        sameStrongNs = millis(positive(config.getLong(ROOT + "detection.same-frame-gap-ms.strong", 5L), "same-frame-gap-ms.strong"));
        sameMediumNs = millis(positive(config.getLong(ROOT + "detection.same-frame-gap-ms.medium", 12L), "same-frame-gap-ms.medium"));
        sameWeakNs = millis(positive(config.getLong(ROOT + "detection.same-frame-gap-ms.weak", 20L), "same-frame-gap-ms.weak"));
        boundaryStrongNs = millis(positive(config.getLong(ROOT + "detection.boundary-gap-ms.strong", 8L), "boundary-gap-ms.strong"));
        boundaryWeakNs = millis(positive(config.getLong(ROOT + "detection.boundary-gap-ms.weak", 12L), "boundary-gap-ms.weak"));
        requireAscending(sameStrongNs, sameMediumNs, sameWeakNs, "same-frame gaps");
        requireAscending(boundaryStrongNs, boundaryWeakNs, boundaryWeakNs, "boundary gaps");
        boundaryEnabled = config.getBoolean(ROOT + "detection.boundary-detection.enabled", true);
        boundaryRequireArmed = config.getBoolean(ROOT + "detection.boundary-detection.require-armed", true);
        activationScore = positive(config.getDouble(ROOT + "detection.activation.score", 5.5D), "activation.score");
        activationMinBursts = positive(config.getInt(ROOT + "detection.activation.min-bursts", 2), "activation.min-bursts");
        activationWindowNs = millis(positive(config.getLong(ROOT + "detection.activation.window-ms", 2000L), "activation.window-ms"));
        deactivationScore = nonNegative(config.getDouble(ROOT + "detection.deactivation.score", 2.0D), "deactivation.score");
        deactivationNoBurstNs = millis(positive(config.getLong(ROOT + "detection.deactivation.no-burst-ms", 5000L), "deactivation.no-burst-ms"));
        decayPerSecond = nonNegative(config.getDouble(ROOT + "detection.score-decay-per-second", 1.0D), "score-decay-per-second");
        networkBatchPenalty = range(config.getDouble(ROOT + "detection.network-batch-penalty", 0.5D), 0.0D, 1.0D, "network-batch-penalty");
        cancelConfirmedDuplicates = config.getBoolean(ROOT + "detection.cancel-confirmed-duplicate-hits", true);
        maxClientFrames = positive(config.getInt(ROOT + "compensation.max-client-frames", 4), "max-client-frames");
        maxWindowNs = millis(positive(config.getLong(ROOT + "compensation.max-window-ms", 250L), "max-window-ms"));
        minHorizontalVelocity = positive(config.getDouble(ROOT + "compensation.min-horizontal-velocity", 0.08D), "min-horizontal-velocity");
        correctionScale = range(config.getDouble(ROOT + "compensation.correction-scale", 0.96D), 0.01D, 1.0D, "correction-scale");
        maxCorrections = positive(config.getInt(ROOT + "compensation.max-corrections-per-window", 2), "max-corrections-per-window");
        minCorrectionGapNs = millis(nonNegative(config.getLong(ROOT + "compensation.min-correction-gap-ms", 40L), "min-correction-gap-ms"));
        maxDirectionDegrees = range(config.getDouble(ROOT + "compensation.max-direction-deviation-degrees", 35.0D), 0.0D, 180.0D, "max-direction-deviation-degrees");
        pingProjection = config.getBoolean(ROOT + "compensation.ping-projection", true);
        maxPingMs = positive(config.getInt(ROOT + "compensation.max-ping-ms", 220), "max-ping-ms");
        requireSlowdownEligible = config.getBoolean(ROOT + "compensation.require-slowdown-eligible", true);
        knockbackEnchantSupport = config.getBoolean(ROOT + "compensation.knockback-enchant-support", false);
        airDrag = range(config.getDouble(ROOT + "physics.air-horizontal-drag", 0.91D), 0.0D, 1.0D, "air-horizontal-drag");
        groundSlipperiness = range(config.getDouble(ROOT + "physics.default-ground-slipperiness", 0.60D), 0.0D, 1.0D, "default-ground-slipperiness");
        gravity = nonNegative(config.getDouble(ROOT + "physics.gravity", 0.08D), "gravity");
        verticalDrag = range(config.getDouble(ROOT + "physics.vertical-drag", 0.98D), 0.0D, 1.0D, "vertical-drag");
        observationAlpha = range(config.getDouble(ROOT + "physics.observation-blend-alpha", 0.35D), 0.0D, 1.0D, "observation-blend-alpha");
        minTps = range(config.getDouble(ROOT + "safety.min-tps", 18.0D), 0.0D, 20.0D, "min-tps");
        disableOnUnsupportedProtocol = config.getBoolean(ROOT + "safety.disable-on-unsupported-protocol", true);
        skipOnPositionDiscontinuity = config.getBoolean(ROOT + "safety.skip-on-position-discontinuity", true);
        loggingEnabled = config.getBoolean(ROOT + "logging.enabled", true);
        includeAttackSamples = config.getBoolean(ROOT + "logging.include-attack-samples", false);
        includeCorrections = config.getBoolean(ROOT + "logging.include-corrections", true);
        debugRingSize = positive(config.getInt(ROOT + "logging.debug-ring-size", 256), "debug-ring-size");
        asyncQueueSize = positive(config.getInt(ROOT + "logging.async-queue-size", 4096), "async-queue-size");
        rotateBytes = positive(config.getLong(ROOT + "logging.rotate-mb", 32L), "rotate-mb") * 1024L * 1024L;
        retainFiles = positive(config.getInt(ROOT + "logging.retain-files", 7), "retain-files");
    }

    public static ChatterKbConfig load(FileConfiguration config) {
        return new ChatterKbConfig(config);
    }

    boolean supportsProtocol(int protocol) {
        return targetProtocols.contains(protocol);
    }

    private static Set<Integer> targetProtocols(FileConfiguration config) {
        List<Integer> configured = config.getIntegerList(ROOT + "target.protocols");
        LinkedHashSet<Integer> protocols = new LinkedHashSet<Integer>();
        if (!configured.isEmpty()) {
            for (Integer protocol : configured) {
                if (protocol == null) {
                    throw invalid("target.protocols", "must not contain null values");
                }
                protocols.add(positive(protocol.intValue(), "target.protocols"));
            }
        } else if (config.contains(ROOT + "target.protocol")) {
            // Backward compatibility with the original single-protocol configuration.
            protocols.add(positive(config.getInt(ROOT + "target.protocol"),
                    "target.protocol"));
        } else {
            protocols.add(5);
            protocols.add(47);
        }
        if (protocols.isEmpty()) {
            throw invalid("target.protocols", "must contain at least one protocol");
        }
        return Collections.unmodifiableSet(protocols);
    }

    private static long millis(long value) {
        return value * 1000000L;
    }

    private static int positive(int value, String name) {
        if (value <= 0) throw invalid(name, "must be positive");
        return value;
    }

    private static long positive(long value, String name) {
        if (value <= 0L) throw invalid(name, "must be positive");
        return value;
    }

    private static double positive(double value, String name) {
        if (!(value > 0.0D) || !finite(value)) throw invalid(name, "must be positive and finite");
        return value;
    }

    private static long nonNegative(long value, String name) {
        if (value < 0L) throw invalid(name, "must not be negative");
        return value;
    }

    private static double nonNegative(double value, String name) {
        if (value < 0.0D || !finite(value)) throw invalid(name, "must not be negative");
        return value;
    }

    private static double range(double value, double minimum, double maximum, String name) {
        if (!finite(value) || value < minimum || value > maximum) {
            throw invalid(name, "must be between " + minimum + " and " + maximum);
        }
        return value;
    }

    private static void requireAscending(long first, long second, long third, String name) {
        if (first > second || second > third) throw invalid(name, "must be ascending");
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static IllegalArgumentException invalid(String path, String reason) {
        return new IllegalArgumentException("chatter-kb." + path + ' ' + reason);
    }
}
