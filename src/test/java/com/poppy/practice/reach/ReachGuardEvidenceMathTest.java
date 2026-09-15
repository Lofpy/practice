package com.poppy.practice.reach;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class ReachGuardEvidenceMathTest {
    private static final double ERROR = 0.0000001D;

    @Test
    public void reachVlUsesBaseReachRatherThanDecisionThreshold() {
        ReachGuardConfig config = ReachGuardConfig.load(null);
        ReachResult result = result(ReachDecision.FLAG, 3.27D, 3.03D,
                Reliability.HIGH, "OBSERVE_ONLY");

        double excess = ReachGuardService.evidenceVlExcess(result, config);

        assertEquals(0.27D, excess, ERROR);
        assertEquals(3.70D,
                ReachGuardService.evidenceVlAdded(result, config, excess), ERROR);
    }

    @Test
    public void staleAndInvalidHaveZeroExcessAndBaseAddition() {
        ReachGuardConfig config = ReachGuardConfig.load(null);
        ReachResult stale = result(ReachDecision.CANCEL_STALE_ATTACK,
                4.20D, 3.03D, Reliability.MEDIUM, "STALE_ATTACK");
        ReachResult invalid = result(ReachDecision.CANCEL_INVALID_ENTITY,
                Double.POSITIVE_INFINITY, 3.03D, Reliability.MEDIUM,
                "INVALID_ENTITY");

        assertCategoryBaseOnly(stale, config);
        assertCategoryBaseOnly(invalid, config);
    }

    @Test
    public void lowReliabilityDoesNotClaimAReachVlAddition() {
        ReachGuardConfig config = ReachGuardConfig.load(null);
        ReachResult result = result(ReachDecision.CANCEL_REACH, 4.0D, 3.08D,
                Reliability.LOW, "REACH_EXCEEDED");

        double excess = ReachGuardService.evidenceVlExcess(result, config);

        assertEquals(0.0D, excess, ERROR);
        assertEquals(0.0D,
                ReachGuardService.evidenceVlAdded(result, config, excess), ERROR);
    }

    private static void assertCategoryBaseOnly(ReachResult result,
                                               ReachGuardConfig config) {
        double excess = ReachGuardService.evidenceVlExcess(result, config);
        assertEquals(0.0D, excess, ERROR);
        assertEquals(config.getViolationBase(),
                ReachGuardService.evidenceVlAdded(result, config, excess), ERROR);
    }

    private static ReachResult result(ReachDecision decision, double measured,
                                      double allowed, Reliability reliability,
                                      String reason) {
        return new ReachResult(decision, measured, allowed, reliability, reason,
                1L, 2L, 1, 1, 20L);
    }
}
