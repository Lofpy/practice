package com.poppy.practice.combat;

import com.poppy.practice.config.ComboConfig;
import dev.cobblesword.nachospigot.knockback.KnockbackProfile;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ComboCombatServiceTest {
    private static final double EPSILON = 0.000000001D;

    @Test
    public void appliesDedicatedProfileAndExactInvulnerabilityInterval() {
        ComboCombatService service = service();
        Player player = player(null, 20);

        service.apply(player);

        ArgumentCaptor<KnockbackProfile> profile = ArgumentCaptor.forClass(KnockbackProfile.class);
        verify(player).setKnockbackProfile(profile.capture());
        assertEquals(0.30D, profile.getValue().getHorizontal(), EPSILON);
        verify(player).setMaximumNoDamageTicks(4);
        verify(player).setNoDamageTicks(0);
        assertTrue(service.isApplied(player.getUniqueId()));
    }

    @Test
    public void restoresExactPreviousProfileObjectAndCustomMaximum() {
        ComboCombatService service = service();
        KnockbackProfile previous = mock(KnockbackProfile.class);
        Player player = player(previous, 14);
        service.apply(player);

        service.restore(player);

        InOrder order = inOrder(player);
        order.verify(player).setKnockbackProfile(any(KnockbackProfile.class));
        order.verify(player).setMaximumNoDamageTicks(4);
        order.verify(player).setKnockbackProfile(same(previous));
        order.verify(player).setMaximumNoDamageTicks(14);
        order.verify(player).setNoDamageTicks(0);
        verifyZeroInteractions(previous);
        assertFalse(service.isApplied(player.getUniqueId()));
    }

    @Test
    public void restoresNullProfileToResumeTheGlobalFallback() {
        ComboCombatService service = service();
        Player player = player(null, 20);
        service.apply(player);

        service.restore(player.getUniqueId());

        verify(player).setKnockbackProfile(null);
        verify(player).setMaximumNoDamageTicks(20);
        assertFalse(service.isApplied(player.getUniqueId()));
    }

    @Test
    public void separateParticipantsNeverShareMutableProfiles() {
        ComboCombatService service = service();
        KnockbackProfile global = mock(KnockbackProfile.class);
        Player first = player(global, 20);
        Player second = player(global, 20);
        service.apply(first);
        service.apply(second);
        ArgumentCaptor<KnockbackProfile> firstProfile = ArgumentCaptor.forClass(KnockbackProfile.class);
        ArgumentCaptor<KnockbackProfile> secondProfile = ArgumentCaptor.forClass(KnockbackProfile.class);
        verify(first).setKnockbackProfile(firstProfile.capture());
        verify(second).setKnockbackProfile(secondProfile.capture());

        firstProfile.getValue().setHorizontal(3.0D);
        firstProfile.getValue().setPearlVertical(2.0D);

        assertNotSame(global, firstProfile.getValue());
        assertNotSame(firstProfile.getValue(), secondProfile.getValue());
        assertEquals(0.30D, secondProfile.getValue().getHorizontal(), EPSILON);
        assertEquals(0.4D, secondProfile.getValue().getPearlVertical(), EPSILON);
        verifyZeroInteractions(global);
    }

    @Test
    public void repeatedApplyDoesNotReplaceSnapshotOrResetTheHurtCounter() {
        ComboCombatService service = service();
        Player player = player(null, 20);
        service.apply(player);

        service.apply(player);
        service.restore(player);

        verify(player, times(1)).setMaximumNoDamageTicks(4);
        verify(player, times(1)).getMaximumNoDamageTicks();
        verify(player, times(2)).setNoDamageTicks(0);
        verify(player).setMaximumNoDamageTicks(20);
    }

    @Test
    public void restoreIsIdempotentAndOrdinaryKitsRemainUntouched() {
        ComboCombatService service = service();
        Player ordinaryPlayer = player(null, 20);

        service.restore(ordinaryPlayer);
        service.restore(ordinaryPlayer.getUniqueId());
        service.restore((Player) null);
        service.restore((UUID) null);
        service.shutdown();

        verify(ordinaryPlayer, never()).setKnockbackProfile(any(KnockbackProfile.class));
        verify(ordinaryPlayer, never()).setMaximumNoDamageTicks(anyInt());
        verify(ordinaryPlayer, never()).setNoDamageTicks(anyInt());
    }

    @Test
    public void reloadChangesFutureMatchesOnly() {
        ComboCombatService service = service();
        Player current = player(null, 20);
        service.apply(current);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("combo.no-damage-ticks", 5);
        yaml.set("combo.knockback.horizontal", 0.2D);
        yaml.set("combo.knockback.fall-speed", 0.05D);

        service.reload(ComboConfig.load(yaml));
        Player future = player(null, 20);
        service.apply(future);

        verify(current, never()).setMaximumNoDamageTicks(10);
        ArgumentCaptor<KnockbackProfile> oldProfile = ArgumentCaptor.forClass(KnockbackProfile.class);
        ArgumentCaptor<KnockbackProfile> newProfile = ArgumentCaptor.forClass(KnockbackProfile.class);
        verify(current).setKnockbackProfile(oldProfile.capture());
        verify(future).setKnockbackProfile(newProfile.capture());
        assertEquals(0.30D, oldProfile.getValue().getHorizontal(), EPSILON);
        assertEquals(0.2D, newProfile.getValue().getHorizontal(), EPSILON);
        verify(future).setMaximumNoDamageTicks(10);
        assertEquals(0.08D, service.getFallSpeed(current.getUniqueId()), EPSILON);
        assertEquals(0.05D, service.getFallSpeed(future.getUniqueId()), EPSILON);
    }

    @Test
    public void fallSpeedIsCapturedForParticipantsAndClearedWhenRestored() {
        ComboCombatService service = service();
        Player player = player(null, 20);
        assertEquals(0.0D, service.getFallSpeed(null), EPSILON);
        assertEquals(0.0D, service.getFallSpeed(player.getUniqueId()), EPSILON);

        service.apply(player);

        assertEquals(0.08D, service.getFallSpeed(player.getUniqueId()), EPSILON);
        service.restore(player);
        assertEquals(0.0D, service.getFallSpeed(player.getUniqueId()), EPSILON);
    }

    @Test
    public void disconnectedParticipantIsRestoredUsingTheSavedEntity() {
        ComboCombatService service = service();
        Player player = player(null, 20);
        service.apply(player);
        when(player.isOnline()).thenReturn(false);

        service.restore(player.getUniqueId());

        verify(player).setKnockbackProfile(null);
        verify(player).setMaximumNoDamageTicks(20);
        assertFalse(service.isApplied(player.getUniqueId()));
    }

    @Test
    public void shutdownRestoresAllRemainingParticipants() {
        ComboCombatService service = service();
        Player first = player(null, 20);
        Player second = player(mock(KnockbackProfile.class), 12);
        service.apply(first);
        service.apply(second);

        service.shutdown();
        service.shutdown();

        verify(first, times(1)).setMaximumNoDamageTicks(20);
        verify(second, times(1)).setMaximumNoDamageTicks(12);
        assertFalse(service.isApplied(first.getUniqueId()));
        assertFalse(service.isApplied(second.getUniqueId()));
    }

    @Test
    public void partialApplicationFailureRollsBackBothCombatProperties() {
        ComboCombatService service = service();
        KnockbackProfile previous = mock(KnockbackProfile.class);
        Player player = player(previous, 20);
        RuntimeException failure = new IllegalStateException("test failure");
        doThrow(failure).when(player).setMaximumNoDamageTicks(4);

        try {
            service.apply(player);
            fail("Expected application failure");
        } catch (RuntimeException expected) {
            assertSame(failure, expected);
        }

        verify(player).setKnockbackProfile(same(previous));
        verify(player).setMaximumNoDamageTicks(20);
        assertFalse(service.isApplied(player.getUniqueId()));
    }

    @Test
    public void restorationFailureStillRestoresOtherPropertyAndCanBeRetried() {
        ComboCombatService service = service();
        KnockbackProfile previous = mock(KnockbackProfile.class);
        Player player = player(previous, 20);
        service.apply(player);
        doThrow(new IllegalStateException("retry once")).doNothing()
                .when(player).setKnockbackProfile(same(previous));

        try {
            service.restore(player);
            fail("Expected restoration failure");
        } catch (IllegalStateException expected) {
            assertEquals("retry once", expected.getMessage());
        }

        verify(player).setMaximumNoDamageTicks(20);
        assertTrue(service.isApplied(player.getUniqueId()));
        service.restore(player);
        assertFalse(service.isApplied(player.getUniqueId()));
    }

    @Test
    public void shutdownContinuesRestoringOthersWhenOneParticipantFails() {
        ComboCombatService service = service();
        KnockbackProfile previous = mock(KnockbackProfile.class);
        Player failing = player(previous, 20);
        Player other = player(null, 20);
        service.apply(failing);
        service.apply(other);
        doThrow(new IllegalStateException("test restore failure"))
                .when(failing).setKnockbackProfile(same(previous));

        try {
            service.shutdown();
            fail("Expected failure from one participant");
        } catch (IllegalStateException expected) {
            assertEquals("test restore failure", expected.getMessage());
        }

        verify(other).setKnockbackProfile(null);
        assertFalse(service.isApplied(other.getUniqueId()));
        assertTrue(service.isApplied(failing.getUniqueId()));
    }

    @Test
    public void nullReloadDoesNotLoseTheLastGoodConfiguration() {
        ComboCombatService service = service();
        try {
            service.reload(null);
            fail("Expected null configuration rejection");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
        Player player = player(null, 20);
        service.apply(player);
        verify(player).setMaximumNoDamageTicks(4);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullParticipantCannotBeApplied() {
        service().apply(null);
    }

    private static ComboCombatService service() {
        return new ComboCombatService(ComboConfig.load(new YamlConfiguration()));
    }

    private static Player player(KnockbackProfile previous, int maximumTicks) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getKnockbackProfile()).thenReturn(previous);
        when(player.getMaximumNoDamageTicks()).thenReturn(maximumTicks);
        return player;
    }
}
