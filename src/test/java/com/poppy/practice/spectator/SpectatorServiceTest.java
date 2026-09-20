package com.poppy.practice.spectator;

import com.poppy.practice.bot.BotMatch;
import com.poppy.practice.match.Match;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.spectator.SpectatorFixture.PlayerStub;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.junit.Test;

import static org.junit.Assert.*;

public class SpectatorServiceTest {
    @Test public void startsCompatibleInvisibleFlightAndLeavesCleanly() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer"), first = f.player("First"), second = f.player("Second");
        f.match(first, second);
        assertTrue(f.service.spectate(viewer.player, first.player));
        assertEquals(PlayerState.SPECTATING, f.profiles.get(viewer.id).getState());
        assertEquals(GameMode.ADVENTURE, viewer.gameMode);
        assertTrue(viewer.allowFlight);
        assertTrue(viewer.flying);
        assertFalse(viewer.collides);
        assertFalse(viewer.canPickup);
        assertTrue(first.hidden.contains(viewer.id));
        assertTrue(second.hidden.contains(viewer.id));
        assertFalse(viewer.hidden.contains(first.id));
        assertTrue(f.service.leave(viewer.player, true));
        assertFalse(f.service.isSpectating(viewer.id));
        assertEquals(PlayerState.LOBBY, f.profiles.get(viewer.id).getState());
        assertFalse(viewer.allowFlight);
        assertFalse(viewer.flying);
        assertTrue(viewer.collides);
        assertTrue(viewer.canPickup);
        assertFalse(viewer.sleepingIgnored);
        assertFalse(first.hidden.contains(viewer.id));
        assertTrue(f.calls.contains("lobby:Viewer"));
        assertFalse(f.service.leave(viewer.player, true));
    }

    @Test public void allNonLobbyStatesRejectWithoutResettingOrTeleporting() {
        for (PlayerState state : PlayerState.values()) {
            if (state == PlayerState.LOBBY) continue;
            SpectatorFixture f = new SpectatorFixture();
            PlayerStub viewer = f.player("Viewer"), first = f.player("First"), second = f.player("Second");
            f.match(first, second);
            f.profiles.get(viewer.id).setState(state);
            assertFalse(state.name(), f.service.spectate(viewer.player, first.player));
            assertEquals(state, f.profiles.get(viewer.id).getState());
            assertEquals(0, viewer.teleports);
            assertFalse(f.calls.contains("reset:Viewer"));
        }
    }

    @Test public void permissionSelfOfflineHiddenAndIdleTargetsAreRejected() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer"), first = f.player("First"), second = f.player("Second"), idle = f.player("Idle");
        f.match(first, second);
        viewer.permission = false;
        assertFalse(f.service.spectate(viewer.player, first.player));
        viewer.permission = true;
        assertFalse(f.service.spectate(viewer.player, viewer.player));
        assertFalse(f.service.spectate(viewer.player, null));
        assertFalse(f.service.spectate(viewer.player, idle.player));
        first.online = false;
        assertFalse(f.service.spectate(viewer.player, first.player));
        first.online = true;
        viewer.hidden.add(first.id);
        assertFalse(f.service.spectate(viewer.player, first.player));
        assertEquals(0, viewer.teleports);
        assertEquals(PlayerState.LOBBY, f.profiles.get(viewer.id).getState());
    }

    @Test public void activeParticipantCannotBypassWithStaleLobbyProfile() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer"), first = f.player("First");
        f.match(viewer, first);
        f.profiles.get(viewer.id).setState(PlayerState.LOBBY);
        assertFalse(f.service.spectate(viewer.player, first.player));
        assertEquals(0, viewer.teleports);
    }

    @Test public void endingAllowsExistingViewersButRejectsNewViewers() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer"), late = f.player("Late"), first = f.player("First"), second = f.player("Second");
        Match match = f.match(first, second);
        assertTrue(f.service.spectate(viewer.player, first.player));
        match.beginEnding();
        f.service.tick();
        assertTrue(f.service.isSpectating(viewer.id));
        assertFalse(f.service.spectate(late.player, first.player));
        f.service.handleMatchEnd(match.getId());
        assertFalse(f.service.isSpectating(viewer.id));
        assertTrue(f.calls.contains("lobby:Viewer"));
    }

    @Test public void switchingTargetToANewMatchDoesNotFollowIt() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer"), first = f.player("First"), second = f.player("Second");
        Match old = f.match(first, second);
        assertTrue(f.service.spectate(viewer.player, first.player));
        Match replacement = f.match(first, second);
        assertNotEquals(old.getId(), replacement.getId());
        f.service.tick();
        assertFalse(f.service.isSpectating(viewer.id));
        assertEquals(PlayerState.LOBBY, f.profiles.get(viewer.id).getState());
    }

    @Test public void staleEndCallbackCannotTerminateANewerSpectatingSession() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer"), first = f.player("First"), second = f.player("Second");
        Match old = f.match(first, second);
        assertTrue(f.service.spectate(viewer.player, first.player));
        f.service.handleMatchEnd(old.getId());
        f.match(first, second);
        assertTrue(f.service.spectate(viewer.player, first.player));
        f.service.handleMatchEnd(old.getId());
        assertTrue(f.service.isSpectating(viewer.id));
    }

    @Test public void botProfileLivesUntilViewingStopsEvenDuringEnding() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer"), first = f.player("First");
        BotMatch match = f.botMatch(first);
        viewer.teleportCallback = () -> assertTrue(f.calls.contains("showbot:Viewer"));
        assertTrue(f.service.spectate(viewer.player, first.player));
        match.beginEnding();
        f.service.tick();
        assertFalse(f.calls.contains("hidebot:Viewer"));
        f.service.handleMatchEnd(match.getId());
        assertTrue(f.calls.contains("hidebot:Viewer"));
        assertFalse(f.service.isSpectating(viewer.id));
    }

    @Test public void preExistingHiddenStateAndPlayerPropertiesArePreserved() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer"), first = f.player("First"), second = f.player("Second");
        f.match(first, second);
        first.hidden.add(viewer.id);
        viewer.collides = false;
        viewer.canPickup = false;
        viewer.sleepingIgnored = true;
        assertTrue(f.service.spectate(viewer.player, second.player));
        f.service.leave(viewer.player, false);
        assertTrue(first.hidden.contains(viewer.id));
        assertEquals(0, first.shows);
        assertFalse(viewer.collides);
        assertFalse(viewer.canPickup);
        assertTrue(viewer.sleepingIgnored);
    }

    @Test public void failedTeleportRollsBackSessionVisibilityAndAbilities() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer"), first = f.player("First"), second = f.player("Second");
        f.match(first, second);
        viewer.rejectTeleport = true;
        assertFalse(f.service.spectate(viewer.player, first.player));
        assertFalse(f.service.isSpectating(viewer.id));
        assertFalse(first.hidden.contains(viewer.id));
        assertFalse(viewer.allowFlight);
        assertTrue(viewer.collides);
        assertTrue(f.calls.contains("lobby:Viewer"));
    }

    @Test public void failedResetRollsBackBeforeTeleport() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer"), first = f.player("First"), second = f.player("Second");
        f.match(first, second);
        f.failReset = true;
        assertFalse(f.service.spectate(viewer.player, first.player));
        assertEquals(0, viewer.teleports);
        assertFalse(f.service.isSpectating(viewer.id));
        assertTrue(f.calls.contains("lobby:Viewer"));
    }

    @Test public void reentrantMatchEndDuringResetCannotResurrectSession() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer"), first = f.player("First"), second = f.player("Second");
        Match match = f.match(first, second);
        f.duringReset = () -> f.service.handleMatchEnd(match.getId());
        assertFalse(f.service.spectate(viewer.player, first.player));
        assertEquals(0, viewer.teleports);
        assertFalse(f.service.isSpectating(viewer.id));
        assertFalse(viewer.flying);
    }

    @Test public void visibilityAndBotFailuresDoNotPreventRemainingCleanup() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer"), first = f.player("First"), bystander = f.player("Bystander");
        f.botMatch(first);
        assertTrue(f.service.spectate(viewer.player, first.player));
        first.failShow = true;
        f.failBotHide = true;
        assertTrue(f.service.leave(viewer.player, false));
        assertFalse(f.service.isSpectating(viewer.id));
        assertFalse(viewer.flying);
        assertTrue(viewer.collides);
        assertFalse(bystander.hidden.contains(viewer.id));
        assertTrue(f.calls.contains("lobby:Viewer"));
    }

    @Test public void joiningPlayersCannotSeeExistingSpectators() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer"), first = f.player("First"), second = f.player("Second");
        f.match(first, second);
        assertTrue(f.service.spectate(viewer.player, first.player));
        PlayerStub newcomer = f.player("Newcomer");
        f.service.handleJoin(newcomer.player);
        assertTrue(newcomer.hidden.contains(viewer.id));
        f.service.leave(viewer.player, false);
        assertFalse(newcomer.hidden.contains(viewer.id));
    }

    @Test public void participantQuitEndsEverySpectatorAndViewerQuitDoesNotTeleport() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer"), other = f.player("Other"), first = f.player("First"), second = f.player("Second");
        f.match(first, second);
        assertTrue(f.service.spectate(viewer.player, first.player));
        assertTrue(f.service.spectate(other.player, second.player));
        f.service.handleQuit(viewer.player);
        assertFalse(f.service.isSpectating(viewer.id));
        assertFalse(f.calls.contains("lobby:Viewer"));
        assertTrue(f.service.isSpectating(other.id));
        f.service.handleQuit(first.player);
        assertFalse(f.service.isSpectating(other.id));
        assertTrue(f.calls.contains("lobby:Other"));
    }

    @Test public void shutdownRestoresAllSpectatorsAndRejectsNewJoins() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer"), other = f.player("Other"), first = f.player("First"), second = f.player("Second");
        f.match(first, second);
        assertTrue(f.service.spectate(viewer.player, first.player));
        assertTrue(f.service.spectate(other.player, second.player));
        f.service.shutdown();
        f.service.shutdown();
        assertFalse(f.service.isSpectating(viewer.id));
        assertFalse(f.service.isSpectating(other.id));
        assertFalse(viewer.allowFlight);
        assertFalse(other.allowFlight);
        assertFalse(f.service.spectate(viewer.player, first.player));
    }

    @Test public void missingQuitEventAndPlayerLookupStillClearSpectatorState() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer"), first = f.player("First"), second = f.player("Second");
        f.match(first, second);
        assertTrue(f.service.spectate(viewer.player, first.player));
        viewer.online = false;
        f.players.remove(viewer.id);
        f.service.tick();
        assertFalse(f.service.isSpectating(viewer.id));
        assertFalse(first.hidden.contains(viewer.id));
        assertFalse(viewer.allowFlight);
    }

    @Test public void outOfWorldNonFiniteVoidAndExcessDistanceAreRejected() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer"), first = f.player("First"), second = f.player("Second");
        f.match(first, second);
        assertTrue(f.service.spectate(viewer.player, first.player));
        assertTrue(f.service.isWithinBounds(viewer.id, new Location(f.world, 224, 68, 0)));
        assertFalse(f.service.isWithinBounds(viewer.id, new Location(f.world, 225, 68, 0)));
        assertFalse(f.service.isWithinBounds(viewer.id, new Location(f.world, 0, 0, 0)));
        assertFalse(f.service.isWithinBounds(viewer.id, new Location(f.world, Double.NaN, 68, 0)));
        assertFalse(f.service.isWithinBounds(viewer.id, new Location(SpectatorFixture.world(), 0, 68, 0)));
        viewer.location = new Location(f.world, 1000, 68, 0);
        f.service.tick();
        assertFalse(f.service.isSpectating(viewer.id));
    }

    @Test public void permissionRevocationDoesNotPreventExit() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer"), first = f.player("First"), second = f.player("Second");
        f.match(first, second);
        assertTrue(f.service.spectate(viewer.player, first.player));
        viewer.permission = false;
        assertTrue(f.service.leave(viewer.player, true));
    }

    @Test public void staleSpectatorCleanupCannotTeleportPlayerOutOfNewCombat() {
        SpectatorFixture f = new SpectatorFixture();
        PlayerStub viewer = f.player("Viewer"), first = f.player("First"), second = f.player("Second");
        f.match(first, second);
        assertTrue(f.service.spectate(viewer.player, first.player));
        f.profiles.get(viewer.id).setState(PlayerState.STARTING);
        f.service.tick();
        assertFalse(f.service.isSpectating(viewer.id));
        assertEquals(PlayerState.STARTING, f.profiles.get(viewer.id).getState());
        assertFalse(f.calls.contains("lobby:Viewer"));
        assertFalse(first.hidden.contains(viewer.id));
    }
}
