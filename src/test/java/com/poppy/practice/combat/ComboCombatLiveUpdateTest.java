package com.poppy.practice.combat;

import com.poppy.practice.config.ComboConfig;
import dev.cobblesword.nachospigot.knockback.KnockbackProfile;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ComboCombatLiveUpdateTest {
    private static final double EPSILON = 0.000000001D;

    @Test
    public void updatesEveryTrackedHumanAndNpcButNotOrdinaryParticipants() {
        ComboCombatService service = service();
        Participant human = new Participant();
        Participant npc = new Participant();
        Participant ordinary = new Participant();
        service.apply(human.player);
        service.apply(npc.player);
        KnockbackProfile previousHuman = human.profile;
        KnockbackProfile previousNpc = npc.profile;
        ComboConfig changed = configuration(0.44D, 3);

        service.updateLive(changed);

        assertSame(changed, service.getConfiguration());
        assertEquals(0.44D, human.profile.getHorizontal(), EPSILON);
        assertEquals(0.44D, npc.profile.getHorizontal(), EPSILON);
        assertNotSame(previousHuman, human.profile);
        assertNotSame(previousNpc, npc.profile);
        assertNotSame(human.profile, npc.profile);
        assertEquals(6, human.maximum);
        assertEquals(6, npc.maximum);
        assertSame(ordinary.originalProfile, ordinary.profile);
        assertEquals(20, ordinary.maximum);
        verify(ordinary.player, never()).setKnockbackProfile(any(KnockbackProfile.class));
    }

    @Test
    public void subsequentParticipantsUseTheNewConfiguration() {
        ComboCombatService service = service();
        service.updateLive(configuration(0.52D, 6));
        Participant participant = new Participant();

        service.apply(participant.player);

        assertEquals(0.52D, participant.profile.getHorizontal(), EPSILON);
        assertEquals(12, participant.maximum);
    }

    @Test
    public void originalPreMatchSnapshotSurvivesRepeatedLiveUpdates() {
        ComboCombatService service = service();
        Participant participant = new Participant();
        participant.maximum = 16;
        service.apply(participant.player);
        service.updateLive(configuration(0.52D, 6));
        service.updateLive(configuration(0.61D, 9));

        service.restore(participant.player);

        assertSame(participant.originalProfile, participant.profile);
        assertEquals(16, participant.maximum);
        assertEquals(0, participant.remaining);
        assertFalse(service.isApplied(participant.player.getUniqueId()));
    }

    @Test
    public void kbOnlyChangeNeverWritesOrClearsHurtCounters() {
        ComboCombatService service = service();
        Participant participant = new Participant();
        service.apply(participant.player);
        participant.remaining = 3;

        service.updateLive(configuration(0.52D, 2));

        assertEquals(3, participant.remaining);
        assertEquals(4, participant.maximum);
        verify(participant.player, times(1)).setMaximumNoDamageTicks(anyInt());
        verify(participant.player, times(1)).setNoDamageTicks(anyInt());
    }

    @Test
    public void intervalIncreasePreservesElapsedHurtAge() {
        ComboCombatService service = service();
        Participant participant = new Participant();
        service.apply(participant.player);
        participant.remaining = 3;

        service.updateLive(configuration(0.30D, 5));

        assertEquals(10, participant.maximum);
        assertEquals(9, participant.remaining);
    }

    @Test
    public void intervalDecreaseExpiresAnOldHurtCounterWithoutAddingImmunity() {
        ComboCombatService service = new ComboCombatService(configuration(0.30D, 5));
        Participant participant = new Participant();
        service.apply(participant.player);
        participant.remaining = 7;

        service.updateLive(configuration(0.30D, 1));

        assertEquals(2, participant.maximum);
        assertEquals(0, participant.remaining);
    }

    @Test
    public void intervalIncreaseCannotMakeAnAlreadyHittableParticipantImmuneAgain() {
        ComboCombatService service = service();
        Participant participant = new Participant();
        service.apply(participant.player);
        participant.remaining = 2;

        service.updateLive(configuration(0.30D, 7));

        assertEquals(14, participant.maximum);
        assertEquals(7, participant.remaining);
        assertTrue(participant.remaining <= participant.maximum / 2);
    }

    @Test
    public void intervalChangeDoesNotStartAHurtCounterOnAnUnhurtParticipant() {
        ComboCombatService service = service();
        Participant participant = new Participant();
        service.apply(participant.player);

        service.updateLive(configuration(0.30D, 7));

        assertEquals(0, participant.remaining);
    }

    @Test
    public void zeroIntervalImmediatelyRemovesRemainingImmunity() {
        ComboCombatService service = service();
        Participant participant = new Participant();
        service.apply(participant.player);
        participant.remaining = 3;

        service.updateLive(configuration(0.30D, 0));

        assertEquals(0, participant.maximum);
        assertEquals(0, participant.remaining);
    }

    @Test
    public void enablingAnIntervalFromZeroDoesNotInjectFreshImmunity() {
        ComboCombatService service = new ComboCombatService(configuration(0.30D, 0));
        Participant participant = new Participant();
        service.apply(participant.player);

        service.updateLive(configuration(0.30D, 7));

        assertEquals(14, participant.maximum);
        assertEquals(0, participant.remaining);
    }

    @Test
    public void persistenceRollbackRestoresExactPreviousActiveStateAndConfiguration() {
        ComboCombatService service = service();
        Participant participant = new Participant();
        service.apply(participant.player);
        service.updateLive(configuration(0.42D, 5));
        participant.remaining = 7;
        KnockbackProfile previousProfile = participant.profile;
        ComboConfig previousConfig = service.getConfiguration();

        ComboCombatService.LiveUpdate update = service.updateLive(configuration(0.52D, 7));
        update.rollback();

        assertSame(previousProfile, participant.profile);
        assertSame(previousConfig, service.getConfiguration());
        assertEquals(10, participant.maximum);
        assertEquals(7, participant.remaining);
        assertTrue(service.isApplied(participant.player.getUniqueId()));
        service.restore(participant.player);
        assertSame(participant.originalProfile, participant.profile);
        assertEquals(20, participant.maximum);
    }

    @Test
    public void successfulRollbackIsIdempotentEvenAfterAnotherUpdate() {
        ComboCombatService service = service();
        Participant participant = new Participant();
        service.apply(participant.player);
        ComboCombatService.LiveUpdate update = service.updateLive(configuration(0.42D, 5));
        update.rollback();
        ComboConfig latest = configuration(0.52D, 7);
        service.updateLive(latest);
        KnockbackProfile latestProfile = participant.profile;

        update.rollback();

        assertSame(latestProfile, participant.profile);
        assertSame(latest, service.getConfiguration());
        assertEquals(14, participant.maximum);
    }

    @Test
    public void getterFailureHappensBeforeAnyParticipantIsMutated() {
        ComboCombatService service = service();
        Participant first = new Participant();
        Participant second = new Participant();
        service.apply(first.player);
        service.apply(second.player);
        KnockbackProfile firstProfile = first.profile;
        KnockbackProfile secondProfile = second.profile;
        ComboConfig previous = service.getConfiguration();
        when(second.player.getNoDamageTicks()).thenThrow(new IllegalStateException("capture failed"));

        try {
            service.updateLive(configuration(0.42D, 5));
            fail("Expected capture failure");
        } catch (IllegalStateException expected) {
            assertEquals("capture failed", expected.getMessage());
        }

        assertSame(firstProfile, first.profile);
        assertSame(secondProfile, second.profile);
        assertSame(previous, service.getConfiguration());
        verify(first.player, times(1)).setKnockbackProfile(any(KnockbackProfile.class));
        verify(second.player, times(1)).setKnockbackProfile(any(KnockbackProfile.class));
    }

    @Test
    public void partialSetterFailureRestoresEveryTouchedParticipantIncludingTheFailingOne() {
        ComboCombatService service = service();
        Participant first = new Participant();
        Participant second = new Participant();
        service.apply(first.player);
        service.apply(second.player);
        first.remaining = 3;
        second.remaining = 2;
        KnockbackProfile firstProfile = first.profile;
        KnockbackProfile secondProfile = second.profile;
        ComboConfig previous = service.getConfiguration();
        AtomicInteger changed = new AtomicInteger();
        RuntimeException failure = new IllegalStateException("second write failed after mutation");
        Consumer<KnockbackProfile> failOnSecondUpdate = profile -> {
            if (profile.getHorizontal() == 0.42D && changed.incrementAndGet() == 2) {
                throw failure;
            }
        };
        first.afterProfileWrite = failOnSecondUpdate;
        second.afterProfileWrite = failOnSecondUpdate;

        try {
            service.updateLive(configuration(0.42D, 5));
            fail("Expected update failure");
        } catch (RuntimeException expected) {
            assertSame(failure, expected);
        }

        assertEquals(2, changed.get());
        assertSame(firstProfile, first.profile);
        assertSame(secondProfile, second.profile);
        assertSame(previous, service.getConfiguration());
        assertEquals(4, first.maximum);
        assertEquals(4, second.maximum);
        assertEquals(3, first.remaining);
        assertEquals(2, second.remaining);
        assertTrue(service.isApplied(first.player.getUniqueId()));
        assertTrue(service.isApplied(second.player.getUniqueId()));
    }

    @Test
    public void maximumSetterFailureAlsoRollsBackTheAlreadyChangedProfile() {
        ComboCombatService service = service();
        Participant participant = new Participant();
        service.apply(participant.player);
        participant.remaining = 3;
        KnockbackProfile previousProfile = participant.profile;
        ComboConfig previousConfig = service.getConfiguration();
        doThrow(new IllegalStateException("maximum failed")).when(participant.player)
                .setMaximumNoDamageTicks(10);

        try {
            service.updateLive(configuration(0.42D, 5));
            fail("Expected maximum write failure");
        } catch (IllegalStateException expected) {
            assertEquals("maximum failed", expected.getMessage());
        }

        assertSame(previousProfile, participant.profile);
        assertSame(previousConfig, service.getConfiguration());
        assertEquals(4, participant.maximum);
        assertEquals(3, participant.remaining);
    }

    @Test
    public void rollbackAttemptsEveryPropertyAndParticipantEvenIfProfileRestorationFails() {
        ComboCombatService service = service();
        Participant first = new Participant();
        Participant second = new Participant();
        service.apply(first.player);
        service.apply(second.player);
        first.remaining = 3;
        second.remaining = 2;
        KnockbackProfile firstProfile = first.profile;
        KnockbackProfile secondProfile = second.profile;
        ComboConfig previous = service.getConfiguration();
        ComboCombatService.LiveUpdate update = service.updateLive(configuration(0.42D, 5));
        first.afterProfileWrite = profile -> {
            if (profile == firstProfile) {
                throw new IllegalStateException("first rollback failed");
            }
        };
        second.afterProfileWrite = profile -> {
            if (profile == secondProfile) {
                throw new IllegalStateException("second rollback failed");
            }
        };

        try {
            update.rollback();
            fail("Expected restoration failure");
        } catch (IllegalStateException expected) {
            assertEquals(1, expected.getSuppressed().length);
        }

        assertSame(firstProfile, first.profile);
        assertSame(secondProfile, second.profile);
        assertSame(previous, service.getConfiguration());
        assertEquals(4, first.maximum);
        assertEquals(4, second.maximum);
        assertEquals(3, first.remaining);
        assertEquals(2, second.remaining);
        first.afterProfileWrite = null;
        second.afterProfileWrite = null;
        update.rollback();
    }

    @Test
    public void rollbackFailureIsSuppressedUnderTheOriginalApplicationFailure() {
        ComboCombatService service = service();
        Participant participant = new Participant();
        service.apply(participant.player);
        KnockbackProfile previousProfile = participant.profile;
        ComboConfig previousConfig = service.getConfiguration();
        RuntimeException applicationFailure = new IllegalStateException("apply failed");
        RuntimeException rollbackFailure = new IllegalStateException("rollback failed");
        participant.afterProfileWrite = profile -> {
            throw profile == previousProfile ? rollbackFailure : applicationFailure;
        };

        try {
            service.updateLive(configuration(0.42D, 5));
            fail("Expected application failure");
        } catch (RuntimeException expected) {
            assertSame(applicationFailure, expected);
            assertEquals(1, expected.getSuppressed().length);
            assertSame(rollbackFailure, expected.getSuppressed()[0]);
        }

        assertSame(previousConfig, service.getConfiguration());
        assertSame(previousProfile, participant.profile);
        assertEquals(4, participant.maximum);
    }

    @Test
    public void reusingTheSameFailureDuringRollbackDoesNotMaskTheApplicationFailure() {
        ComboCombatService service = service();
        Participant participant = new Participant();
        service.apply(participant.player);
        RuntimeException repeatedFailure = new IllegalStateException("reused failure");
        participant.afterProfileWrite = profile -> {
            throw repeatedFailure;
        };

        try {
            service.updateLive(configuration(0.42D, 5));
            fail("Expected application failure");
        } catch (RuntimeException expected) {
            assertSame(repeatedFailure, expected);
        }

        assertEquals(4, participant.maximum);
        assertEquals(0, participant.remaining);
    }

    @Test
    public void nullUpdateCannotChangeExistingConfigurationOrParticipants() {
        ComboCombatService service = service();
        Participant participant = new Participant();
        service.apply(participant.player);
        KnockbackProfile previousProfile = participant.profile;
        ComboConfig previousConfig = service.getConfiguration();

        try {
            service.updateLive(null);
            fail("Expected null rejection");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }

        assertSame(previousProfile, participant.profile);
        assertSame(previousConfig, service.getConfiguration());
    }

    @Test
    public void nativeHurtTimerDoesNotIncludeOrModifyTheSeparateSpawnProtection() {
        ComboCombatService service = service();
        CraftPlayer player = mock(CraftPlayer.class);
        EntityPlayer nativePlayer = mock(EntityPlayer.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getHandle()).thenReturn(nativePlayer);
        when(player.getMaximumNoDamageTicks()).thenReturn(20);
        // Model the actual CraftPlayer getter, which merges two independent timers.
        when(player.getNoDamageTicks()).thenAnswer(invocation ->
                Math.max(nativePlayer.invulnerableTicks, nativePlayer.noDamageTicks));
        doAnswer(invocation -> {
            nativePlayer.noDamageTicks = (Integer) invocation.getArguments()[0];
            return null;
        }).when(player).setNoDamageTicks(anyInt());
        service.apply(player);
        when(player.getMaximumNoDamageTicks()).thenReturn(4);
        nativePlayer.invulnerableTicks = 60;
        nativePlayer.noDamageTicks = 3;

        ComboCombatService.LiveUpdate update = service.updateLive(configuration(0.42D, 5));

        assertEquals(9, nativePlayer.noDamageTicks);
        assertEquals(60, nativePlayer.invulnerableTicks);
        update.rollback();
        assertEquals(3, nativePlayer.noDamageTicks);
        assertEquals(60, nativePlayer.invulnerableTicks);
        verify(player, never()).getNoDamageTicks();
    }

    private static ComboCombatService service() {
        return new ComboCombatService(configuration(0.30D, 2));
    }

    @Test
    public void fallSpeedLiveUpdateAffectsAllParticipantsAndCanRollBack() {
        ComboCombatService service = service();
        Participant human = new Participant();
        Participant npc = new Participant();
        Participant ordinary = new Participant();
        service.apply(human.player);
        service.apply(npc.player);
        ComboConfig changed = service.getConfiguration().withSetting("fall-speed", "0.05");

        ComboCombatService.LiveUpdate update = service.updateLive(changed);

        assertEquals(0.05D, service.getFallSpeed(human.player.getUniqueId()), EPSILON);
        assertEquals(0.05D, service.getFallSpeed(npc.player.getUniqueId()), EPSILON);
        assertEquals(0.0D, service.getFallSpeed(ordinary.player.getUniqueId()), EPSILON);
        assertEquals(3.0D, service.getFallHeight(human.player.getUniqueId()), EPSILON);
        assertEquals(0.3D, human.profile.getHorizontal(), EPSILON);
        assertEquals(0.1D, npc.profile.getVertical(), EPSILON);
        verify(human.player, times(1)).setNoDamageTicks(anyInt());
        verify(npc.player, times(1)).setNoDamageTicks(anyInt());

        update.rollback();

        assertEquals(0.08D, service.getFallSpeed(human.player.getUniqueId()), EPSILON);
        assertEquals(0.08D, service.getFallSpeed(npc.player.getUniqueId()), EPSILON);
        assertEquals(0.08D, service.getConfiguration().getFallSpeed(), EPSILON);
    }

    @Test
    public void failedLiveUpdateRestoresTheSpeedOfAlreadyChangedParticipants() {
        ComboCombatService service = service();
        Participant human = new Participant();
        Participant npc = new Participant();
        service.apply(human.player);
        service.apply(npc.player);
        AtomicInteger changed = new AtomicInteger();
        Consumer<KnockbackProfile> failSecondWrite = profile -> {
            if (changed.incrementAndGet() == 2) {
                throw new IllegalStateException("second participant failed");
            }
        };
        human.afterProfileWrite = failSecondWrite;
        npc.afterProfileWrite = failSecondWrite;

        try {
            service.updateLive(service.getConfiguration().withSetting("fall-speed", "0.05"));
            fail("Expected second participant failure");
        } catch (IllegalStateException expected) {
            assertEquals("second participant failed", expected.getMessage());
        }

        assertEquals(0.08D, service.getFallSpeed(human.player.getUniqueId()), EPSILON);
        assertEquals(0.08D, service.getFallSpeed(npc.player.getUniqueId()), EPSILON);
        assertEquals(0.08D, service.getConfiguration().getFallSpeed(), EPSILON);
    }

    @Test
    public void repeatedSpeedUpdatesPreservePreMatchRestorationAndFutureSpeed() {
        ComboCombatService service = service();
        Participant human = new Participant();
        service.apply(human.player);
        service.updateLive(service.getConfiguration().withSetting("fall-speed", "0.05"));
        service.updateLive(service.getConfiguration().withSetting("fall-speed", "0.12"));
        Participant future = new Participant();
        service.apply(future.player);

        assertEquals(0.12D, service.getFallSpeed(future.player.getUniqueId()), EPSILON);
        service.restore(human.player);

        assertEquals(0.0D, service.getFallSpeed(human.player.getUniqueId()), EPSILON);
        assertSame(human.originalProfile, human.profile);
        assertEquals(20, human.maximum);
        assertEquals(0.12D, service.getFallSpeed(future.player.getUniqueId()), EPSILON);
    }

    private static ComboConfig configuration(double horizontal, int noDamageTicks) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("combo.knockback.horizontal", horizontal);
        yaml.set("combo.no-damage-ticks", noDamageTicks);
        return ComboConfig.load(yaml);
    }

    private static final class Participant {
        private final Player player = mock(Player.class);
        private final KnockbackProfile originalProfile = mock(KnockbackProfile.class);
        private KnockbackProfile profile = originalProfile;
        private int maximum = 20;
        private int remaining;
        private Consumer<KnockbackProfile> afterProfileWrite;

        private Participant() {
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getKnockbackProfile()).thenAnswer(invocation -> profile);
            when(player.getMaximumNoDamageTicks()).thenAnswer(invocation -> maximum);
            when(player.getNoDamageTicks()).thenAnswer(invocation -> remaining);
            doAnswer(invocation -> {
                profile = (KnockbackProfile) invocation.getArguments()[0];
                if (afterProfileWrite != null) {
                    afterProfileWrite.accept(profile);
                }
                return null;
            }).when(player).setKnockbackProfile(any(KnockbackProfile.class));
            doAnswer(invocation -> {
                maximum = (Integer) invocation.getArguments()[0];
                return null;
            }).when(player).setMaximumNoDamageTicks(anyInt());
            doAnswer(invocation -> {
                remaining = (Integer) invocation.getArguments()[0];
                return null;
            }).when(player).setNoDamageTicks(anyInt());
        }
    }
}
