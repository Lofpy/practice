package com.poppy.practice.duel;

import com.poppy.practice.arena.ArenaManager;
import com.poppy.practice.kit.KitManager;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Command ingress and acceptance must fail closed before attempting to start any match. */
public class DuelServiceSecurityTest {
    private final ProfileManager profiles = new ProfileManager();
    private final List<BaseComponent[]> messages = new ArrayList<BaseComponent[]>();
    private final Player first = player("First");
    private final Player second = player("Second");
    private DuelService service;
    private DuelRequests requests;
    private Field serverField;
    private Object previousServer;

    @Before public void installServer() throws Exception {
        Server server = mock(Server.class);
        when(server.getPlayerExact("First")).thenReturn(first);
        when(server.getPlayerExact("Second")).thenReturn(second);
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        serverField.set(null, server);
        // An empty real registry exercises "arena unavailable" without ever constructing MatchService.
        service = new DuelService(null, profiles, new KitManager(), new ArenaManager(null), null);
        Field field = DuelService.class.getDeclaredField("requests");
        field.setAccessible(true);
        requests = (DuelRequests) field.get(service);
    }

    @After public void restoreServer() throws Exception {
        if (serverField != null) serverField.set(null, previousServer);
    }

    @Test public void uncertifiedIdlePlayersCanInviteWithoutBeingPlacedInQueue() {
        command(first, "Second", "boxing");
        DuelRequests.Request request = requests.find(first.getUniqueId(), second.getUniqueId(), System.currentTimeMillis());
        assertNotNull(request);
        assertEquals("boxing", request.kit);
        assertEquals(1, messages.size());
        assertEquals(PlayerState.LOBBY, profiles.get(first.getUniqueId()).getState());
        assertEquals(PlayerState.LOBBY, profiles.get(second.getUniqueId()).getState());
        assertNull(profiles.get(first.getUniqueId()).getQueuedKitId());
    }

    @Test public void neitherParticipantCanInviteFromAnyNonLobbyState() {
        for (PlayerState state : PlayerState.values()) {
            if (state == PlayerState.LOBBY) continue;
            profiles.get(first.getUniqueId()).setState(state);
            command(first, "Second", "boxing");
            assertNull(requests.find(first.getUniqueId(), second.getUniqueId(), System.currentTimeMillis()));
            profiles.get(first.getUniqueId()).setState(PlayerState.LOBBY);
            profiles.get(second.getUniqueId()).setState(state);
            command(first, "Second", "boxing");
            assertNull(requests.find(first.getUniqueId(), second.getUniqueId(), System.currentTimeMillis()));
            profiles.get(second.getUniqueId()).setState(PlayerState.LOBBY);
        }
        assertTrue(messages.isEmpty());
    }

    @Test public void selfUnknownPlayerAndUnknownKitCannotProduceInvitations() {
        command(first, "First", "boxing");
        command(first, "Missing", "boxing");
        command(first, "Second", "unknown-kit");
        assertTrue(messages.isEmpty());
        assertNull(requests.find(first.getUniqueId(), second.getUniqueId(), System.currentTimeMillis()));
    }

    @Test public void offlineAndDeadPlayersCannotBeInvited() {
        when(second.isOnline()).thenReturn(false);
        command(first, "Second", "boxing");
        when(second.isOnline()).thenReturn(true);
        when(second.isDead()).thenReturn(true);
        command(first, "Second", "boxing");
        assertTrue(messages.isEmpty());
        assertNull(requests.find(first.getUniqueId(), second.getUniqueId(), System.currentTimeMillis()));
    }

    @Test public void acceptanceRechecksBothProfilesAndRetainsRequestWhileBusy() {
        command(first, "Second", "boxing");
        profiles.get(first.getUniqueId()).setState(PlayerState.QUEUE);
        command(second, "accept", "First");
        assertNotNull(requests.find(first.getUniqueId(), second.getUniqueId(), System.currentTimeMillis()));
        profiles.get(first.getUniqueId()).setState(PlayerState.LOBBY);
        profiles.get(second.getUniqueId()).setState(PlayerState.FIGHTING);
        command(second, "accept", "First");
        assertNotNull(requests.find(first.getUniqueId(), second.getUniqueId(), System.currentTimeMillis()));
        assertEquals(PlayerState.FIGHTING, profiles.get(second.getUniqueId()).getState());
    }

    @Test public void noAvailableArenaDoesNotConsumeInvitation() {
        command(first, "Second", "boxing");
        command(second, "accept", "First");
        assertNotNull(requests.find(first.getUniqueId(), second.getUniqueId(), System.currentTimeMillis()));
        assertEquals(PlayerState.LOBBY, profiles.get(first.getUniqueId()).getState());
        assertEquals(PlayerState.LOBBY, profiles.get(second.getUniqueId()).getState());
    }

    @Test public void declineAndQuitRemoveOnlyRelevantInvitations() {
        command(first, "Second", "boxing");
        command(second, "deny", "First");
        assertNull(requests.find(first.getUniqueId(), second.getUniqueId(), System.currentTimeMillis()));
        command(first, "Second", "combo");
        service.quit(new PlayerQuitEvent(second, ""));
        assertNull(requests.find(first.getUniqueId(), second.getUniqueId(), System.currentTimeMillis()));
    }

    private void command(Player player, String... args) {
        assertTrue(service.onCommand(player, null, "duel", args));
    }

    private Player player(String name) {
        Player mocked = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(mocked.getUniqueId()).thenReturn(id);
        when(mocked.getName()).thenReturn(name);
        when(mocked.isOnline()).thenReturn(true);
        when(mocked.spigot()).thenReturn(new Player.Spigot() {
            @Override public void sendMessage(BaseComponent... components) { messages.add(components); }
        });
        profiles.create(id);
        return mocked;
    }
}
