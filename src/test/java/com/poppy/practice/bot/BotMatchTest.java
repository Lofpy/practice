package com.poppy.practice.bot;

import com.poppy.practice.match.MatchState;
import com.poppy.practice.match.BoxingRules;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BotMatchTest {
    @Test
    public void onlyExplicitPlacementMatchesAreCertification() {
        assertFalse(match("nodebuff").isPlacement());
        BotMatch placement = new BotMatch(UUID.randomUUID(), UUID.randomUUID(), "combo", "arena", true);
        assertTrue(placement.isPlacement());
        assertNull(placement.getFinishTaskId());
        placement.setFinishTaskId(123);
        assertEquals(Integer.valueOf(123), placement.getFinishTaskId());
    }

    @Test
    public void comboUsesConsumablesButNeverCreatesHealingSplashPotionStatistics() {
        BotMatch combo = match("combo");
        assertTrue(combo.isCombo());
        assertFalse(combo.isBoxing());
        assertTrue(combo.allowsConsumables());
        assertFalse(combo.allowsHealingPotions());
        combo.setBotRemainingHealingPotions(29);
        assertEquals(0, combo.getBotRemainingHealingPotions());
        assertTrue(match("nodebuff").allowsHealingPotions());
        assertFalse(match("boxing").allowsHealingPotions());
    }

    @Test
    public void boxingLoadoutIsLocalAndNeverContainsHealingPotions() {
        BotMatch boxing = match("boxing");
        BotMatch ordinary = match("nodebuff");
        assertTrue(boxing.isBoxing());
        assertFalse(boxing.allowsConsumables());
        assertFalse(ordinary.isBoxing());
        assertTrue(ordinary.allowsConsumables());
        boxing.setBotRemainingHealingPotions(29);
        ordinary.setBotRemainingHealingPotions(29);
        assertEquals(0, boxing.getBotRemainingHealingPotions());
        assertEquals(29, ordinary.getBotRemainingHealingPotions());
    }

    @Test
    public void boxingAllowsOnlyItsTwoOpponentsDuringFighting() {
        BotMatch match = match("boxing");
        assertFalse(match.canScoreBoxingHit(match.getPlayerId(), match.getBotEntityId()));
        match.markFighting();
        assertTrue(match.canScoreBoxingHit(match.getPlayerId(), match.getBotEntityId()));
        assertTrue(match.canScoreBoxingHit(match.getBotEntityId(), match.getPlayerId()));
        assertFalse(match.canScoreBoxingHit(UUID.randomUUID(), match.getBotEntityId()));
        assertFalse(match.canScoreBoxingHit(match.getPlayerId(), UUID.randomUUID()));
        assertFalse(match.canScoreBoxingHit(match.getPlayerId(), match.getPlayerId()));
        assertFalse(match.canScoreBoxingHit(null, match.getPlayerId()));
        assertFalse(match.canScoreBoxingHit(match.getPlayerId(), null));
        assertNull(match.getOpponent(UUID.randomUUID()));
        assertNull(match.getOpponent(null));
        match.beginEnding();
        assertFalse(match.canScoreBoxingHit(match.getPlayerId(), match.getBotEntityId()));
    }

    @Test
    public void eitherSideCanWinBoxingButOnlyAtOneHundredAndOnlyOnce() {
        for (boolean playerWins : new boolean[] {true, false}) {
            BotMatch match = match("boxing");
            match.markFighting();
            UUID winner = playerWins ? match.getPlayerId() : match.getBotEntityId();
            UUID loser = match.getOpponent(winner);
            for (int hit = 0; hit < BoxingRules.HITS_TO_WIN - 1; hit++) {
                match.getStats(winner).recordMeleeHit(false);
            }
            assertFalse(match.claimBoxingWinner(winner));
            assertNull(match.getBoxingWinnerId());
            match.getStats(winner).recordMeleeHit(false);
            assertTrue(match.claimBoxingWinner(winner));
            assertEquals(winner, match.getBoxingWinnerId());
            assertFalse(match.canScoreBoxingHit(winner, loser));
            assertFalse(match.canScoreBoxingHit(loser, winner));
            assertFalse(match.claimBoxingWinner(winner));
            for (int hit = 0; hit < BoxingRules.HITS_TO_WIN; hit++) {
                match.getStats(loser).recordMeleeHit(false);
            }
            assertFalse(match.claimBoxingWinner(loser));
            assertEquals(winner, match.getBoxingWinnerId());
        }
    }

    @Test
    public void noDebuffAndCountdownsCannotClaimBoxingWins() {
        BotMatch ordinary = match("nodebuff");
        ordinary.markFighting();
        BotMatch countdown = match("boxing");
        for (int hit = 0; hit < BoxingRules.HITS_TO_WIN; hit++) {
            ordinary.getStats(ordinary.getPlayerId()).recordMeleeHit(false);
            countdown.getStats(countdown.getBotEntityId()).recordMeleeHit(false);
        }
        assertFalse(ordinary.claimBoxingWinner(ordinary.getPlayerId()));
        assertFalse(countdown.claimBoxingWinner(countdown.getBotEntityId()));
        assertFalse(ordinary.canScoreBoxingHit(ordinary.getPlayerId(), ordinary.getBotEntityId()));
        assertFalse(countdown.claimBoxingWinner(UUID.randomUUID()));
        assertFalse(countdown.claimBoxingWinner(null));
    }

    @Test
    public void delayedCountdownCannotReviveAnEndedBotMatch() {
        BotMatch match = match("boxing");
        match.markFighting();
        long startedAt = match.getStartedAt();
        match.beginEnding();
        match.markFinished();
        match.markFighting();
        assertEquals(MatchState.FINISHED, match.getState());
        assertEquals(startedAt, match.getStartedAt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDuplicateParticipants() {
        UUID playerId = UUID.randomUUID();
        new BotMatch(playerId, playerId, "boxing", "arena");
    }

    @Test
    public void transitionsFromStartingToFinishedOnce() {
        BotMatch match = new BotMatch(UUID.randomUUID(), UUID.randomUUID(), "nodebuff", "arena");

        assertEquals(MatchState.STARTING, match.getState());
        assertNotNull(match.getStats(match.getPlayerId()));
        assertNotNull(match.getStats(match.getBotEntityId()));
        match.markFighting();
        assertEquals(MatchState.FIGHTING, match.getState());
        assertTrue(match.getStartedAt() > 0L);
        assertTrue(match.beginEnding());
        assertFalse(match.beginEnding());
        match.markFinished();
        assertEquals(MatchState.FINISHED, match.getState());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullValues() {
        new BotMatch(null, UUID.randomUUID(), "nodebuff", "arena");
    }

    private static BotMatch match(String kit) {
        return new BotMatch(UUID.randomUUID(), UUID.randomUUID(), kit, "arena");
    }
}
