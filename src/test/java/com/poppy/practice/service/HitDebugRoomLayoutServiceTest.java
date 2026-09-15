package com.poppy.practice.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class HitDebugRoomLayoutServiceTest {
    @Test
    public void roomIsSeparatedFromTheLastArena() {
        int lastArenaMaximumX = ArenaWorldLayoutService.arenaMinimumX(9) + 99;
        assertTrue(HitDebugRoomLayoutService.MINIMUM_X - lastArenaMaximumX > 1000);
    }

    @Test
    public void fourStationsHaveDistinctCoordinates() {
        assertEquals(20.0D, HitDebugRoomLayoutService.NORMAL_ATTACK_BOT_X
                - HitDebugRoomLayoutService.PASSIVE_BOT_X, 0.0D);
        assertEquals(18.0D, HitDebugRoomLayoutService.SPRINT_ATTACK_BOT_X
                - HitDebugRoomLayoutService.NORMAL_ATTACK_BOT_X, 0.0D);
        assertEquals(20.0D, HitDebugRoomLayoutService.PEARL_BOT_X
                - HitDebugRoomLayoutService.SPRINT_ATTACK_BOT_X, 0.0D);
        assertEquals(15.0D, HitDebugRoomLayoutService.PEARL_SECOND_Z
                - HitDebugRoomLayoutService.BOT_Z, 0.0D);
    }
}
