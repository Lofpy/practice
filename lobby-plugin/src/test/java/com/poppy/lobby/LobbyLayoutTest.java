package com.poppy.lobby;

import org.bukkit.Material;
import org.junit.Test;
import static org.junit.Assert.*;

public class LobbyLayoutTest {
    @Test public void platformHasExactlySixtyFiveBySixtyFiveFloor() {
        int blocks = 0;
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                if (LobbyLayout.blockAt(x, LobbyLayout.FLOOR_Y, z) != Material.AIR) { blocks++; }
            }
        }
        assertEquals(65 * 65, blocks);
    }
    @Test public void outsidePlatformIsVoidAtAllRelevantHeights() {
        for (int y = 0; y < 256; y++) {
            assertEquals(Material.AIR, LobbyLayout.blockAt(33, y, 0));
            assertEquals(Material.AIR, LobbyLayout.blockAt(0, y, -33));
            assertEquals(Material.AIR, LobbyLayout.blockAt(Integer.MIN_VALUE, y, 0));
        }
    }
    @Test public void spawnHasSolidFloorAndTwoBlocksOfHeadroom() {
        assertEquals(Material.QUARTZ_BLOCK, LobbyLayout.blockAt(0, 64, 8));
        assertEquals(Material.AIR, LobbyLayout.blockAt(0, 65, 8));
        assertEquals(Material.AIR, LobbyLayout.blockAt(0, 66, 8));
    }
    @Test public void allEdgesHaveTwoBlockHighGlassRail() {
        for (int n = -32; n <= 32; n++) {
            for (int y = 65; y <= 66; y++) {
                assertEquals(Material.GLASS, LobbyLayout.blockAt(-32, y, n));
                assertEquals(Material.GLASS, LobbyLayout.blockAt(32, y, n));
                assertEquals(Material.GLASS, LobbyLayout.blockAt(n, y, -32));
                assertEquals(Material.GLASS, LobbyLayout.blockAt(n, y, 32));
            }
        }
    }
    @Test public void voidAndInvalidPositionsRequireRescue() {
        assertFalse(LobbyLayout.needsRescue(0.5, 65, 8.5));
        assertFalse(LobbyLayout.needsRescue(32, 65, 32));
        assertTrue(LobbyLayout.needsRescue(0, 60, 0));
        assertTrue(LobbyLayout.needsRescue(37, 65, 0));
        assertTrue(LobbyLayout.needsRescue(0, 65, -37));
        assertTrue(LobbyLayout.needsRescue(Double.NaN, 65, 0));
        assertTrue(LobbyLayout.needsRescue(0, Double.POSITIVE_INFINITY, 0));
    }
    @Test public void archAndLightingAreFiniteAndSpawnPathClear() {
        assertEquals(Material.QUARTZ_BLOCK, LobbyLayout.blockAt(6, 68, -16));
        assertEquals(Material.QUARTZ_BLOCK, LobbyLayout.blockAt(0, 73, -16));
        assertEquals(Material.AIR, LobbyLayout.blockAt(0, 65, -16));
        assertEquals(Material.GLOWSTONE, LobbyLayout.blockAt(24, 69, -24));
        assertEquals(Material.AIR, LobbyLayout.blockAt(24, 70, -24));
    }
}
