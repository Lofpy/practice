package com.poppy.practice.bot;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BotServiceTest {
    @Test
    public void allowsTheFullOneHundredByOneHundredFiftyArenaDiagonal() {
        assertFalse(BotService.exceedsArenaDistance(111.0D * 111.0D));
        assertFalse(BotService.exceedsArenaDistance(181.0D * 181.0D));
        assertFalse(BotService.exceedsArenaDistance(200.0D * 200.0D));
        assertTrue(BotService.exceedsArenaDistance(200.01D * 200.01D));
    }
}
