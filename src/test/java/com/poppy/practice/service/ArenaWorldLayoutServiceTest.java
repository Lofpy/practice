package com.poppy.practice.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class ArenaWorldLayoutServiceTest {
    @Test
    public void placesTenArenasAtOneThousandBlockIntervals() {
        assertEquals(950, ArenaWorldLayoutService.arenaMinimumX(0));
        assertEquals(1950, ArenaWorldLayoutService.arenaMinimumX(1));
        assertEquals(9950, ArenaWorldLayoutService.arenaMinimumX(9));
    }

    @Test
    public void enclosureUsesFiftyBlockHighGlassWallsOutsideTheFloor() {
        assertEquals(12400, ArenaWorldLayoutService.enclosureBlockCount(61, 61));
        assertEquals(25200, ArenaWorldLayoutService.enclosureBlockCount(100, 150));
    }

    @Test
    public void blockCountIncludesFloorsAndGlassEnclosures() {
        assertEquals(879284, ArenaWorldLayoutService.totalPlacedBlocks());
    }
}
