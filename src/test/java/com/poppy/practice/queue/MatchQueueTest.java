package com.poppy.practice.queue;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MatchQueueTest {
    @Test
    public void preservesOrderAndRejectsDuplicates() {
        MatchQueue queue = new MatchQueue("nodebuff");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(queue.add(first));
        assertFalse(queue.add(first));
        assertTrue(queue.add(second));
        assertEquals(2, queue.size());
        assertEquals(first, queue.poll());
        assertEquals(second, queue.poll());
        assertNull(queue.poll());
    }

    @Test
    public void removalUpdatesBothDataStructures() {
        MatchQueue queue = new MatchQueue("nodebuff");
        UUID playerId = UUID.randomUUID();

        queue.add(playerId);
        assertTrue(queue.remove(playerId));
        assertFalse(queue.contains(playerId));
        assertEquals(0, queue.size());
        assertNull(queue.poll());
    }

    @Test
    public void failedPairCanBeReturnedWithoutLosingQueueOrder() {
        MatchQueue queue = new MatchQueue("nodebuff");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        queue.add(first);
        queue.add(second);
        queue.add(third);

        queue.poll();
        queue.poll();
        assertTrue(queue.addFirst(second));
        assertTrue(queue.addFirst(first));
        assertFalse(queue.addFirst(first));

        assertEquals(first, queue.poll());
        assertEquals(second, queue.poll());
        assertEquals(third, queue.poll());
        assertEquals(0, queue.size());
    }

    @Test
    public void invalidEntryDoesNotCorruptMembershipCount() {
        MatchQueue queue = new MatchQueue("nodebuff");
        try {
            queue.add(null);
            fail("Null player ids must be rejected");
        } catch (IllegalArgumentException expected) {
            assertEquals(0, queue.size());
            assertFalse(queue.contains(null));
            assertNull(queue.poll());
        }
    }
}
