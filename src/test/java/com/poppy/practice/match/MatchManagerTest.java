package com.poppy.practice.match;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class MatchManagerTest {
    @Test
    public void rejectsDuplicateParticipantsWithoutPartiallyRegisteringNewMatch() {
        MatchManager manager = new MatchManager();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        Match existing = new Match(first, second, "nodebuff", "one");
        assertTrue(manager.register(existing));

        assertFalse(manager.register(new Match(third, first, "nodebuff", "two")));
        assertSame(existing, manager.getByPlayer(first));
        assertNull(manager.getByPlayer(third));
        assertNull(manager.getByArena("two"));
        assertEquals(1, manager.size());
    }

    @Test
    public void delayedRemovalOfOldMatchDoesNotRemoveReplacement() {
        MatchManager manager = new MatchManager();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Match old = new Match(first, second, "nodebuff", "one");
        manager.register(old);
        manager.remove(old);
        Match replacement = new Match(first, second, "nodebuff", "one");
        assertTrue(manager.register(replacement));

        manager.remove(old);
        assertSame(replacement, manager.getByPlayer(first));
        assertSame(replacement, manager.getByPlayer(second));
        assertSame(replacement, manager.getByArena("one"));
    }

    @Test
    public void endedMatchCannotBeRegisteredAgain() {
        MatchManager manager = new MatchManager();
        Match match = new Match(UUID.randomUUID(), UUID.randomUUID(), "nodebuff", "one");
        match.beginEnding();
        assertFalse(manager.register(match));
        match.markFinished();
        assertFalse(manager.register(match));
        assertEquals(0, manager.size());
    }
}
