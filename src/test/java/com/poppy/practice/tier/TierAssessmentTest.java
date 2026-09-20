package com.poppy.practice.tier;

import com.poppy.practice.result.MatchParticipantSnapshot;
import com.poppy.practice.result.MatchParticipantStats;
import com.poppy.practice.result.MatchResult;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TierAssessmentTest {
    private final UUID playerId = UUID.randomUUID();
    private final UUID botId = UUID.randomUUID();

    @Test
    public void boxingUsesHitShareAndOutcomeOnly() {
        TierAssessment assessment = TierAssessment.assess("boxing", playerId,
                result(100, 50, true, 20, new MatchParticipantStats()));
        double rawScore = (100.0 / 150.0 * 100.0) * .8 + 20;
        assertEquals(rawScore, assessment.getRawScore(), .000001);
        assertEquals(88.0, assessment.getScore(), .000001);
        assertEquals(2, assessment.getMetrics().size());
        assertEquals(80.0, assessment.getWeights().get("Hit share"), 0.0);
        assertEquals(20.0, assessment.getWeights().get("Result"), 0.0);
        assertFalse(assessment.getMetrics().containsKey("Potion accuracy"));
        assertFalse(assessment.getMetrics().containsKey("Remaining health"));
    }

    @Test
    public void comboDoesNotRewardOrPunishUnavailableSplashPotions() {
        TierAssessment assessment = TierAssessment.assess("combo", playerId,
                result(80, 20, true, 10, new MatchParticipantStats()));
        assertEquals(82.0, assessment.getRawScore(), .000001);
        assertEquals(98.4, assessment.getScore(), .000001);
        assertEquals(3, assessment.getMetrics().size());
        assertEquals(65.0, assessment.getWeights().get("Hit share"), 0.0);
        assertEquals(25.0, assessment.getWeights().get("Result"), 0.0);
        assertEquals(10.0, assessment.getWeights().get("Remaining health"), 0.0);
        assertFalse(assessment.getMetrics().containsKey("Potion accuracy"));
    }

    @Test
    public void noDebuffNoPotionsRenormalizesTheRemainingWeights() {
        TierAssessment assessment = TierAssessment.assess("nodebuff", playerId,
                result(60, 40, true, 10, new MatchParticipantStats()));
        assertEquals((60.0 * 50 + 100.0 * 20 + 50.0 * 10) / 80.0,
                assessment.getRawScore(), .000001);
        assertEquals(82.5, assessment.getScore(), .000001);
        assertFalse(assessment.getMetrics().containsKey("Potion accuracy"));
    }

    @Test
    public void noDebuffUsesActualHealingInsteadOfOverhealing() {
        MatchParticipantStats potions = new MatchParticipantStats();
        potions.recordHealingPotionThrown();
        potions.recordHealingPotionThrown();
        potions.recordHealingPotionResult(4, 4, 6);
        potions.recordHealingPotionResult(8, 0, 0);
        TierAssessment assessment = TierAssessment.assess("nodebuff", playerId,
                result(60, 40, true, 10, potions));
        assertEquals(75.0, assessment.getMetrics().get("Potion accuracy"), .000001);
        assertEquals(70.0, assessment.getRawScore(), .000001);
        assertEquals(84.0, assessment.getScore(), .000001);
        assertEquals(50.0, assessment.getWeights().get("Hit share"), 0.0);
        assertEquals(20.0, assessment.getWeights().get("Result"), 0.0);
        assertEquals(10.0, assessment.getWeights().get("Remaining health"), 0.0);
        assertEquals(20.0, assessment.getWeights().get("Potion accuracy"), 0.0);
        assertEquals(12.0, potions.getHealedHealth(), 0.0);
        assertEquals(4.0, potions.getOverhealedHealth(), 0.0);
        assertEquals(6.0, potions.getOpponentHealedHealth(), 0.0);
        assertEquals(75.0, potions.getPotionAccuracyPercent(), 0.0);
    }

    @Test
    public void unresolvedThrownPotionsCountAsMissingHealing() {
        MatchParticipantStats potions = new MatchParticipantStats();
        potions.recordHealingPotionThrown();
        TierAssessment assessment = TierAssessment.assess("nodebuff", playerId,
                result(0, 20, false, 0, potions));
        assertEquals(0.0, assessment.getScore(), 0.0);
        assertEquals(0.0, assessment.getMetrics().get("Potion accuracy"), 0.0);
    }

    @Test
    public void boundsAndZeroHitsAlwaysRemainFinite() {
        TierAssessment low = TierAssessment.assess("combo", playerId,
                result(0, 0, false, 0, new MatchParticipantStats()));
        TierAssessment high = TierAssessment.assess("nodebuff", playerId,
                result(100, 0, true, 200, new MatchParticipantStats()));
        assertEquals(0.0, low.getScore(), 0.0);
        assertEquals(100.0, high.getScore(), 0.0);
    }

    @Test
    public void calibrationIsMonotonicAndNeverLessThanTheOldScoreInEveryKit() {
        for (String kit : new String[] {"nodebuff", "boxing", "combo"}) {
            for (boolean won : new boolean[] {false, true}) {
                double previousScore = -1.0;
                for (int hits = 0; hits <= 100; hits++) {
                    MatchParticipantStats stats = new MatchParticipantStats();
                    stats.recordHealingPotionThrown();
                    stats.recordHealingPotionResult(4, 4, 0);
                    TierAssessment assessment = TierAssessment.assess(kit, playerId,
                            result(hits, 50, won, 10, stats));
                    double oldScore;
                    double hitShare = hits * 100.0 / (hits + 50.0);
                    double outcome = won ? 100.0 : 0.0;
                    if ("boxing".equals(kit)) {
                        oldScore = hitShare * .8 + outcome * .2;
                    } else if ("combo".equals(kit)) {
                        oldScore = hitShare * .65 + outcome * .25 + 50.0 * .1;
                    } else {
                        oldScore = hitShare * .5 + outcome * .2 + 50.0 * .1 + 50.0 * .2;
                    }
                    assertEquals(kit, oldScore, assessment.getRawScore(), .000001);
                    assertEquals(kit, Math.min(100.0, oldScore * 1.2),
                            assessment.getScore(), .000001);
                    assertTrue(kit, assessment.getScore() >= oldScore - .000001);
                    assertTrue(kit, assessment.getScore() >= previousScore);
                    assertTrue(kit, assessment.getScore() >= 0.0 && assessment.getScore() <= 100.0);
                    previousScore = assessment.getScore();
                }
            }
        }
    }

    @Test
    public void zeroPerformanceGetsNoFlatCompletionBonusInEveryKit() {
        for (String kit : new String[] {"nodebuff", "boxing", "combo"}) {
            TierAssessment assessment = TierAssessment.assess(kit, playerId,
                    result(0, 100, false, 0, new MatchParticipantStats()));
            assertEquals(kit, 0.0, assessment.getRawScore(), 0.0);
            assertEquals(kit, 0.0, assessment.getScore(), 0.0);
        }
    }

    @Test
    public void calibrationCapsHighButImperfectPerformancesAtOneHundredInEveryKit() {
        for (String kit : new String[] {"nodebuff", "boxing", "combo"}) {
            TierAssessment assessment = TierAssessment.assess(kit, playerId,
                    result(100, 10, true, 20, new MatchParticipantStats()));
            assertTrue(kit, assessment.getRawScore() < 100.0);
            assertEquals(kit, 100.0, assessment.getScore(), 0.0);
            assertEquals(kit, 100.0 / 110.0 * 100.0,
                    assessment.getMetrics().get("Hit share"), .000001);
        }
    }

    @Test
    public void invalidMetricValuesDoNotProduceNonFiniteScores() {
        for (String kit : new String[] {"nodebuff", "boxing", "combo"}) {
            for (double health : new double[] {Double.NaN, Double.POSITIVE_INFINITY,
                    Double.NEGATIVE_INFINITY, -100.0}) {
                TierAssessment assessment = TierAssessment.assess(kit, playerId,
                        result(0, 0, false, health, new MatchParticipantStats()));
                assertEquals(kit, 0.0, assessment.getRawScore(), 0.0);
                assertEquals(kit, 0.0, assessment.getScore(), 0.0);
            }
        }
    }

    @Test
    public void calibrationHasANewAuditableModelVersion() {
        assertEquals("fixed-balanced-v2", TierAssessment.MODEL_VERSION);
        assertEquals(1.2, TierAssessment.SCORE_MULTIPLIER, 0.0);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void exposedMetricSnapshotIsImmutable() {
        TierAssessment.assess("boxing", playerId,
                result(1, 1, false, 0, new MatchParticipantStats())).getMetrics().clear();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void exposedWeightSnapshotIsImmutable() {
        TierAssessment.assess("boxing", playerId,
                result(1, 1, false, 0, new MatchParticipantStats())).getWeights().clear();
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMismatchedParticipant() {
        TierAssessment.assess("nodebuff", UUID.randomUUID(),
                result(1, 1, false, 0, new MatchParticipantStats()));
    }

    private MatchResult result(int playerHits, int botHits, boolean won, double health,
                               MatchParticipantStats playerStats) {
        for (int i = 0; i < playerHits; i++) playerStats.recordMeleeHit(false);
        MatchParticipantStats botStats = new MatchParticipantStats();
        for (int i = 0; i < botHits; i++) botStats.recordMeleeHit(false);
        Player player = mock(Player.class);
        when(player.getHealth()).thenReturn(health);
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        return new MatchResult(won ? playerId : botId,
                MatchParticipantSnapshot.capture(playerId, "Human", player, playerStats),
                MatchParticipantSnapshot.capture(botId, "Bot", null, botStats), 60);
    }
}
