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
        assertEquals((100.0 / 150.0 * 100.0) * .8 + 20, assessment.getScore(), .000001);
        assertEquals(2, assessment.getMetrics().size());
        assertFalse(assessment.getMetrics().containsKey("Potion accuracy"));
        assertFalse(assessment.getMetrics().containsKey("Remaining health"));
    }

    @Test
    public void comboDoesNotRewardOrPunishUnavailableSplashPotions() {
        TierAssessment assessment = TierAssessment.assess("combo", playerId,
                result(80, 20, true, 10, new MatchParticipantStats()));
        assertEquals(82.0, assessment.getScore(), .000001);
        assertEquals(3, assessment.getMetrics().size());
        assertFalse(assessment.getMetrics().containsKey("Potion accuracy"));
    }

    @Test
    public void noDebuffNoPotionsRenormalizesTheRemainingWeights() {
        TierAssessment assessment = TierAssessment.assess("nodebuff", playerId,
                result(60, 40, true, 10, new MatchParticipantStats()));
        assertEquals((60.0 * 50 + 100.0 * 20 + 50.0 * 10) / 80.0,
                assessment.getScore(), .000001);
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
        assertEquals(70.0, assessment.getScore(), .000001);
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

    @Test(expected = UnsupportedOperationException.class)
    public void exposedMetricSnapshotIsImmutable() {
        TierAssessment.assess("boxing", playerId,
                result(1, 1, false, 0, new MatchParticipantStats())).getMetrics().clear();
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
