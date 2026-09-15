package com.poppy.practice.match;

import java.util.UUID;

/** Shared rules for the damage-free, first-to-100 melee queue. */
public final class BoxingRules {
    public static final int HITS_TO_WIN = 100;

    private BoxingRules() {
    }

    public static boolean isBoxing(String kitId) {
        return "boxing".equalsIgnoreCase(kitId);
    }

    public static boolean canScore(Match match, UUID attackerId, UUID victimId) {
        return match != null && isBoxing(match.getKitId())
                && match.getState() == MatchState.FIGHTING
                && match.getBoxingWinnerId() == null
                && attackerId != null && victimId != null
                && victimId.equals(match.getOpponent(attackerId));
    }
}
