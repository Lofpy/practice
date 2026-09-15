package com.poppy.practice.bot;

final class BotHealingPlan {
    private BotHealingPlan() {
    }

    static int potionsForHealth(double health, double doublePotionThreshold, int availablePotions) {
        if (availablePotions <= 0) {
            return 0;
        }
        int requested = health <= doublePotionThreshold ? 2 : 1;
        return Math.min(requested, availablePotions);
    }

    static boolean isEmergency(double health, double doublePotionThreshold,
                               int unansweredHits, int emergencyHitCount) {
        return health <= doublePotionThreshold || unansweredHits >= emergencyHitCount;
    }

    static boolean shouldSplashImmediately(boolean emergency, boolean onGround) {
        return emergency || !onGround;
    }

    static int maximumRetreatTicks(int configuredTicks, double distance,
                                   double safeDistance, int safeDistanceRetreatTicks) {
        return distance < safeDistance
                ? Math.max(configuredTicks, safeDistanceRetreatTicks)
                : configuredTicks;
    }

    static boolean isRetreatComplete(int elapsedTicks, int requiredTicks,
                                     int maximumTicks, double distance, double safeDistance) {
        return elapsedTicks >= requiredTicks
                && (distance >= safeDistance || elapsedTicks >= maximumTicks);
    }
}
