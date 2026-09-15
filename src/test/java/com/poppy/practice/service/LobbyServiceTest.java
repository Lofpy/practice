package com.poppy.practice.service;

import com.poppy.practice.player.PlayerState;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LobbyServiceTest {
    @Test
    public void leaveQueueItemIsOnlyShownWhileQueued() {
        assertTrue(LobbyService.shouldShowLeaveQueueItem(PlayerState.QUEUE));
        assertFalse(LobbyService.shouldShowLeaveQueueItem(PlayerState.LOBBY));
        assertFalse(LobbyService.shouldShowLeaveQueueItem(PlayerState.STARTING));
        assertFalse(LobbyService.shouldShowLeaveQueueItem(PlayerState.FIGHTING));
        assertFalse(LobbyService.shouldShowLeaveQueueItem(PlayerState.DEBUG));
        assertFalse(LobbyService.shouldShowLeaveQueueItem(null));
    }
}
