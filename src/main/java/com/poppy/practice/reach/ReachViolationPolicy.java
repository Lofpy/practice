package com.poppy.practice.reach;

/** Shared classification and arithmetic for accounting, alerts and evidence. */
final class ReachViolationPolicy {
    private ReachViolationPolicy() { }

    static boolean records(ReachResult result) {
        return result != null && result.getDecision().isViolation()
                && result.getReliability() != Reliability.LOW;
    }

    static ViolationCategory category(ReachResult result) {
        if (result != null && (result.getDecision() == ReachDecision.CANCEL_STALE_ATTACK
                || "STALE_ATTACK".equals(result.getReason()))) {
            return ViolationCategory.STALE;
        }
        if (result != null && (result.getDecision() == ReachDecision.CANCEL_INVALID_ENTITY
                || "INVALID_ENTITY".equals(result.getReason())
                || "DAMAGE_WITHOUT_PERMIT".equals(result.getReason()))) {
            return ViolationCategory.INVALID_ENTITY;
        }
        return ViolationCategory.REACH;
    }

    static double excess(ReachResult result, double baseReach) {
        if (!records(result) || category(result) != ViolationCategory.REACH) {
            return 0.0D;
        }
        double measured = result.getMeasuredReach();
        return Double.isNaN(measured) || measured <= baseReach ? 0.0D : measured - baseReach;
    }

    static double addition(double excess, double base, double multiplier, double maximum) {
        double contribution;
        if (Double.isNaN(excess) || excess <= 0.0D || multiplier == 0.0D) {
            contribution = 0.0D;
        } else if (Double.isInfinite(excess)) {
            contribution = maximum;
        } else {
            contribution = Math.min(excess * multiplier, maximum);
        }
        return base + contribution;
    }
}
