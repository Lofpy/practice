package com.poppy.practice.match;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public final class BoxingRulesTest {
    @Test
    public void recognizesOnlyTheBoxingKit() {
        assertTrue(BoxingRules.isBoxing("boxing"));
        assertTrue(BoxingRules.isBoxing("BOXING"));
        assertFalse(BoxingRules.isBoxing("nodebuff"));
        assertFalse(BoxingRules.isBoxing(null));
    }

    @Test
    public void countsOnlyOpponentsDuringAnActiveBoxingMatch() {
        Match match = match("boxing");
        assertFalse(canScore(match));
        match.markFighting();
        assertTrue(canScore(match));
        assertTrue(BoxingRules.canScore(match, match.getSecondPlayerId(), match.getFirstPlayerId()));
        assertFalse(BoxingRules.canScore(match, UUID.randomUUID(), match.getSecondPlayerId()));
        assertFalse(BoxingRules.canScore(match, match.getFirstPlayerId(), UUID.randomUUID()));
        assertFalse(BoxingRules.canScore(match, match.getFirstPlayerId(), match.getFirstPlayerId()));
        assertFalse(BoxingRules.canScore(match, null, match.getFirstPlayerId()));
        assertFalse(BoxingRules.canScore(null, UUID.randomUUID(), UUID.randomUUID()));
        match.beginEnding();
        assertFalse(canScore(match));
        match.markFinished();
        assertFalse(canScore(match));
    }

    @Test
    public void locksTheWinnerAtOneHundredAndRejectsLaterHitsOrWinnerChanges() {
        Match match = match("boxing");
        match.markFighting();
        for (int hit = 0; hit < BoxingRules.HITS_TO_WIN - 1; hit++) {
            match.getStats(match.getFirstPlayerId()).recordMeleeHit(false);
        }
        assertFalse(match.claimBoxingWinner(match.getFirstPlayerId()));
        assertNull(match.getBoxingWinnerId());
        assertTrue(canScore(match));

        match.getStats(match.getFirstPlayerId()).recordMeleeHit(false);
        assertTrue(match.claimBoxingWinner(match.getFirstPlayerId()));
        assertEquals(match.getFirstPlayerId(), match.getBoxingWinnerId());
        assertFalse(canScore(match));
        assertFalse(BoxingRules.canScore(match, match.getSecondPlayerId(), match.getFirstPlayerId()));
        assertFalse(match.claimBoxingWinner(match.getFirstPlayerId()));
        for (int hit = 0; hit < BoxingRules.HITS_TO_WIN; hit++) {
            match.getStats(match.getSecondPlayerId()).recordMeleeHit(false);
        }
        assertFalse(match.claimBoxingWinner(match.getSecondPlayerId()));
        assertEquals(match.getFirstPlayerId(), match.getBoxingWinnerId());
    }

    @Test
    public void reachingOneHundredDoesNotEndOtherKitsOrCountdowns() {
        Match ordinary = match("nodebuff");
        ordinary.markFighting();
        Match countdown = match("boxing");
        for (int hit = 0; hit < BoxingRules.HITS_TO_WIN; hit++) {
            ordinary.getStats(ordinary.getFirstPlayerId()).recordMeleeHit(false);
            countdown.getStats(countdown.getFirstPlayerId()).recordMeleeHit(false);
        }
        assertFalse(canScore(ordinary));
        assertFalse(ordinary.claimBoxingWinner(ordinary.getFirstPlayerId()));
        assertFalse(countdown.claimBoxingWinner(countdown.getFirstPlayerId()));
        assertFalse(ordinary.claimBoxingWinner(UUID.randomUUID()));
    }

    private boolean canScore(Match match) {
        return BoxingRules.canScore(match, match.getFirstPlayerId(), match.getSecondPlayerId());
    }

    private Match match(String kitId) {
        return new Match(UUID.randomUUID(), UUID.randomUUID(), kitId, "one");
    }
}
