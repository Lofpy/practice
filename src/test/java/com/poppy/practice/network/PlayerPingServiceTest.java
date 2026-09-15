package com.poppy.practice.network;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlayerPingServiceTest {
    @Test
    public void normalizesPingForSafeDisplay() {
        assertEquals(0, PlayerPingService.normalizePing(-10));
        assertEquals(73, PlayerPingService.normalizePing(73));
        assertEquals(9999, PlayerPingService.normalizePing(12000));
    }
}
