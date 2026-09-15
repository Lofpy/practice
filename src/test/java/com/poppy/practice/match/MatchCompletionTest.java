package com.poppy.practice.match;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class MatchCompletionTest {
    @Test
    public void snapshotFailureStillReturnsBothPlayersAndReleasesTheArena() {
        MatchManager manager = new MatchManager();
        Match match = match();
        manager.register(match);
        List<String> operations = new ArrayList<String>();
        List<RuntimeException> failures = new ArrayList<RuntimeException>();
        RuntimeException snapshotFailure = new IllegalStateException("Inventory snapshot failed");
        MatchCompletion completion = new MatchCompletion(manager,
                playerId -> operations.add("lobby:" + playerId),
                arenaId -> operations.add("release:" + arenaId),
                (message, exception) -> failures.add(exception));

        assertTrue(completion.complete(match, () -> { throw snapshotFailure; }));

        assertEquals(Arrays.asList("lobby:" + match.getFirstPlayerId(),
                "lobby:" + match.getSecondPlayerId(), "release:one"), operations);
        assertEquals(0, manager.size());
        assertEquals(MatchState.FINISHED, match.getState());
        assertEquals(1, failures.size());
        assertSame(snapshotFailure, failures.get(0));
    }

    @Test
    public void oneFailedPlayerResetDoesNotStrandTheirOpponentOrArena() {
        MatchManager manager = new MatchManager();
        Match match = match();
        manager.register(match);
        List<UUID> returned = new ArrayList<UUID>();
        List<String> released = new ArrayList<String>();
        AtomicInteger failures = new AtomicInteger();
        MatchCompletion completion = new MatchCompletion(manager, playerId -> {
            returned.add(playerId);
            if (playerId.equals(match.getFirstPlayerId())) {
                throw new IllegalStateException("Player reset failed");
            }
        }, released::add, (message, exception) -> failures.incrementAndGet());

        assertTrue(completion.complete(match, () -> { }));

        assertEquals(Arrays.asList(match.getFirstPlayerId(), match.getSecondPlayerId()), returned);
        assertEquals(Arrays.asList("one"), released);
        assertEquals(1, failures.get());
        assertEquals(MatchState.FINISHED, match.getState());
    }

    @Test
    public void duplicateEndCallsAndReentrantResultEventsOnlyCleanUpOnce() {
        MatchManager manager = new MatchManager();
        Match match = match();
        manager.register(match);
        AtomicInteger returned = new AtomicInteger();
        AtomicInteger released = new AtomicInteger();
        MatchCompletion completion = new MatchCompletion(manager,
                playerId -> returned.incrementAndGet(), arenaId -> released.incrementAndGet(),
                (message, exception) -> { throw exception; });

        assertTrue(completion.complete(match,
                () -> assertFalse(completion.complete(match, () -> { }))));
        assertFalse(completion.complete(match, () -> { }));

        assertEquals(2, returned.get());
        assertEquals(1, released.get());
    }

    @Test
    public void staleMatchCannotReleaseANewMatchesArenaOrResetItsPlayers() {
        MatchManager manager = new MatchManager();
        Match stale = match();
        Match current = new Match(stale.getFirstPlayerId(), stale.getSecondPlayerId(), "nodebuff", "one");
        manager.register(current);
        AtomicInteger operations = new AtomicInteger();
        MatchCompletion completion = new MatchCompletion(manager,
                playerId -> operations.incrementAndGet(), arenaId -> operations.incrementAndGet(),
                (message, exception) -> { throw exception; });

        assertFalse(completion.complete(stale, () -> operations.incrementAndGet()));

        assertSame(current, manager.getByArena("one"));
        assertSame(current, manager.getByPlayer(current.getFirstPlayerId()));
        assertEquals(0, operations.get());
    }

    private Match match() {
        return new Match(UUID.randomUUID(), UUID.randomUUID(), "nodebuff", "one");
    }
}
