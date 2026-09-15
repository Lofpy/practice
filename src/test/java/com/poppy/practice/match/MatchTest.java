package com.poppy.practice.match;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MatchTest {
    @Test
    public void lateCountdownCannotRestartAnEndingOrFinishedMatch() {
        Match match = match();
        assertTrue(match.beginEnding());
        assertFalse(match.markFighting());
        assertEquals(0L, match.getStartedAt());
        assertEquals(MatchState.ENDING, match.getState());

        match.markFinished();
        assertFalse(match.markFighting());
        assertFalse(match.beginEnding());
        assertEquals(MatchState.FINISHED, match.getState());
    }

    @Test
    public void fightingAndEndingTransitionsOnlyHappenOnce() {
        Match match = match();
        assertTrue(match.markFighting());
        long startedAt = match.getStartedAt();
        assertFalse(match.markFighting());
        assertEquals(startedAt, match.getStartedAt());
        assertTrue(match.beginEnding());
        assertFalse(match.beginEnding());
        match.markFinished();
        match.markFinished();
        assertEquals(MatchState.FINISHED, match.getState());
    }

    @Test(expected = IllegalStateException.class)
    public void finishingCannotSkipCleanupTransition() {
        match().markFinished();
    }

    private Match match() {
        return new Match(UUID.randomUUID(), UUID.randomUUID(), "nodebuff", "one");
    }
}
