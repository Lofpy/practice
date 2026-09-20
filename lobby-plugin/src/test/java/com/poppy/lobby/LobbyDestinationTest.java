package com.poppy.lobby;

import org.junit.Test;
import static org.junit.Assert.*;

public class LobbyDestinationTest {
    @Test public void twoModesAreSymmetricalAroundMiddleSlot() {
        assertEquals(11, LobbyDestination.PRACTICE.slot());
        assertEquals(15, LobbyDestination.SURVIVAL.slot());
        assertEquals(26, LobbyDestination.PRACTICE.slot() + LobbyDestination.SURVIVAL.slot());
    }

    @Test public void onlyModeSlotsRouteConnections() {
        for (int slot = -999; slot < 64; slot++) {
            if (slot == 11) assertEquals(LobbyDestination.PRACTICE, LobbyDestination.atSlot(slot));
            else if (slot == 15) assertEquals(LobbyDestination.SURVIVAL, LobbyDestination.atSlot(slot));
            else assertNull(LobbyDestination.atSlot(slot));
        }
    }
}
