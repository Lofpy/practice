package com.poppy.practice.result;

/**
 * Mutable statistics for one participant while a match is active.
 * All mutations happen on the primary server thread.
 */
public final class MatchParticipantStats {
    public static final int MAX_GUARDS_PER_MATCH = 19;
    private static final double FULL_HEALING_TWO_HEALTH = 8.0D;
    private int hits;
    private int criticals;
    private int guards;
    private int healingPotionsThrown;
    private int healingPotionsMissed;
    private int healingPotionResults;
    private double healedHealth;
    private double overhealedHealth;
    private double opponentHealedHealth;

    public void recordMeleeHit(boolean critical) {
        hits++;
        if (critical) {
            criticals++;
        }
    }

    public void recordGuard() {
        if (guards < MAX_GUARDS_PER_MATCH) {
            guards++;
        }
    }

    public void recordHealingPotionThrown() {
        healingPotionsThrown++;
    }

    public void recordHealingPotionResult(double healed, double overhealed,
                                          double opponentHealed) {
        healingPotionResults++;
        if (healed + 0.0001D < FULL_HEALING_TWO_HEALTH) {
            healingPotionsMissed++;
        }
        healedHealth += Math.max(0.0D, healed);
        overhealedHealth += Math.max(0.0D, overhealed);
        opponentHealedHealth += Math.max(0.0D, opponentHealed);
    }

    public int getHits() {
        return hits;
    }

    public int getCriticals() {
        return criticals;
    }

    public int getGuards() {
        return guards;
    }

    public int getHealingPotionsThrown() {
        return healingPotionsThrown;
    }

    public int getHealingPotionsMissed() {
        return healingPotionsMissed;
    }

    public int getFinalHealingPotionsMissed() {
        int unresolved = healingPotionsThrown - healingPotionResults;
        return healingPotionsMissed + Math.max(0, unresolved);
    }

    public double getPotionAccuracyPercent() {
        double expectedHealing = getExpectedHealingHealth();
        if (expectedHealing <= 0.0D) {
            return 0.0D;
        }
        return Math.min(100.0D, healedHealth * 100.0D / expectedHealing);
    }

    double getExpectedHealingHealth() {
        return healingPotionsThrown * FULL_HEALING_TWO_HEALTH;
    }

    public double getHealedHealth() {
        return healedHealth;
    }

    public double getOverhealedHealth() {
        return overhealedHealth;
    }

    public double getOpponentHealedHealth() {
        return opponentHealedHealth;
    }
}
