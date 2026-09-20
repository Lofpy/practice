package com.poppy.practice.listener;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerMoveListenerTest {
    @Test public void endingPlayersCanMoveButCountdownStillFreezesPosition() {
        com.poppy.practice.player.ProfileManager profiles = new com.poppy.practice.player.ProfileManager();
        org.bukkit.entity.Player player = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        java.util.UUID id = java.util.UUID.randomUUID();
        org.mockito.Mockito.when(player.getUniqueId()).thenReturn(id);
        com.poppy.practice.player.PlayerProfile profile = profiles.getOrCreate(id);
        PlayerMoveListener listener = new PlayerMoveListener(profiles);
        org.bukkit.World world = org.mockito.Mockito.mock(org.bukkit.World.class);
        org.bukkit.Location from = new org.bukkit.Location(world, 1, 4, 1);
        org.bukkit.Location to = new org.bukkit.Location(world, 2, 4, 2, 90, 10);
        profile.setState(com.poppy.practice.player.PlayerState.ENDING);
        org.bukkit.event.player.PlayerMoveEvent ending = new org.bukkit.event.player.PlayerMoveEvent(player, from, to);
        listener.onMove(ending);
        org.junit.Assert.assertEquals(to, ending.getTo());
        profile.setState(com.poppy.practice.player.PlayerState.STARTING);
        org.bukkit.event.player.PlayerMoveEvent countdown = new org.bukkit.event.player.PlayerMoveEvent(player, from, to);
        listener.onMove(countdown);
        org.junit.Assert.assertEquals(from.getX(), countdown.getTo().getX(), 0.0D);
        org.junit.Assert.assertEquals(90.0F, countdown.getTo().getYaw(), 0.0F);
    }

    @Test
    public void allowsPingDuringCountdown() {
        assertTrue(PlayerMoveListener.isAllowedDuringCountdown("/ping"));
        assertTrue(PlayerMoveListener.isAllowedDuringCountdown("/ping ignored"));
        assertFalse(PlayerMoveListener.isAllowedDuringCountdown("/spawn"));
    }

    @Test
    public void allowsComboKnockbackAdministrationDuringCountdown() {
        assertTrue(PlayerMoveListener.isAllowedDuringCountdown("/combokb"));
        assertTrue(PlayerMoveListener.isAllowedDuringCountdown("/combokb view"));
        assertTrue(PlayerMoveListener.isAllowedDuringCountdown("/combokb set horizontal 0.3"));
        assertTrue(PlayerMoveListener.isAllowedDuringCountdown("/poppypractice:combokb"));
        assertTrue(PlayerMoveListener.isAllowedDuringCountdown(
                "/poppypractice:combokb set vertical 0.1"));
    }

    @Test
    public void comboExemptionDoesNotAllowDifferentCommandsOrNamespaces() {
        assertFalse(PlayerMoveListener.isAllowedDuringCountdown("/combokbextra"));
        assertFalse(PlayerMoveListener.isAllowedDuringCountdown("/combokbextra set horizontal 0.3"));
        assertFalse(PlayerMoveListener.isAllowedDuringCountdown("/poppypractice:combokbextra"));
        assertFalse(PlayerMoveListener.isAllowedDuringCountdown("/otherplugin:combokb"));
        assertFalse(PlayerMoveListener.isAllowedDuringCountdown("/kb"));
        assertFalse(PlayerMoveListener.isAllowedDuringCountdown("/spawn"));
        assertFalse(PlayerMoveListener.isAllowedDuringCountdown(null));
    }
}
