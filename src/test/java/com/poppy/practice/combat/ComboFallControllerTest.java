package com.poppy.practice.combat;

import com.poppy.practice.config.ComboConfig;
import dev.cobblesword.nachospigot.knockback.KnockbackProfile;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ComboFallControllerTest {
    private static final double EPSILON = 0.000000001D;
    private static final double FIRST_FALL = -0.08D;

    @Test
    public void qualifyingDamageArmsHeightLimitAndLeavesHorizontalKnockbackIntact() {
        Fixture fixture = new Fixture();
        fixture.damage(2.0D);
        fixture.move(66.999D, false);
        assertVelocity(fixture.velocity(0.4D, 0.3D, -0.7D), 0.4D, 0.3D, -0.7D);

        fixture.move(67.0D, false);
        assertVelocity(fixture.velocity(0.4D, 0.3D, -0.7D), 0.4D, FIRST_FALL, -0.7D);
    }

    @Test
    public void damageBeforeTakeoffSurvivesAnotherGroundedVelocitySample() {
        Fixture fixture = new Fixture();
        fixture.damage(2.0D);
        assertVelocity(fixture.velocity(0.4D, 0.3D, -0.7D), 0.4D, 0.3D, -0.7D);
        fixture.move(67.0D, false);

        assertEquals(FIRST_FALL, fixture.velocity(0.4D, 0.3D, -0.7D).getY(), EPSILON);
    }

    @Test
    public void cancelledDamageDoesNotArmHeightLimit() {
        Fixture fixture = new Fixture();
        EntityDamageByEntityEvent cancelled = fixture.damageEvent(2.0D);
        cancelled.setCancelled(true);
        fixture.controller.onDamage(cancelled);
        fixture.move(68.0D, false);

        assertEquals(0.3D, fixture.velocity(0.4D, 0.3D, -0.7D).getY(), EPSILON);
        assertTrue(cancelled.isCancelled());
    }

    @Test
    public void fullyAbsorbedZeroFinalDamageStillArmsNativeKnockback() {
        Fixture fixture = new Fixture();
        fixture.damage(0.0D);
        fixture.move(68.0D, false);

        assertEquals(FIRST_FALL, fixture.velocity(0.4D, 0.3D, -0.7D).getY(), EPSILON);
    }

    @Test
    public void nonComboParticipantIsUnaffected() {
        Fixture fixture = new Fixture();
        fixture.combat.restore(fixture.player);
        fixture.damage(2.0D);
        fixture.move(68.0D, false);
        fixture.controller.run();

        assertEquals(0.3D, fixture.velocity(0.4D, 0.3D, -0.7D).getY(), EPSILON);
    }

    @Test
    public void nonPlayerDamageTargetIsIgnored() {
        Fixture fixture = new Fixture();
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(
                fixture.attacker, mock(Entity.class), DamageCause.ENTITY_ATTACK, 2.0D);
        fixture.controller.onDamage(event);
        fixture.move(68.0D, false);

        assertEquals(0.3D, fixture.velocity(0.4D, 0.3D, -0.7D).getY(), EPSILON);
        assertEquals(2.0D, event.getFinalDamage(), EPSILON);
    }

    @Test
    public void normalJumpWithoutDamageNeverTriggersFallLimit() {
        Fixture fixture = new Fixture();
        fixture.move(70.0D, false);
        fixture.controller.run();

        assertVelocity(fixture.velocity(0.4D, 0.3D, -0.7D), 0.4D, 0.3D, -0.7D);
    }

    @Test
    public void repeatedSameTickHitsAndPacketsCannotIncreaseDownwardAcceleration() {
        Fixture fixture = falling();
        for (int index = 0; index < 100; index++) {
            fixture.damage(2.0D);
            assertEquals(FIRST_FALL, fixture.velocity(0.4D, 0.3D, -0.7D).getY(), EPSILON);
        }
    }

    @Test
    public void schedulerKeepsFallSpeedConstantOnNextTick() {
        Fixture fixture = falling();
        fixture.controller.run();

        assertEquals(FIRST_FALL,
                fixture.velocity(0.4D, 0.3D, -0.7D).getY(), EPSILON);
    }

    @Test
    public void fallingRemainsLatchedBelowThresholdUntilLanding() {
        Fixture fixture = falling();
        fixture.move(64.1D, false);
        assertEquals(FIRST_FALL, fixture.velocity(0.4D, 0.3D, -0.7D).getY(), EPSILON);

        fixture.move(64.0D, true);
        assertEquals(0.3D, fixture.velocity(0.4D, 0.3D, -0.7D).getY(), EPSILON);
        fixture.move(68.0D, false);
        assertEquals(0.3D, fixture.velocity(0.4D, 0.3D, -0.7D).getY(), EPSILON);
    }

    @Test
    public void existingFasterDownwardVelocityIsReducedToGentleDescent() {
        Fixture fixture = falling();

        assertVelocity(fixture.velocity(0.4D, -1.0D, -0.7D), 0.4D, FIRST_FALL, -0.7D);
    }

    @Test
    public void cancelledVelocityEventIsNotModified() {
        Fixture fixture = falling();
        PlayerVelocityEvent event = new PlayerVelocityEvent(fixture.player, new Vector(0.4D, 0.3D, -0.7D));
        event.setCancelled(true);
        fixture.controller.onVelocity(event);

        assertVelocity(event.getVelocity(), 0.4D, 0.3D, -0.7D);
        assertTrue(event.isCancelled());
    }

    @Test
    public void zeroHeightDisablesMainThreadFilteringImmediately() {
        Fixture fixture = falling();
        fixture.height(0.0D);

        assertEquals(0.3D, fixture.velocity(0.4D, 0.3D, -0.7D).getY(), EPSILON);
        fixture.height(3.0D);
        assertEquals(0.3D, fixture.velocity(0.4D, 0.3D, -0.7D).getY(), EPSILON);
    }

    @Test
    public void zeroHeightDisablesAlreadyPublishedAsyncLimitWithoutWaitingForTick() {
        Fixture fixture = falling();
        fixture.height(0.0D);
        fixture.mainThread.set(false);

        assertEquals(0.3D, fixture.velocity(0.4D, 0.3D, -0.7D).getY(), EPSILON);
    }

    @Test
    public void zeroHeightClearsPendingDamageArming() {
        Fixture fixture = new Fixture();
        fixture.damage(2.0D);
        fixture.height(0.0D);
        fixture.controller.run();
        fixture.height(3.0D);
        fixture.move(68.0D, false);

        assertEquals(0.3D, fixture.velocity(0.4D, 0.3D, -0.7D).getY(), EPSILON);
    }

    @Test
    public void asyncVelocityUsesCachedLimitWithoutInspectingEntityLocationOrWorld() {
        Fixture fixture = falling();
        fixture.mainThread.set(false);
        when(fixture.player.getLocation()).thenThrow(new AssertionError("Async location access"));
        when(fixture.player.isOnGround()).thenThrow(new AssertionError("Async ground access"));
        when(fixture.player.getWorld()).thenThrow(new AssertionError("Async entity world access"));
        when(fixture.world.getUID()).thenThrow(new AssertionError("Async world access"));

        assertVelocity(fixture.velocity(0.4D, 0.3D, -0.7D), 0.4D, FIRST_FALL, -0.7D);
    }

    @Test
    public void removedParticipantCannotUseStaleAsyncLimit() {
        Fixture fixture = falling();
        fixture.combat.restore(fixture.player);
        fixture.mainThread.set(false);

        assertEquals(0.3D, fixture.velocity(0.4D, 0.3D, -0.7D).getY(), EPSILON);
    }

    @Test
    public void newMatchWithSameUuidCannotUsePreviousMatchAsyncLimit() {
        Fixture fixture = falling();
        Object previousToken = fixture.combat.getParticipantToken(fixture.player.getUniqueId());
        fixture.combat.restore(fixture.player);
        fixture.combat.apply(fixture.player);
        assertNotSame(previousToken, fixture.combat.getParticipantToken(fixture.player.getUniqueId()));
        fixture.mainThread.set(false);

        assertEquals(0.3D, fixture.velocity(0.4D, 0.3D, -0.7D).getY(), EPSILON);
    }

    @Test
    public void acceptedTeleportClearsPublishedLimit() {
        Fixture fixture = falling();
        fixture.controller.onTeleport(fixture.teleportEvent());
        fixture.mainThread.set(false);

        assertEquals(0.3D, fixture.velocity(0.4D, 0.3D, -0.7D).getY(), EPSILON);
    }

    @Test
    public void cancelledTeleportPreservesPublishedLimit() {
        Fixture fixture = falling();
        PlayerTeleportEvent event = fixture.teleportEvent();
        event.setCancelled(true);
        fixture.controller.onTeleport(event);
        fixture.mainThread.set(false);

        assertEquals(FIRST_FALL, fixture.velocity(0.4D, 0.3D, -0.7D).getY(), EPSILON);
    }

    @Test
    public void quitClearsPublishedLimit() {
        Fixture fixture = falling();
        fixture.controller.onQuit(new PlayerQuitEvent(fixture.player, "quit"));
        fixture.mainThread.set(false);

        assertEquals(0.3D, fixture.velocity(0.4D, 0.3D, -0.7D).getY(), EPSILON);
    }

    @Test
    public void shutdownStopsTrackingAndAllFiltering() {
        Fixture fixture = falling();
        fixture.controller.shutdown();
        fixture.controller.run();
        fixture.damage(2.0D);
        assertEquals(0.3D, fixture.velocity(0.4D, 0.3D, -0.7D).getY(), EPSILON);
        fixture.mainThread.set(false);
        assertEquals(0.3D, fixture.velocity(0.4D, 0.3D, -0.7D).getY(), EPSILON);
    }

    @Test
    public void offlineNpcParticipantIsStillSampledByScheduler() {
        Fixture fixture = new Fixture();
        assertFalse(fixture.player.isOnline());
        fixture.damage(2.0D);
        fixture.move(67.0D, false);
        fixture.controller.run();
        fixture.mainThread.set(false);

        assertEquals(FIRST_FALL, fixture.velocity(0.4D, 0.3D, -0.7D).getY(), EPSILON);
    }

    @Test
    public void trackingDoesNotAlterDamageHealthOrKnockbackProfile() {
        Fixture fixture = new Fixture();
        EntityDamageByEntityEvent damage = fixture.damageEvent(2.0D);
        fixture.controller.onDamage(damage);
        fixture.move(67.0D, false);
        fixture.controller.run();
        fixture.velocity(0.4D, 0.3D, -0.7D);

        assertFalse(damage.isCancelled());
        assertEquals(2.0D, damage.getDamage(), EPSILON);
        assertEquals(2.0D, damage.getFinalDamage(), EPSILON);
        verify(fixture.player, never()).setHealth(anyDouble());
        verify(fixture.player, times(1)).setKnockbackProfile(any(KnockbackProfile.class));
        verify(fixture.player, times(1)).setMaximumNoDamageTicks(4);
        verify(fixture.player, times(1)).setNoDamageTicks(0);
    }

    @Test
    public void groundedKnockbackThatNeverTakesOffExpiresBeforeLaterJump() {
        Fixture fixture = new Fixture();
        fixture.damage(2.0D);
        for (int index = 0; index < 21; index++) {
            fixture.controller.run();
        }
        fixture.move(68.0D, false);

        assertEquals(0.3D, fixture.velocity(0.4D, 0.3D, -0.7D).getY(), EPSILON);
    }

    @Test
    public void onlinePlayerContinuesGentleDescentWithoutFurtherHits() {
        Fixture fixture = falling();
        when(fixture.player.isOnline()).thenReturn(true);
        for (int index = 0; index < 20; index++) {
            fixture.controller.run();
        }

        assertEquals(20, fixture.packets.size());
        for (int index = 0; index < 20; index++) {
            double drag = Math.pow(0.91D, index + 1);
            assertVelocity(fixture.packets.get(index), 0.4D * drag, -0.08D, -0.7D * drag);
        }
        verify(fixture.player, never()).getVelocity();
        verify(fixture.player, never()).setVelocity(any(Vector.class));
    }

    @Test
    public void liveFallSpeedAppliesToCachedAsyncHitAndNextTickPacket() {
        Fixture fixture = falling();
        when(fixture.player.isOnline()).thenReturn(true);
        fixture.combat.updateLive(fixture.combat.getConfiguration().withSetting("fall-speed", "0.05"));
        fixture.mainThread.set(false);
        assertVelocity(fixture.velocity(0.5D, 0.3D, -0.6D), 0.5D, -0.05D, -0.6D);

        fixture.mainThread.set(true);
        fixture.controller.run();
        assertVelocity(fixture.packets.get(0), 0.5D * 0.91D, -0.05D, -0.6D * 0.91D);
    }

    @Test
    public void newKnockbackReplacesPreviousHorizontalCorrectionImmediately() {
        Fixture fixture = falling();
        when(fixture.player.isOnline()).thenReturn(true);
        fixture.controller.run();
        fixture.velocity(-0.8D, 0.4D, 0.9D);
        fixture.controller.run();

        assertVelocity(fixture.packets.get(1), -0.8D * 0.91D, -0.08D, 0.9D * 0.91D);
    }

    @Test
    public void positionChangesSupplyClientMomentumButRotationOnlyEventsDoNotEraseIt() {
        Fixture fixture = falling();
        when(fixture.player.isOnline()).thenReturn(true);
        fixture.controller.run();
        fixture.controller.onMove(new PlayerMoveEvent(fixture.player, fixture.location.clone(),
                fixture.location.clone().add(0.2D, -0.08D, -0.3D)));
        Location rotated = fixture.location.clone();
        rotated.setYaw(90.0F);
        fixture.controller.onMove(new PlayerMoveEvent(fixture.player, fixture.location.clone(), rotated));
        fixture.controller.run();

        assertVelocity(fixture.packets.get(1), 0.2D * 0.91D * 0.91D,
                -0.08D, -0.3D * 0.91D * 0.91D);
    }

    @Test
    public void cancelledMovesAndTeleportsDoNotBecomeHorizontalMomentum() {
        Fixture fixture = falling();
        when(fixture.player.isOnline()).thenReturn(true);
        fixture.controller.run();
        PlayerMoveEvent cancelled = new PlayerMoveEvent(fixture.player, fixture.location.clone(),
                fixture.location.clone().add(100.0D, 0.0D, 100.0D));
        cancelled.setCancelled(true);
        fixture.controller.onMove(cancelled);
        fixture.controller.onMove(fixture.teleportEvent());
        fixture.controller.run();

        assertVelocity(fixture.packets.get(1), 0.4D * 0.91D * 0.91D,
                -0.08D, -0.7D * 0.91D * 0.91D);
    }

    @Test
    public void landingStopsPeriodicPacketsAndDoesNotAffectNextOrdinaryJump() {
        Fixture fixture = falling();
        when(fixture.player.isOnline()).thenReturn(true);
        fixture.controller.run();
        fixture.move(64.0D, true);
        fixture.controller.run();
        fixture.move(68.0D, false);
        fixture.controller.run();

        assertEquals(1, fixture.packets.size());
    }

    @Test
    public void disablingHeightStopsPeriodicPacketsImmediately() {
        Fixture fixture = falling();
        when(fixture.player.isOnline()).thenReturn(true);
        fixture.controller.run();
        fixture.height(0.0D);
        fixture.controller.run();

        assertEquals(1, fixture.packets.size());
        assertEquals(0.4D, fixture.controller.controlledVerticalVelocity(fixture.player, 0.4D), EPSILON);
    }

    @Test
    public void endedMatchOrNewMatchCannotContinueOldSlowFallPackets() {
        Fixture fixture = falling();
        when(fixture.player.isOnline()).thenReturn(true);
        fixture.controller.run();
        fixture.combat.restore(fixture.player);
        fixture.controller.run();
        fixture.combat.apply(fixture.player);
        fixture.controller.run();

        assertEquals(1, fixture.packets.size());
    }

    @Test
    public void teleportAndShutdownStopPeriodicPackets() {
        Fixture teleported = falling();
        when(teleported.player.isOnline()).thenReturn(true);
        teleported.controller.run();
        teleported.controller.onTeleport(teleported.teleportEvent());
        teleported.controller.run();
        assertEquals(1, teleported.packets.size());

        Fixture stopped = falling();
        when(stopped.player.isOnline()).thenReturn(true);
        stopped.controller.run();
        stopped.controller.shutdown();
        stopped.controller.run();
        assertEquals(1, stopped.packets.size());
    }

    @Test
    public void npcReceivesOnlyNativeVerticalControlWithoutOnlineClientPackets() {
        Fixture fixture = falling();
        for (int index = 0; index < 20; index++) {
            assertEquals(-0.08D, fixture.controller.controlledVerticalVelocity(
                    fixture.player, (-0.08D - 0.08D) * 0.98D), EPSILON);
            fixture.controller.run();
        }
        assertTrue(fixture.packets.isEmpty());
        fixture.move(64.0D, true);
        assertEquals(0.4D, fixture.controller.controlledVerticalVelocity(fixture.player, 0.4D), EPSILON);
    }

    private static Fixture falling() {
        Fixture fixture = new Fixture();
        fixture.damage(2.0D);
        fixture.move(67.0D, false);
        assertEquals(FIRST_FALL, fixture.velocity(0.4D, 0.3D, -0.7D).getY(), EPSILON);
        return fixture;
    }

    private static void assertVelocity(Vector velocity, double x, double y, double z) {
        assertEquals(x, velocity.getX(), EPSILON);
        assertEquals(y, velocity.getY(), EPSILON);
        assertEquals(z, velocity.getZ(), EPSILON);
    }

    private static final class Fixture {
        private final World world = mock(World.class);
        private final Player player = mock(Player.class);
        private final Player attacker = mock(Player.class);
        private final Location location = new Location(world, 0.0D, 64.0D, 0.0D);
        private final AtomicBoolean grounded = new AtomicBoolean(true);
        private final AtomicBoolean mainThread = new AtomicBoolean(true);
        private final ComboCombatService combat = new ComboCombatService(config(3.0D));
        private final List<Vector> packets = new ArrayList<Vector>();
        private final ComboSlowFallMotion motion = new ComboSlowFallMotion((player, x, y, z) -> {
            packets.add(new Vector(x, y, z));
            return true;
        });
        private final ComboFallController controller = new ComboFallController(
                combat, Logger.getAnonymousLogger(), mainThread::get, motion);

        private Fixture() {
            when(world.getUID()).thenReturn(UUID.randomUUID());
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getLocation()).thenAnswer(invocation -> location.clone());
            when(player.isOnGround()).thenAnswer(invocation -> grounded.get());
            when(player.getMaximumNoDamageTicks()).thenReturn(20);
            combat.apply(player);
            controller.run();
        }

        private void move(double y, boolean onGround) {
            location.setY(y);
            grounded.set(onGround);
        }

        private void height(double height) {
            combat.updateLive(config(height));
        }

        private EntityDamageByEntityEvent damageEvent(double amount) {
            return new EntityDamageByEntityEvent(attacker, player, DamageCause.ENTITY_ATTACK, amount);
        }

        private void damage(double amount) {
            controller.onDamage(damageEvent(amount));
        }

        private Vector velocity(double x, double y, double z) {
            PlayerVelocityEvent event = new PlayerVelocityEvent(player, new Vector(x, y, z));
            controller.onVelocity(event);
            controller.rememberVelocity(event);
            assertFalse(event.isCancelled());
            return event.getVelocity();
        }

        private PlayerTeleportEvent teleportEvent() {
            return new PlayerTeleportEvent(player, location.clone(),
                    new Location(world, 100.0D, 64.0D, 100.0D));
        }

        private static ComboConfig config(double height) {
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.set("combo.knockback.fall-height", height);
            return ComboConfig.load(configuration);
        }
    }
}
