package com.poppy.practice.match;

import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class MatchPhasedCompletionTest {
    @Test
    public void beginningReservesBothPlayersAndArenaUntilExplicitFinish() {
        MatchManager matches = new MatchManager();
        Match match = new Match(UUID.randomUUID(), UUID.randomUUID(), "nodebuff", "one");
        matches.register(match);
        AtomicInteger resets = new AtomicInteger();
        AtomicInteger releases = new AtomicInteger();
        MatchCompletion completion = new MatchCompletion(matches, id -> resets.incrementAndGet(),
                arena -> releases.incrementAndGet(), (message, failure) -> {});
        assertTrue(completion.begin(match, () -> {}));
        assertEquals(MatchState.ENDING, match.getState());
        assertSame(match, matches.getByPlayer(match.getFirstPlayerId()));
        assertSame(match, matches.getByPlayer(match.getSecondPlayerId()));
        assertSame(match, matches.getByArena("one"));
        assertEquals(0, resets.get());
        assertEquals(0, releases.get());
        assertTrue(completion.finish(match));
        assertFalse(completion.finish(match));
        assertEquals(2, resets.get());
        assertEquals(1, releases.get());
    }

    @Test
    public void brokenReporterCannotStrandBeginOrSkipCleanup() {
        MatchManager matches = new MatchManager();
        Match match = new Match(UUID.randomUUID(), UUID.randomUUID(), "nodebuff", "one");
        matches.register(match);
        AtomicInteger resets = new AtomicInteger();
        AtomicInteger releases = new AtomicInteger();
        MatchCompletion completion = new MatchCompletion(matches, id -> {
            resets.incrementAndGet();
            throw new IllegalStateException("reset failed");
        }, arena -> releases.incrementAndGet(), (message, failure) -> {
            throw new IllegalStateException("logger failed");
        });
        assertTrue(completion.begin(match, () -> { throw new IllegalStateException("presentation failed"); }));
        assertTrue(completion.finish(match));
        assertEquals(MatchState.FINISHED, match.getState());
        assertEquals(2, resets.get());
        assertEquals(1, releases.get());
    }

    @Test
    public void staleDelayedCallbackCannotReleaseReplacementArena() {
        MatchManager matches = new MatchManager();
        Match first = new Match(UUID.randomUUID(), UUID.randomUUID(), "nodebuff", "one");
        matches.register(first);
        AtomicInteger resets = new AtomicInteger();
        AtomicInteger releases = new AtomicInteger();
        MatchCompletion completion = new MatchCompletion(matches, id -> resets.incrementAndGet(),
                arena -> releases.incrementAndGet(), (message, failure) -> {});
        completion.begin(first, () -> {});
        completion.finish(first);
        Match next = new Match(first.getFirstPlayerId(), first.getSecondPlayerId(), "nodebuff", "one");
        matches.register(next);
        assertFalse(completion.finish(first));
        assertSame(next, matches.getByArena("one"));
        assertEquals(2, resets.get());
        assertEquals(1, releases.get());
    }
}
