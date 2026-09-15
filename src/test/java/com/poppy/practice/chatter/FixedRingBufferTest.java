package com.poppy.practice.chatter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class FixedRingBufferTest {
    @Test
    public void capacityRemainsFixedAndOldestEntryIsDiscarded() {
        FixedRingBuffer<Integer> buffer = new FixedRingBuffer<Integer>(3);
        buffer.add(1);
        buffer.add(2);
        buffer.add(3);
        buffer.add(4);

        assertEquals(3, buffer.size());
        assertEquals(java.util.Arrays.asList(2, 3, 4), buffer.snapshot());
    }
}
