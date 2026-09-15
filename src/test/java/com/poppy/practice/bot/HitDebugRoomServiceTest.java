package com.poppy.practice.bot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class HitDebugRoomServiceTest {
    @Test
    public void debugDamageCanNeverBeLethal() {
        assertEquals(19.0D, HitDebugRoomService.nonLethalRawDamage(100.0D, 20.0D), 0.0D);
        assertEquals(7.0D, HitDebugRoomService.nonLethalRawDamage(7.0D, 20.0D), 0.0D);
        assertEquals(0.0D, HitDebugRoomService.nonLethalRawDamage(-1.0D, 20.0D), 0.0D);
    }
}
