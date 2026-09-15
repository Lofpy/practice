package com.poppy.practice.bot;

final class BotAttackPlan {
    private static final double MAXIMUM_VERTICAL_DIFFERENCE = 2.4D;
    private static final double MAXIMUM_AIM_DIFFERENCE = 18.0D;

    private BotAttackPlan() {
    }

    static boolean shouldAttemptAttack(int clickCooldown, int targetNoDamageTicks) {
        // Hurt-time does not stop clicks, nor does it grant extra clicks above
        // the configured rate. Native damage decides whether each hit lands.
        return clickCooldown <= 0;
    }

    static boolean shouldSwing(double distance, double swingRange,
                               double verticalDifference, boolean hasLineOfSight,
                               double aimDifference) {
        return distance <= swingRange
                && Math.abs(verticalDifference) <= MAXIMUM_VERTICAL_DIFFERENCE
                && hasLineOfSight
                && aimDifference <= MAXIMUM_AIM_DIFFERENCE;
    }

    static boolean shouldDamage(double distance, double attackRange) {
        return attackRange > 0.0D && distance <= attackRange;
    }
}
