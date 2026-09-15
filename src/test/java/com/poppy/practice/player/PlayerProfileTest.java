package com.poppy.practice.player;

import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class PlayerProfileTest {
    @Test
    public void countdownAndFightingBelongToTheSameCombatSession() {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID());
        profile.setState(PlayerState.STARTING);
        long session = profile.getCombatSessionVersion();
        assertTrue(profile.isSameCombatSession(session));
        profile.setState(PlayerState.FIGHTING);
        assertTrue(profile.isSameCombatSession(session));
        profile.setState(PlayerState.FIGHTING);
        assertTrue(profile.isSameCombatSession(session));
    }

    @Test
    public void oldWorkIsInvalidAfterReturningToLobbyOrStartingAnotherMatch() {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID());
        profile.setState(PlayerState.FIGHTING);
        long session = profile.getCombatSessionVersion();
        profile.setState(PlayerState.LOBBY);
        assertFalse(profile.isSameCombatSession(session));
        profile.setState(PlayerState.STARTING);
        profile.setState(PlayerState.FIGHTING);
        assertFalse(profile.isSameCombatSession(session));
        assertTrue(profile.isSameCombatSession(profile.getCombatSessionVersion()));
    }

    @Test
    public void debugAndQueueNeverCountAsAnActiveMatch() {
        PlayerProfile profile = new PlayerProfile(UUID.randomUUID());
        for (PlayerState state : new PlayerState[] {
                PlayerState.LOBBY, PlayerState.QUEUE, PlayerState.DEBUG}) {
            profile.setState(state);
            assertFalse(profile.isSameCombatSession(profile.getCombatSessionVersion()));
        }
    }
}
