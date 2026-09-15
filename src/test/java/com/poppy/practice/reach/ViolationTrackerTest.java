package com.poppy.practice.reach;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public final class ViolationTrackerTest {
    private static final double ERROR = 0.0000001D;

    @Test
    public void accountingAndEvidenceAgreeForEveryDecisionIncludingLowReliability() {
        ReachGuardConfig config = ReachGuardConfig.load(null);
        for (ReachDecision decision : ReachDecision.values()) {
            for (Reliability reliability : Reliability.values()) {
                for (String reason : new String[] {"REACH_EXCEEDED", "STALE_ATTACK",
                        "INVALID_ENTITY", "DAMAGE_WITHOUT_PERMIT"}) {
                    ViolationTracker tracker = new ViolationTracker(config);
                    ReachResult result = new ReachResult(decision, 3.27D, 3.0D,
                            reliability, reason, 1L, 2L, 1, 1, 0L);
                    double expected = ReachGuardService.evidenceVlAdded(result, config,
                            ReachGuardService.evidenceVlExcess(result, config));
                    ViolationSnapshot actual = tracker.record(UUID.randomUUID(), result,
                            config.getBaseReach(), 0L);
                    assertEquals(decision + "/" + reliability + "/" + reason,
                            expected, actual.getTotalVl(), ERROR);
                    if (reliability == Reliability.LOW) {
                        assertEquals(0, tracker.trackedPlayerCount());
                    }
                }
            }
        }
    }

    @Test
    public void permitFailureObserveFlagBelongsToInvalidEntityCategory() {
        ViolationSnapshot snapshot = tracker().record(UUID.randomUUID(),
                result(ReachDecision.FLAG, Double.POSITIVE_INFINITY,
                        "DAMAGE_WITHOUT_PERMIT"), 3.0D, 0L);
        assertEquals(0.0D, snapshot.getReachVl(), ERROR);
        assertEquals(1.0D, snapshot.getInvalidEntityVl(), ERROR);
    }

    @Test
    public void reachAdditionMatchesSpecificationExamples() {
        assertEquals(1.4D, reachVl(3.04D), ERROR);
        assertEquals(2.0D, reachVl(3.10D), ERROR);
        assertEquals(3.0D, reachVl(3.20D), ERROR);
        assertEquals(5.0D, reachVl(3.50D), ERROR);
    }

    @Test
    public void categoriesAreStoredAndDecayedIndependently() {
        ViolationTracker tracker = tracker();
        UUID playerId = UUID.randomUUID();
        tracker.add(playerId, ViolationCategory.REACH, 0.0D, 0L);
        tracker.add(playerId, ViolationCategory.STALE, 0.0D, 0L);
        tracker.add(playerId, ViolationCategory.INVALID_ENTITY, 0.0D, 0L);

        ViolationSnapshot initial = tracker.snapshot(playerId, 0L);
        assertEquals(1.0D, initial.getReachVl(), ERROR);
        assertEquals(1.0D, initial.getStaleVl(), ERROR);
        assertEquals(1.0D, initial.getInvalidEntityVl(), ERROR);
        assertEquals(3.0D, initial.getTotalVl(), ERROR);

        ViolationSnapshot decayed = tracker.snapshot(playerId, 2000000000L);
        assertEquals(0.6D, decayed.getReachVl(), ERROR);
        assertEquals(0.6D, decayed.getStaleVl(), ERROR);
        assertEquals(0.6D, decayed.getInvalidEntityVl(), ERROR);
    }

    @Test
    public void lazyDecayNeverDropsBelowZero() {
        ViolationTracker tracker = tracker();
        UUID playerId = UUID.randomUUID();
        tracker.add(playerId, ViolationCategory.REACH, 0.0D, 100L);

        ViolationSnapshot snapshot = tracker.snapshot(playerId, 100000000100L);

        assertEquals(0.0D, snapshot.getReachVl(), ERROR);
    }

    @Test
    public void olderNanoTimeCannotReverseOrDoubleApplyDecay() {
        ViolationTracker tracker = tracker();
        UUID playerId = UUID.randomUUID();
        tracker.add(playerId, ViolationCategory.REACH, 0.0D, 1000000000L);

        assertEquals(1.0D,
                tracker.snapshot(playerId, 500000000L).getReachVl(), ERROR);
        assertEquals(0.8D,
                tracker.snapshot(playerId, 2000000000L).getReachVl(), ERROR);
    }

    @Test
    public void resultDecisionsMapToSeparateCategories() {
        ViolationTracker tracker = tracker();
        UUID playerId = UUID.randomUUID();

        tracker.record(playerId, result(ReachDecision.CANCEL_REACH, 3.10D),
                3.00D, 0L);
        tracker.record(playerId, result(ReachDecision.CANCEL_STALE_ATTACK, 4.0D),
                3.00D, 0L);
        ViolationSnapshot snapshot = tracker.record(playerId,
                result(ReachDecision.CANCEL_INVALID_ENTITY,
                        Double.POSITIVE_INFINITY), 3.00D, 0L);

        assertEquals(2.0D, snapshot.getReachVl(), ERROR);
        assertEquals(1.0D, snapshot.getStaleVl(), ERROR);
        assertEquals(1.0D, snapshot.getInvalidEntityVl(), ERROR);
    }

    @Test
    public void observeFlagsKeepStaleAndInvalidCategoriesSeparate() {
        ViolationTracker tracker = tracker();
        UUID playerId = UUID.randomUUID();

        tracker.record(playerId, result(ReachDecision.FLAG, 4.0D,
                "STALE_ATTACK"), 3.0D, 0L);
        ViolationSnapshot snapshot = tracker.record(playerId,
                result(ReachDecision.FLAG, Double.POSITIVE_INFINITY,
                        "INVALID_ENTITY"), 3.0D, 0L);

        assertEquals(0.0D, snapshot.getReachVl(), ERROR);
        assertEquals(1.0D, snapshot.getStaleVl(), ERROR);
        assertEquals(1.0D, snapshot.getInvalidEntityVl(), ERROR);
    }

    @Test
    public void nonViolationDecisionDoesNotCreateState() {
        ViolationTracker tracker = tracker();
        UUID playerId = UUID.randomUUID();

        ViolationSnapshot snapshot = tracker.record(playerId,
                result(ReachDecision.ALLOW, 2.9D), 3.0D, 0L);

        assertEquals(0.0D, snapshot.getTotalVl(), ERROR);
        assertEquals(0, tracker.trackedPlayerCount());
    }

    @Test
    public void resetOnlyRemovesSelectedPlayer() {
        ViolationTracker tracker = tracker();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        tracker.add(first, ViolationCategory.REACH, 0.0D, 0L);
        tracker.add(second, ViolationCategory.REACH, 0.0D, 0L);

        tracker.reset(first);

        assertEquals(0.0D, tracker.snapshot(first, 0L).getReachVl(), ERROR);
        assertEquals(1.0D, tracker.snapshot(second, 0L).getReachVl(), ERROR);
        assertEquals(1, tracker.trackedPlayerCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidSettingsAreRejectedAtomically() {
        ViolationTracker tracker = tracker();
        tracker.updateConfiguration(1.0D, 10.0D, 4.0D, Double.NaN);
    }

    @Test
    public void zeroMultiplierDoesNotTurnInfiniteExcessIntoMaximumAddition() {
        ViolationTracker tracker = new ViolationTracker(1.0D, 0.0D, 4.0D, 0.20D);
        ViolationSnapshot snapshot = tracker.add(UUID.randomUUID(),
                ViolationCategory.REACH, Double.POSITIVE_INFINITY, 0L);

        assertEquals(1.0D, snapshot.getReachVl(), ERROR);
    }

    private static double reachVl(double measuredReach) {
        ViolationTracker tracker = tracker();
        UUID playerId = UUID.randomUUID();
        return tracker.record(playerId,
                result(ReachDecision.CANCEL_REACH, measuredReach),
                3.0D, 0L).getReachVl();
    }

    private static ViolationTracker tracker() {
        return new ViolationTracker(1.0D, 10.0D, 4.0D, 0.20D);
    }

    private static ReachResult result(ReachDecision decision, double measured) {
        return result(decision, measured, "TEST");
    }

    private static ReachResult result(ReachDecision decision, double measured,
                                      String reason) {
        return new ReachResult(decision, measured, 3.08D, Reliability.HIGH,
                reason, 1L, 2L, 1, 1, 0L);
    }
}
