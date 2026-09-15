package com.poppy.practice.reach;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe violation accounting with independent Reach, Stale and Invalid
 * Entity levels. Decay is applied lazily whenever a state is read or changed.
 */
public final class ViolationTracker {
    private static final double NANOS_PER_SECOND = 1000000000.0D;

    private final ConcurrentMap<UUID, State> states =
            new ConcurrentHashMap<UUID, State>();
    private volatile Settings settings;

    public ViolationTracker(ReachGuardConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        settings = settings(config);
    }

    public ViolationTracker(double baseViolation, double excessMultiplier,
                            double maxExcessAddition, double decayPerSecond) {
        settings = new Settings(baseViolation, excessMultiplier,
                maxExcessAddition, decayPerSecond);
    }

    public void updateConfiguration(ReachGuardConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        settings = settings(config);
    }

    public void updateConfiguration(double baseViolation, double excessMultiplier,
                                    double maxExcessAddition, double decayPerSecond) {
        settings = new Settings(baseViolation, excessMultiplier,
                maxExcessAddition, decayPerSecond);
    }

    /**
     * Records the violation represented by a validation result. Reach excess is
     * deliberately measured from baseReach, rather than from a more lenient
     * PROTECT cancellation threshold, matching the specification's VL examples.
     */
    public ViolationSnapshot record(UUID playerId, ReachResult result,
                                    double baseReach, long nowNanoTime) {
        if (result == null) {
            throw new IllegalArgumentException("result must not be null");
        }
        if (!ReachViolationPolicy.records(result)) {
            return snapshot(playerId, nowNanoTime);
        }
        return add(playerId, ReachViolationPolicy.category(result),
                ReachViolationPolicy.excess(result, baseReach), nowNanoTime);
    }

    /**
     * Adds one violation. The excess contribution follows the ReachGuard
     * formula for every category; callers normally pass zero for Stale and
     * Invalid Entity because the specification does not define an excess value
     * for those categories.
     */
    public ViolationSnapshot add(UUID playerId, ViolationCategory category,
                                 double excess, long nowNanoTime) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId must not be null");
        }
        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }
        State state = state(playerId, nowNanoTime);
        Settings current = settings;
        synchronized (state) {
            state.decay(nowNanoTime, current.decayPerSecond);
            double addition = current.addition(excess);
            state.add(category, addition);
            return state.snapshot(playerId, nowNanoTime);
        }
    }

    public ViolationSnapshot snapshot(UUID playerId, long nowNanoTime) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId must not be null");
        }
        State state = states.get(playerId);
        if (state == null) {
            return new ViolationSnapshot(playerId, 0.0D, 0.0D,
                    0.0D, nowNanoTime);
        }
        Settings current = settings;
        synchronized (state) {
            state.decay(nowNanoTime, current.decayPerSecond);
            return state.snapshot(playerId, nowNanoTime);
        }
    }

    public void reset(UUID playerId) {
        if (playerId != null) {
            states.remove(playerId);
        }
    }

    public void clear() {
        states.clear();
    }

    public int trackedPlayerCount() {
        return states.size();
    }

    private State state(UUID playerId, long nowNanoTime) {
        State existing = states.get(playerId);
        if (existing != null) {
            return existing;
        }
        State created = new State(nowNanoTime);
        State raced = states.putIfAbsent(playerId, created);
        return raced == null ? created : raced;
    }

    private static Settings settings(ReachGuardConfig config) {
        return new Settings(config.getViolationBase(), config.getExcessMultiplier(),
                config.getMaxExcessAddition(), config.getDecayPerSecond());
    }

    private static final class Settings {
        private final double baseViolation;
        private final double excessMultiplier;
        private final double maxExcessAddition;
        private final double decayPerSecond;

        private Settings(double baseViolation, double excessMultiplier,
                         double maxExcessAddition, double decayPerSecond) {
            requireNonNegativeFinite(baseViolation, "baseViolation");
            requireNonNegativeFinite(excessMultiplier, "excessMultiplier");
            requireNonNegativeFinite(maxExcessAddition, "maxExcessAddition");
            requireNonNegativeFinite(decayPerSecond, "decayPerSecond");
            this.baseViolation = baseViolation;
            this.excessMultiplier = excessMultiplier;
            this.maxExcessAddition = maxExcessAddition;
            this.decayPerSecond = decayPerSecond;
        }

        private double addition(double excess) {
            return ReachViolationPolicy.addition(excess, baseViolation,
                    excessMultiplier, maxExcessAddition);
        }

        private static void requireNonNegativeFinite(double value, String name) {
            if (value < 0.0D || Double.isNaN(value) || Double.isInfinite(value)) {
                throw new IllegalArgumentException(name
                        + " must be non-negative and finite");
            }
        }
    }

    private static final class State {
        private double reachVl;
        private double staleVl;
        private double invalidEntityVl;
        private long lastUpdateNanoTime;

        private State(long nowNanoTime) {
            lastUpdateNanoTime = nowNanoTime;
        }

        private void decay(long nowNanoTime, double decayPerSecond) {
            if (nowNanoTime <= lastUpdateNanoTime) {
                return;
            }
            double seconds = (nowNanoTime - lastUpdateNanoTime) / NANOS_PER_SECOND;
            double amount = seconds * decayPerSecond;
            reachVl = Math.max(0.0D, reachVl - amount);
            staleVl = Math.max(0.0D, staleVl - amount);
            invalidEntityVl = Math.max(0.0D, invalidEntityVl - amount);
            lastUpdateNanoTime = nowNanoTime;
        }

        private void add(ViolationCategory category, double addition) {
            switch (category) {
                case REACH:
                    reachVl += addition;
                    break;
                case STALE:
                    staleVl += addition;
                    break;
                case INVALID_ENTITY:
                    invalidEntityVl += addition;
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported category " + category);
            }
        }

        private ViolationSnapshot snapshot(UUID playerId, long nowNanoTime) {
            return new ViolationSnapshot(playerId, reachVl, staleVl,
                    invalidEntityVl, nowNanoTime);
        }
    }
}
