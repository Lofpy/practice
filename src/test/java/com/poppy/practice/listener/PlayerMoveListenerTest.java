package com.poppy.practice.listener;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerMoveListenerTest {
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
