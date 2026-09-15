package com.poppy.practice.network;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerLatencyServiceTest {
    @Test
    public void acceptsSafeAdditionalPingValues() {
        assertTrue(PlayerLatencyService.isValidAdditionalPing(0));
        assertTrue(PlayerLatencyService.isValidAdditionalPing(100));
        assertTrue(PlayerLatencyService.isValidAdditionalPing(2000));
        assertFalse(PlayerLatencyService.isValidAdditionalPing(-1));
        assertFalse(PlayerLatencyService.isValidAdditionalPing(2001));
    }

    @Test
    public void splitsRoundTripPingAcrossBothDirections() {
        assertEquals(0L, PlayerLatencyService.oneWayDelay(0));
        assertEquals(50L, PlayerLatencyService.oneWayDelay(100));
        assertEquals(51L, PlayerLatencyService.oneWayDelay(101));
    }

    @Test
    public void loweringDelayCannotOvertakeAnAlreadyQueuedPacket() {
        long firstNow = 1_000_000_000L;
        long firstDue = PlayerLatencyService.monotonicDueNanoTime(
                firstNow, 50_000_000L, 0L);
        long secondDue = PlayerLatencyService.monotonicDueNanoTime(
                firstNow + 1_000_000L, 0L, firstDue);

        assertEquals(firstDue + 1L, secondDue);
    }
}
