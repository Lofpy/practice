package com.poppy.practice.reach;

import java.util.List;

public final class ReachValidationEngine {
    public ReachResult validate(PlayerSession.AttackSnapshot attack,
                                ReachGuardConfig config,
                                ReachGuardMode effectiveMode,
                                ServerHealthSnapshot serverHealth,
                                Aabb currentTargetBox,
                                long now) {
        if (attack == null || config == null || effectiveMode == null) {
            return result(ReachDecision.ALLOW_UNVERIFIED,
                    Double.POSITIVE_INFINITY,
                    config == null ? 0.0D : config.getFlagThreshold(),
                    Reliability.LOW, "INVALID_CONTEXT",
                    0L, 0L, 0, 0, 0L);
        }

        if (!attack.hasTarget()) {
            if (hasHardFailOpenCondition(attack, config, serverHealth, now)) {
                return result(ReachDecision.ALLOW_UNVERIFIED,
                        Double.POSITIVE_INFINITY, config.getFlagThreshold(),
                        Reliability.LOW, "INVALID_ENTITY_FAIL_OPEN", 0L, 0L,
                        attack.getAttackerCandidates().size(), 0, 0L);
            }
            ReachDecision decision = effectiveMode == ReachGuardMode.OBSERVE
                    ? ReachDecision.FLAG : ReachDecision.CANCEL_INVALID_ENTITY;
            return result(decision, Double.POSITIVE_INFINITY,
                    config.getFlagThreshold(), Reliability.MEDIUM,
                    "INVALID_ENTITY", newestAttacker(attack.getAttackerCandidates()),
                    0L, attack.getAttackerCandidates().size(), 0, 0L);
        }

        if (attack.isInvalidTarget()) {
            if (hasHardFailOpenCondition(attack, config, serverHealth, now)) {
                return result(ReachDecision.ALLOW_UNVERIFIED,
                        Double.POSITIVE_INFINITY, config.getFlagThreshold(),
                        Reliability.LOW, "INVALID_ENTITY_FAIL_OPEN", 0L, 0L,
                        attack.getAttackerCandidates().size(), 0, 0L);
            }
            ReachDecision invalidDecision = effectiveMode == ReachGuardMode.OBSERVE
                    ? ReachDecision.FLAG : ReachDecision.CANCEL_INVALID_ENTITY;
            return result(invalidDecision, Double.POSITIVE_INFINITY,
                    config.getFlagThreshold(), Reliability.MEDIUM,
                    "INVALID_ENTITY", 0L, 0L,
                    attack.getAttackerCandidates().size(), 0, 0L);
        }

        CandidateStateResolver.TargetResolution targetResolution =
                attack.getTargetResolution();
        List<MovementFrame> attackers = attack.getAttackerCandidates();
        List<TargetFrame> targets = targetResolution.getCandidates();
        Reliability reliability = reliability(attack, config, serverHealth,
                currentTargetBox, now);

        if (!hasHardFailOpenCondition(attack, config, serverHealth, now)) {
            ReachResult stale = staleResult(attack, config, effectiveMode,
                    currentTargetBox, attackers, targetResolution, now);
            if (stale != null) return stale;
        }

        if (targets.isEmpty()) {
            return result(ReachDecision.ALLOW_UNVERIFIED,
                    Double.POSITIVE_INFINITY, config.getFlagThreshold(), Reliability.LOW,
                    "NO_RECENT_TARGET_STATE", newestAttacker(attackers),
                    targetResolution.getNewest() == null ? 0L
                            : targetResolution.getNewest().getStateSequence(),
                    attackers.size(), 0, targetResolution.getNewest() == null ? 0L
                            : targetResolution.getNewest().ageMillis(now));
        }

        if (attackers.isEmpty()) {
            return result(ReachDecision.ALLOW_UNVERIFIED,
                    Double.POSITIVE_INFINITY, config.getFlagThreshold(), Reliability.LOW,
                    "NO_VALID_ATTACKER_STATE", 0L, targets.get(0).getStateSequence(),
                    0, targets.size(), targets.get(0).ageMillis(now));
        }

        Measurement measurement = measure(attackers, targets, config.getTotalExpansion());
        long age = measurement.target == null ? 0L : measurement.target.ageMillis(now);
        if (reliability == Reliability.LOW) {
            return result(ReachDecision.ALLOW_UNVERIFIED, measurement.distance,
                    config.getFlagThreshold(), reliability, "LOW_RELIABILITY",
                    sequence(measurement.attacker), sequence(measurement.target),
                    attackers.size(), targets.size(), age);
        }

        // The configured boundary is inclusive: with a 3.00 threshold,
        // 2.999... is legal and 3.000 or farther is subject to enforcement.
        if (measurement.distance < config.getFlagThreshold()) {
            return result(ReachDecision.ALLOW, measurement.distance,
                    config.getFlagThreshold(), reliability, "WITHIN_REACH",
                    sequence(measurement.attacker), sequence(measurement.target),
                    attackers.size(), targets.size(), age);
        }

        if (effectiveMode == ReachGuardMode.OBSERVE) {
            return result(ReachDecision.FLAG, measurement.distance,
                    config.getFlagThreshold(), reliability, "OBSERVE_ONLY",
                    sequence(measurement.attacker), sequence(measurement.target),
                    attackers.size(), targets.size(), age);
        }

        double threshold = config.cancellationThreshold(effectiveMode, reliability);
        ReachDecision decision = measurement.distance >= threshold
                ? ReachDecision.CANCEL_REACH : ReachDecision.FLAG;
        return result(decision, measurement.distance, threshold, reliability,
                decision == ReachDecision.CANCEL_REACH ? "REACH_EXCEEDED" : "BORDERLINE",
                sequence(measurement.attacker), sequence(measurement.target),
                attackers.size(), targets.size(), age);
    }

