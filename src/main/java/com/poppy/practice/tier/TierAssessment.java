package com.poppy.practice.tier;

import com.poppy.practice.match.BoxingRules;
import com.poppy.practice.match.ComboRules;
import com.poppy.practice.result.MatchParticipantSnapshot;
import com.poppy.practice.result.MatchResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Transparent local calibration against our fixed bot, not an external competitive tier. */
public final class TierAssessment {
    public static final String MODEL_VERSION = "fixed-hard-v1";
    private final double score;
    private final Map<String, Double> metrics;
    private final Map<String, Double> weights;

    private TierAssessment(Map<String, Double> metrics, Map<String, Double> weights) {
        this.metrics = Collections.unmodifiableMap(new LinkedHashMap<String, Double>(metrics));
        this.weights = Collections.unmodifiableMap(new LinkedHashMap<String, Double>(weights));
        double weightedScore = 0.0D;
        double totalWeight = 0.0D;
        for (Map.Entry<String, Double> entry : metrics.entrySet()) {
            double weight = weights.get(entry.getKey());
            weightedScore += clamp(entry.getValue()) * weight;
            totalWeight += weight;
        }
        score = totalWeight == 0.0D ? 0.0D : clamp(weightedScore / totalWeight);
    }

    public static TierAssessment assess(String kitId, UUID playerId, MatchResult result) {
        if (kitId == null || playerId == null || result == null
                || result.getParticipant(playerId) == null || result.getOpponent(playerId) == null) {
            throw new IllegalArgumentException("A completed two-participant result is required");
        }
        MatchParticipantSnapshot player = result.getParticipant(playerId);
        MatchParticipantSnapshot opponent = result.getOpponent(playerId);
        boolean won = playerId.equals(result.getWinnerId());
        double totalHits = (double) player.getHits() + opponent.getHits();
        double hitShare = totalHits <= 0.0D ? 0.0D : player.getHits() * 100.0D / totalHits;
        Map<String, Double> metrics = new LinkedHashMap<String, Double>();
        Map<String, Double> weights = new LinkedHashMap<String, Double>();
        if (BoxingRules.isBoxing(kitId)) {
            add(metrics, weights, "Hit share", hitShare, 80);
            add(metrics, weights, "Result", won ? 100 : 0, 20);
        } else {
            boolean combo = ComboRules.isCombo(kitId);
            add(metrics, weights, "Hit share", hitShare, combo ? 65 : 50);
            add(metrics, weights, "Result", won ? 100 : 0, combo ? 25 : 20);
            add(metrics, weights, "Remaining health", player.getHealth() * 5.0D, 10);
            // No splash pots in Boxing/Combo, and an unused pot is neither a perfect nor a missed throw.
            // Remove that weight entirely when no healing potion was used.
            if (!combo && player.getHealingPotionsThrown() > 0) {
                add(metrics, weights, "Potion accuracy", player.getPotionAccuracyPercent(), 20);
            }
        }
        return new TierAssessment(metrics, weights);
    }

    private static void add(Map<String, Double> metrics, Map<String, Double> weights,
                            String name, double value, double weight) {
        metrics.put(name, clamp(value));
        weights.put(name, weight);
    }

    private static double clamp(double value) {
        return Double.isNaN(value) || Double.isInfinite(value) ? 0.0D
                : Math.max(0.0D, Math.min(100.0D, value));
    }

    public double getScore() { return score; }
    public Map<String, Double> getMetrics() { return metrics; }
    public Map<String, Double> getWeights() { return weights; }
}
