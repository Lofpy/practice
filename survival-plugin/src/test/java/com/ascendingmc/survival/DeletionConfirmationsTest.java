package com.ascendingmc.survival;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public final class DeletionConfirmationsTest {
    @Test public void tokenIsBoundToAdminAndWorldAndConsumedOnlyOnce() {
        DeletionConfirmations confirmations = new DeletionConfirmations(() -> 100, 30000);
        String token = confirmations.request("alice", "event");
        assertNull(confirmations.consume("bob", token));
        assertNull(confirmations.consume("alice", "wrong"));
        assertEquals("event", confirmations.consume("alice", token));
        assertNull(confirmations.consume("alice", token));
    }

    @Test public void tokenExpiresAtExactDeadline() {
        AtomicLong clock = new AtomicLong(100);
        DeletionConfirmations confirmations = new DeletionConfirmations(clock::get, 30000);
        String token = confirmations.request("alice", "event");
        clock.set(30100);
        assertNull(confirmations.consume("alice", token));
    }

    @Test public void newerRequestReplacesOldAndDisconnectInvalidates() {
        DeletionConfirmations confirmations = new DeletionConfirmations(() -> 100, 30000);
        String first = confirmations.request("alice", "first");
        String second = confirmations.request("alice", "second");
        assertNotEquals(first, second);
        assertNull(confirmations.consume("alice", first));
        confirmations.forget("alice");
        assertNull(confirmations.consume("alice", second));
    }

    @Test public void administratorsHaveIndependentConfirmations() {
        DeletionConfirmations confirmations = new DeletionConfirmations(() -> 100, 30000);
        String alice = confirmations.request("alice", "first");
        String bob = confirmations.request("bob", "second");
        assertEquals("first", confirmations.consume("alice", alice));
        assertEquals("second", confirmations.consume("bob", bob));
    }

    @Test public void rejectsNonPositiveLifetime() {
        assertThrows(IllegalArgumentException.class, () -> new DeletionConfirmations(() -> 0, 0));
    }
}