    private ReachResult staleResult(PlayerSession.AttackSnapshot attack,
                                    ReachGuardConfig config,
                                    ReachGuardMode mode,
                                    Aabb currentTargetBox,
                                    List<MovementFrame> attackers,
                                    CandidateStateResolver.TargetResolution resolution,
                                    long now) {
        TargetFrame stale = resolution.getStale();
        if (stale == null || currentTargetBox == null || attackers.isEmpty()
                || stale.ageMillis(now) <= config.getMaxRewindMs()
                || attack.getPendingSyncDelayMs() <= config.getMaxRewindMs()) {
            return null;
        }
        Vec3 oldCenter = stale.getBoundingBox().centerAtFeet();
        Vec3 currentCenter = currentTargetBox.centerAtFeet();
        double dx = oldCenter.getX() - currentCenter.getX();
        double dy = oldCenter.getY() - currentCenter.getY();
        double dz = oldCenter.getZ() - currentCenter.getZ();
        double moved = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (moved < config.getStaleMovementDistance()) return null;
        List<TargetFrame> currentCandidates = new java.util.ArrayList<TargetFrame>(
                resolution.getCandidates());
        currentCandidates.add(new TargetFrame(-10L, now, now,
                0, attack.getTargetEntityId(), attack.getTargetUuid(),
                currentTargetBox, TargetFrameStatus.CONFIRMED, false, true));
        Measurement current = measure(attackers, currentCandidates,
                config.getTotalExpansion());
        if (current.distance < config.getFlagThreshold()) return null;
        ReachDecision decision = mode == ReachGuardMode.OBSERVE
                ? ReachDecision.FLAG : ReachDecision.CANCEL_STALE_ATTACK;
        return result(decision, current.distance, config.getFlagThreshold(),
                Reliability.MEDIUM, "STALE_ATTACK", sequence(current.attacker),
                sequence(current.target), attackers.size(), currentCandidates.size(),
                stale.ageMillis(now));
    }

