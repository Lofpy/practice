package com.poppy.practice.combat;

import com.poppy.practice.config.ComboConfig;
import dev.cobblesword.nachospigot.knockback.KnockbackProfile;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ComboParticipantMetadataTest {
    private static final double EPSILON = 0.000000001D;

    @Test
    public void capturesConfiguredHeightAndIdentityWhenApplyingCombo() {
        ComboCombatService service = service(2.5D);
        Player player = player();

        service.apply(player);

        assertEquals(2.5D, service.getFallHeight(player.getUniqueId()), EPSILON);
        assertNotNull(service.getParticipantToken(player.getUniqueId()));
        assertEquals(1, service.getParticipants().size());
        assertSame(player, service.getParticipants().get(0));
    }

    @Test
    public void unknownPlayersHaveNoRestrictionOrParticipantToken() {
        ComboCombatService service = service(3.0D);

        assertEquals(0.0D, service.getFallHeight(UUID.randomUUID()), EPSILON);
        assertEquals(0.0D, service.getFallHeight(null), EPSILON);
        assertNull(service.getParticipantToken(UUID.randomUUID()));
        assertNull(service.getParticipantToken(null));
        assertTrue(service.getParticipants().isEmpty());
    }

    @Test
    public void participantListIsAnIndependentSnapshot() {
        ComboCombatService service = service(3.0D);
        Player first = player();
        service.apply(first);
        List<Player> participants = service.getParticipants();

        service.apply(player());

        assertEquals(1, participants.size());
        participants.clear();
        assertEquals(2, service.getParticipants().size());
        assertTrue(service.isApplied(first.getUniqueId()));
    }

    @Test
    public void reloadChangesOnlyNewParticipantsAndRepeatedApplyKeepsMetadata() {
        ComboCombatService service = service(2.0D);
        Player current = player();
        service.apply(current);
        Object token = service.getParticipantToken(current.getUniqueId());

        service.reload(config(4.0D));
        service.apply(current);
        Player future = player();
        service.apply(future);

        assertEquals(2.0D, service.getFallHeight(current.getUniqueId()), EPSILON);
        assertEquals(4.0D, service.getFallHeight(future.getUniqueId()), EPSILON);
        assertSame(token, service.getParticipantToken(current.getUniqueId()));
        assertNotSame(token, service.getParticipantToken(future.getUniqueId()));
    }

    @Test
    public void liveUpdateChangesEveryParticipantWithoutReplacingMatchIdentity() {
        ComboCombatService service = service(2.0D);
        Player first = player();
        Player second = player();
        service.apply(first);
        service.apply(second);
        Object token = service.getParticipantToken(first.getUniqueId());

        service.updateLive(config(4.5D));

        assertEquals(4.5D, service.getFallHeight(first.getUniqueId()), EPSILON);
        assertEquals(4.5D, service.getFallHeight(second.getUniqueId()), EPSILON);
        assertSame(token, service.getParticipantToken(first.getUniqueId()));
    }

    @Test
    public void liveDisableAndRollbackRestoreEachDistinctCapturedHeight() {
        ComboCombatService service = service(2.0D);
        Player first = player();
        service.apply(first);
        service.reload(config(4.0D));
        Player second = player();
        service.apply(second);

        ComboCombatService.LiveUpdate update = service.updateLive(config(0.0D));
        assertEquals(0.0D, service.getFallHeight(first.getUniqueId()), EPSILON);
        assertEquals(0.0D, service.getFallHeight(second.getUniqueId()), EPSILON);
        update.rollback();
        update.rollback();

        assertEquals(2.0D, service.getFallHeight(first.getUniqueId()), EPSILON);
        assertEquals(4.0D, service.getFallHeight(second.getUniqueId()), EPSILON);
        assertEquals(4.0D, service.getConfiguration().getFallHeight(), EPSILON);
    }

    @Test
    public void failedLiveUpdateLeavesCapturedHeightUnchanged() {
        ComboCombatService service = service(2.0D);
        Player player = player();
        service.apply(player);
        RuntimeException failure = new IllegalStateException("update failed");
        doThrow(failure).doNothing().when(player).setKnockbackProfile(any(KnockbackProfile.class));

        try {
            service.updateLive(config(5.0D));
            fail("Expected profile update failure");
        } catch (RuntimeException expected) {
            assertSame(failure, expected);
        }

        assertEquals(2.0D, service.getFallHeight(player.getUniqueId()), EPSILON);
        assertEquals(2.0D, service.getConfiguration().getFallHeight(), EPSILON);
    }

    @Test
    public void rollbackRestoresMetadataEvenWhenNativeRestorationFails() {
        ComboCombatService service = service(2.0D);
        Player player = player();
        service.apply(player);
        ComboCombatService.LiveUpdate update = service.updateLive(config(5.0D));
        doThrow(new IllegalStateException("restore failed"))
                .when(player).setMaximumNoDamageTicks(20);

        try {
            update.rollback();
            fail("Expected native restoration failure");
        } catch (IllegalStateException expected) {
            assertEquals("restore failed", expected.getMessage());
        }

        assertEquals(2.0D, service.getFallHeight(player.getUniqueId()), EPSILON);
    }

    @Test
    public void restoreRemovesMetadataAndNextMatchGetsNewIdentity() {
        ComboCombatService service = service(2.0D);
        Player player = player();
        service.apply(player);
        Object oldToken = service.getParticipantToken(player.getUniqueId());

        service.restore(player);

        assertEquals(0.0D, service.getFallHeight(player.getUniqueId()), EPSILON);
        assertNull(service.getParticipantToken(player.getUniqueId()));
        assertTrue(service.getParticipants().isEmpty());
        service.reload(config(4.0D));
        service.apply(player);
        assertNotSame(oldToken, service.getParticipantToken(player.getUniqueId()));
        assertEquals(4.0D, service.getFallHeight(player.getUniqueId()), EPSILON);
    }

    @Test
    public void failedApplyAndSuccessfulShutdownRemoveParticipantMetadata() {
        ComboCombatService service = service(2.0D);
        Player failing = player();
        doThrow(new IllegalStateException("apply failed"))
                .when(failing).setMaximumNoDamageTicks(4);

        try {
            service.apply(failing);
            fail("Expected application failure");
        } catch (IllegalStateException expected) {
            assertEquals("apply failed", expected.getMessage());
        }

        assertNull(service.getParticipantToken(failing.getUniqueId()));
        assertEquals(0.0D, service.getFallHeight(failing.getUniqueId()), EPSILON);
        Player other = player();
        service.apply(other);
        service.shutdown();
        assertTrue(service.getParticipants().isEmpty());
        assertNull(service.getParticipantToken(other.getUniqueId()));
    }

    private static ComboCombatService service(double height) {
        return new ComboCombatService(config(height));
    }

    private static ComboConfig config(double height) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("combo.knockback.fall-height", height);
        return ComboConfig.load(yaml);
    }

    private static Player player() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getMaximumNoDamageTicks()).thenReturn(20);
        return player;
    }
}
