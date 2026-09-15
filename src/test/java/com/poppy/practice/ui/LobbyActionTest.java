package com.poppy.practice.ui;

import com.poppy.practice.player.PlayerState;
import org.bukkit.Material;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LobbyActionTest {
    @Test
    public void queuedPlayersHaveOnlyTheLeaveAction() {
        assertEquals(LobbyAction.LEAVE_QUEUE,
                LobbyAction.forSlot(PlayerState.QUEUE, 8, Material.REDSTONE));
        assertNull(LobbyAction.forSlot(PlayerState.QUEUE, 0, Material.DIAMOND_SWORD));
        assertNull(LobbyAction.forSlot(PlayerState.QUEUE, 2, Material.BOOK));
        assertNull(LobbyAction.forSlot(PlayerState.QUEUE, 4, Material.IRON_SWORD));
        assertNull(LobbyAction.forSlot(PlayerState.QUEUE, 8, Material.REDSTONE_COMPARATOR));
        assertNull(LobbyAction.forSlot(PlayerState.QUEUE, 6, Material.EXP_BOTTLE));
        assertNull(LobbyAction.forSlot(PlayerState.LOBBY, 8, Material.REDSTONE));
    }

    @Test
    public void onlyTheExpectedSlotAndMaterialWorkOutsideMatches() {
        assertEquals(LobbyAction.QUEUE,
                LobbyAction.forSlot(PlayerState.LOBBY, 0, Material.DIAMOND_SWORD));
        assertNull(LobbyAction.forSlot(PlayerState.LOBBY, 4, Material.DIAMOND_SWORD));
        assertNull(LobbyAction.forSlot(PlayerState.LOBBY, 0, Material.BOOK));
        assertNull(LobbyAction.forSlot(PlayerState.STARTING, 0, Material.DIAMOND_SWORD));
        assertNull(LobbyAction.forSlot(PlayerState.FIGHTING, 0, Material.DIAMOND_SWORD));
        assertNull(LobbyAction.forSlot(null, 0, Material.DIAMOND_SWORD));
    }

    @Test
    public void settingsShareTheLeaveQueueSlotButOnlyExistWhileIdle() {
        assertEquals(LobbyAction.SETTINGS,
                LobbyAction.forSlot(PlayerState.LOBBY, 8, Material.REDSTONE_COMPARATOR));
        assertNull(LobbyAction.forSlot(PlayerState.FIGHTING, 8, Material.REDSTONE_COMPARATOR));
        assertNull(LobbyAction.forSlot(PlayerState.STARTING, 8, Material.REDSTONE_COMPARATOR));
        assertNull(LobbyAction.forSlot(PlayerState.DEBUG, 8, Material.REDSTONE_COMPARATOR));
        assertNull(LobbyAction.forSlot(PlayerState.LOBBY, 7, Material.REDSTONE_COMPARATOR));
    }

    @Test
    public void tierTestsHaveAnIdleLobbyShortcut() {
        assertEquals(LobbyAction.TIER_TEST,
                LobbyAction.forSlot(PlayerState.LOBBY, 6, Material.EXP_BOTTLE));
        assertNull(LobbyAction.forSlot(PlayerState.FIGHTING, 6, Material.EXP_BOTTLE));
        assertNull(LobbyAction.forSlot(PlayerState.STARTING, 6, Material.EXP_BOTTLE));
    }
}