    private Reliability reliability(PlayerSession.AttackSnapshot attack,
                                    ReachGuardConfig config,
                                    ServerHealthSnapshot health,
                                    Aabb currentTargetBox, long now) {
        CandidateStateResolver.TargetResolution target = attack.getTargetResolution();
        if (hasHardFailOpenCondition(attack, config, health, now)
                || !target.hasConfirmed() || target.getNewest() == null
                || (!target.isCurrentConfirmed()
                && target.getNewest().ageMillis(now) > config.getMaxRewindMs())
                || (target.isCurrentConfirmed() && currentTargetBox != null
                && boxesMoved(target.getNewest().getBoundingBox(), currentTargetBox)
                >= config.getStaleMovementDistance())) {
            return Reliability.LOW;
        }
        if (target.hasMixedConfirmation() || attack.hasRecentKnockback()
                || attack.getJitterMs() > config.getMaximumJitterMs()
                || attack.getRuntime().getPing() > 200
                || (!target.isCurrentConfirmed()
                && target.getNewest().ageMillis(now) > 150L)) {
            return Reliability.MEDIUM;
        }
        return Reliability.HIGH;
    }

    private static double boxesMoved(Aabb first, Aabb second) {
        Vec3 a = first.centerAtFeet();
        Vec3 b = second.centerAtFeet();
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private boolean hasHardFailOpenCondition(PlayerSession.AttackSnapshot attack,
                                             ReachGuardConfig config,
                                             ServerHealthSnapshot health,
                                             long now) {
        return !config.supportsProtocol(attack.getProtocol()) || attack.isGrace()
                || attack.isTargetTeleportGrace()
                || attack.getRuntime() == null || !attack.getRuntime().isAlive()
                || attack.getRuntime().getPing()
                > config.getExtremePingObserveThreshold()
                || health == null
                || health.getServerTick() <= attack.getTargetSpawnTick()
                + config.getSpawnGraceTicks()
                || health.hasRecentStall()
                || health.getLastTickDurationMs() >= config.getMaximumTickTimeMs()
                || heartbeatStalled(health, config, now);
    }

    private static boolean heartbeatStalled(ServerHealthSnapshot health,
                                            ReachGuardConfig config, long now) {
        long captured = health.getCapturedNanoTime();
        return captured > 0L && now > captured
                && now - captured >= config.getMaximumTickTimeMs() * 1_000_000L;
    }

    static Measurement measure(List<MovementFrame> attackers,
                               List<TargetFrame> targets, double expansion) {
        double minimum = Double.POSITIVE_INFINITY;
        MovementFrame selectedAttacker = null;
        TargetFrame selectedTarget = null;
        for (MovementFrame attacker : attackers) {
            if (attacker == null || !attacker.isValid()) continue;
            for (TargetFrame target : targets) {
                if (target == null || !target.isValid()) continue;
                double distance = ReachGeometry.distancePointToAabb(
                        attacker.eyePosition(), target.getBoundingBox().expand(expansion));
                if (distance < minimum) {
                    minimum = distance;
                    selectedAttacker = attacker;
                    selectedTarget = target;
                }
            }
        }
        return new Measurement(minimum, selectedAttacker, selectedTarget);
    }

    private static ReachResult result(ReachDecision decision, double measured,
                                      double allowed, Reliability reliability,
                                      String reason, long attackerSequence,
                                      long targetSequence, int attackerCandidates,
                                      int targetCandidates, long age) {
        return new ReachResult(decision, measured, allowed, reliability, reason,
                attackerSequence, targetSequence, attackerCandidates,
                targetCandidates, age);
    }

    private static long newestAttacker(List<MovementFrame> frames) {
        return frames.isEmpty() ? 0L : frames.get(0).getSequence();
    }

    private static long sequence(MovementFrame frame) {
        return frame == null ? 0L : frame.getSequence();
    }

    private static long sequence(TargetFrame frame) {
        return frame == null ? 0L : frame.getStateSequence();
    }

    static final class Measurement {
        final double distance;
        final MovementFrame attacker;
        final TargetFrame target;

        Measurement(double distance, MovementFrame attacker, TargetFrame target) {
            this.distance = distance;
            this.attacker = attacker;
            this.target = target;
        }
    }
}
