package com.poppy.practice.bot;

import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityVelocity;
import net.minecraft.server.v1_8_R3.World;
import net.minecraft.server.v1_8_R3.WorldServer;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.PluginManager;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.InOrder;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

/** Controller wiring checks; native attack and physics are deliberately mocked. */
public class BotControllerParityTest {
    private static final double EPSILON = 0.000000001D;
    private static final double GROUND_DRAG = 0.91F * 0.6F;
    private static Field serverField;
    private static Object previousServer;

    @BeforeClass
    public static void initializeCraftEntityPermissions() throws Exception {
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        Server server = mock(Server.class);
        PluginManager plugins = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(plugins);
        when(plugins.getDefaultPermissions(anyBoolean())).thenReturn(Collections.emptySet());
        serverField.set(null, server);
    }

    @AfterClass
    public static void restoreBukkitServer() throws Exception {
        if (serverField != null) {
            serverField.set(null, previousServer);
        }
    }

    @Test
    public void landedSprintAttackSlowsLocalMomentumExactlyOnce() throws Exception {
        Fixture fixture = new Fixture(0);
        fixture.acceptDamage = true;

        fixture.controller.tick();

        verify(fixture.bot, times(1)).attack(fixture.target);
        assertEquals(1L, fixture.npc.getLandedMeleeHits());
        fixture.assertClientMotion(0.48D, -0.24D);
        fixture.assertServerMotion(0.30D * GROUND_DRAG, -0.15D * GROUND_DRAG);
    }

    @Test
    public void rejectedHurtTimeAttackStillSlowsLocalMomentumExactlyOnce() throws Exception {
        Fixture fixture = new Fixture(2);
        fixture.target.noDamageTicks = 20;

        fixture.controller.tick();

        verify(fixture.bot, times(1)).attack(fixture.target);
        assertEquals(0L, fixture.npc.getLandedMeleeHits());
        fixture.assertClientMotion(0.48D, -0.24D);
        fixture.assertServerMotion(0.50D * GROUND_DRAG, -0.25D * GROUND_DRAG);
    }

    @Test
    public void onlyLandedHitsScheduleTheConfiguredForwardRelease() throws Exception {
        Fixture fixture = new Fixture(2);
        fixture.acceptDamage = true;
        fixture.controller.tick();
        fixture.acceptDamage = false;

        fixture.controller.tick();
        fixture.controller.tick();
        fixture.controller.tick();

        assertEquals(4, fixture.forwardInputs.size());
        assertEquals(1.0F, fixture.forwardInputs.get(0), 0.0F);
        assertEquals(0.0F, fixture.forwardInputs.get(1), 0.0F);
        assertEquals(0.0F, fixture.forwardInputs.get(2), 0.0F);
        assertEquals(1.0F, fixture.forwardInputs.get(3), 0.0F);
        assertEquals(1L, fixture.npc.getLandedMeleeHits());
    }

    @Test
    public void repeatedRejectedAttacksDoNotScheduleForwardRelease() throws Exception {
        Fixture fixture = new Fixture(2);
        fixture.target.noDamageTicks = 20;

        fixture.controller.tick();
        fixture.controller.tick();

        assertEquals(1.0F, fixture.forwardInputs.get(0), 0.0F);
        assertEquals(1.0F, fixture.forwardInputs.get(1), 0.0F);
        fixture.assertClientMotion(0.288D, -0.144D);
        fixture.assertServerMotion(0.50D * GROUND_DRAG * GROUND_DRAG,
                -0.25D * GROUND_DRAG * GROUND_DRAG);
        verify(fixture.bot, times(2)).attack(fixture.target);
        verify(fixture.bot, times(2)).setExtraKnockback(true);
        verify(fixture.bot, times(2)).setExtraKnockback(false);
    }

    @Test
    public void meleeFacesCurrentEyesWithoutPredictionErrorOrTurnLimit() throws Exception {
        Fixture fixture = new Fixture(0, config -> {
            config.set("bot.aim.prediction-ticks", 5.0D);
            config.set("bot.aim.error-degrees", 15.0D);
            config.set("bot.aim.maximum-yaw-change-per-tick", 1.0D);
            config.set("bot.aim.maximum-pitch-change-per-tick", 1.0D);
        });
        fixture.target.locX = 1.0D;
        fixture.target.locY = 65.0D;
        fixture.target.locZ = 2.0D;
        fixture.bot.yaw = 120.0F;
        fixture.bot.pitch = 50.0F;
        fixture.setControllerField("hasLastTargetPosition", true);
        fixture.setControllerField("lastTargetX", -1.0D);
        fixture.setControllerField("lastTargetY", 64.0D);
        fixture.setControllerField("lastTargetZ", 1.0D);
        fixture.setControllerField("aimErrorTicks", 100);
        fixture.setControllerField("yawError", 12.0D);
        fixture.setControllerField("pitchError", 7.0D);

        fixture.controller.tick();

        fixture.assertFacingCurrentEyes();
        verify(fixture.bot).attack(fixture.target);
    }

    @Test
    public void meleeFacingContinuesBetweenClicksAndDuringTargetHurtTime() throws Exception {
        Fixture fixture = new Fixture(0);
        fixture.target.locZ = 3.8D;
        fixture.target.noDamageTicks = 20;
        fixture.bot.yaw = 180.0F;
        fixture.setControllerField("clickCooldown", 10);

        fixture.controller.tick();

        fixture.assertFacingCurrentEyes();
        verify(fixture.bot, never()).bw();
        verify(fixture.bot, never()).attack(fixture.target);
    }

    @Test
    public void meleeFacingIsRefreshedAfterMovementAndNativeBodyAnimation() throws Exception {
        Fixture fixture = new Fixture(0);
        doAnswer(invocation -> {
            fixture.bot.locX = 0.6D;
            fixture.bot.locY = 64.5D;
            fixture.bot.locZ = -0.2D;
            fixture.bot.aI = 48.0F;
            fixture.bot.aJ = -35.0F;
            fixture.bot.aK = 71.0F;
            return null;
        }).when(fixture.bot).l();

        fixture.controller.tick();

        fixture.assertFacingCurrentEyes();
        verify(fixture.bot, times(1)).l();
        verify(fixture.bot).a(0.5D, true);
    }

    @Test
    public void meleeFacingDoesNotDisableConfiguredSidewaysMovement() throws Exception {
        Fixture fixture = new Fixture(0, config -> {
            config.set("bot.movement.strafe-enabled", true);
            config.set("bot.movement.strafe-input", 0.6D);
        });
        fixture.setControllerField("strafeDirection", 1);
        fixture.setControllerField("strafeTicks", 100);

        fixture.controller.tick();

        assertEquals(0.6F, fixture.bot.aZ, 0.00001F);
        assertEquals(1.0F, fixture.forwardInputs.get(0), 0.0F);
        fixture.assertFacingCurrentEyes();
        fixture.assertClientMotion(0.48D, -0.24D);
    }

    @Test
    public void distantApproachRetainsConfiguredPredictionAndAimError() throws Exception {
        Fixture fixture = new Fixture(0, config -> {
            config.set("bot.aim.prediction-ticks", 2.0D);
            config.set("bot.aim.maximum-yaw-change-per-tick", 180.0D);
            config.set("bot.aim.maximum-pitch-change-per-tick", 180.0D);
        });
        fixture.target.locX = 2.0D;
        fixture.target.locZ = 6.0D;
        fixture.setControllerField("hasLastTargetPosition", true);
        fixture.setControllerField("lastTargetX", 1.0D);
        fixture.setControllerField("lastTargetY", 64.0D);
        fixture.setControllerField("lastTargetZ", 6.0D);
        fixture.setControllerField("aimErrorTicks", 100);
        fixture.setControllerField("yawError", 6.0D);
        fixture.setControllerField("pitchError", -2.0D);

        fixture.controller.tick();

        assertEquals(BotMovement.yawTo(4.0D, 6.0D) + 6.0F, fixture.bot.yaw, 0.00001F);
        assertEquals(-2.0F, fixture.bot.pitch, 0.00001F);
        verify(fixture.bot, never()).attack(fixture.target);
    }

    @Test
    public void obstructedTargetRetainsConfiguredTurnLimitOutsideMeleeEngagement() throws Exception {
        Fixture fixture = new Fixture(0, config ->
                config.set("bot.aim.maximum-yaw-change-per-tick", 1.0D));
        fixture.bot.yaw = 90.0F;
        when(fixture.bot.hasLineOfSight(fixture.target)).thenReturn(false);

        fixture.controller.tick();

        assertEquals(89.0F, fixture.bot.yaw, 0.00001F);
        verify(fixture.bot, never()).attack(fixture.target);
    }

    @Test
    public void healingRetreatClearsCombatFacingAndKeepsLookingAway() throws Exception {
        Fixture fixture = new Fixture(0);
        fixture.bot.yaw = -180.0F;
        fixture.bot.pitch = 8.0F;
        fixture.setControllerField("directCombatFacing", true);
        fixture.setControllerField("healingTicks", 3);
        fixture.setControllerField("healingRetreatYaw", -180.0F);
        fixture.setControllerField("healingPotionThrown", true);

        fixture.controller.tick();

        assertEquals(-180.0F, fixture.bot.yaw, 0.00001F);
        assertEquals(8.0F, fixture.bot.pitch, 0.00001F);
        assertEquals(1.0F, fixture.forwardInputs.get(0), 0.0F);
        assertFalse((Boolean) fixture.getControllerField("directCombatFacing"));
        verify(fixture.bot, never()).attack(fixture.target);
    }

    @Test
    public void pearlRecoveryDoesNotApplyPostPhysicsCombatFacing() throws Exception {
        Fixture fixture = new Fixture(0);
        fixture.setControllerField("pearlSwitchBackTicks", 2);
        doAnswer(invocation -> {
            fixture.bot.yaw = 35.0F;
            fixture.bot.pitch = 25.0F;
            return null;
        }).when(fixture.bot).l();

        fixture.controller.tick();

        assertFalse((Boolean) fixture.getControllerField("directCombatFacing"));
        assertEquals(35.0F, fixture.bot.yaw, 0.00001F);
        assertEquals(25.0F, fixture.bot.pitch, 0.00001F);
        verify(fixture.bot, never()).attack(fixture.target);
    }

    @Test
    public void sustainedSprintWithoutAnAttackDoesNotRearmExtraKnockback() throws Exception {
        Fixture fixture = new Fixture(0);
        fixture.target.locZ = 5.0D;

        fixture.controller.tick();
        fixture.controller.tick();
        fixture.controller.tick();

        verify(fixture.bot, never()).attack(fixture.target);
        verify(fixture.bot, times(1)).setExtraKnockback(true);
        verify(fixture.bot, never()).setExtraKnockback(false);
    }

    @Test
    public void externalServerCollisionImpulseDecaysWithoutChangingClientMomentum() throws Exception {
        Fixture fixture = new Fixture(0);
        fixture.target.locZ = 5.0D;
        // External collisions affect exposed server motion, not the client cache.
        fixture.bot.motX = 0.40D;
        fixture.bot.motZ = -0.20D;
        double serverX = 0.40D;
        double serverZ = -0.20D;

        for (int tick = 0; tick < 3; tick++) {
            fixture.controller.tick();
            serverX *= GROUND_DRAG;
            serverZ *= GROUND_DRAG;
            fixture.assertServerMotion(serverX, serverZ);
            fixture.assertClientMotion(0.80D, -0.40D);
            fixture.assertServerMotion(serverX, serverZ);
        }
        verify(fixture.bot, times(3)).l();
    }

    @Test
    public void airborneServerMotionUsesAirDragWithoutReadingGroundBlocks() throws Exception {
        Fixture fixture = new Fixture(0);
        fixture.target.locZ = 5.0D;
        fixture.bot.onGround = false;

        fixture.controller.tick();

        fixture.assertServerMotion(0.50D * 0.91F, -0.25D * 0.91F);
        fixture.assertClientMotion(0.80D, -0.40D);
        verify(fixture.world, never()).getType(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void swingReleasesBlockingBeforeTryingToSprintAgain() throws Exception {
        Fixture fixture = new Fixture(0);
        fixture.blocking = true;

        fixture.controller.tick();

        InOrder order = inOrder(fixture.bot);
        order.verify(fixture.bot).bV();
        order.verify(fixture.bot).setSprinting(true);
        assertEquals(1.0F, fixture.forwardInputs.get(0), 0.0F);
        fixture.assertClientMotion(0.48D, -0.24D);
    }

    @Test
    public void continuedBlockingUsesPlayerItemMovementPenaltyAndCannotSprint() throws Exception {
        Fixture fixture = new Fixture(0);
        fixture.blocking = true;
        fixture.target.locZ = 5.0D;

        fixture.controller.tick();

        assertEquals(0.2F, fixture.forwardInputs.get(0), 0.0F);
        verify(fixture.bot, never()).setSprinting(true);
        verify(fixture.bot, never()).attack(fixture.target);
        fixture.assertClientMotion(0.80D, -0.40D);
    }

    @Test
    public void weakSpacingInputDoesNotStartSprintOrApplySprintAttackSlowdown() throws Exception {
        Fixture fixture = new Fixture(0);
        fixture.target.locZ = 2.0D;

        fixture.controller.tick();

        assertEquals(0.28F, fixture.forwardInputs.get(0), 0.0F);
        verify(fixture.bot, never()).setSprinting(true);
        verify(fixture.bot).attack(fixture.target);
        fixture.assertClientMotion(0.80D, -0.40D);
    }

    @Test
    public void receivingKnockbackUsesFullForwardInputInsteadOfWeakSpacing() throws Exception {
        Fixture fixture = new Fixture(0);
        fixture.target.locZ = 2.0D;
        fixture.bot.noDamageTicks = 10;

        fixture.controller.tick();

        assertEquals(1.0F, fixture.forwardInputs.get(0), 0.0F);
        verify(fixture.bot).setSprinting(true);
        fixture.assertClientMotion(0.48D, -0.24D);
    }

    @Test
    public void ordinaryPracticeDoesNotCreateCertificationMovementState() throws Exception {
        Fixture fixture = new Fixture(0, config -> {
            config.set("bot.movement.strafe-enabled", true);
            config.set("bot.movement.strafe-input", 0.18D);
            config.set("bot.movement.retreat-distance", 2.35D);
        });

        assertNull(fixture.getControllerField("certificationMovement"));
    }

    @Test
    public void certificationKeepsOnlyWeakStrafeBetweenEngagements() throws Exception {
        Fixture fixture = new Fixture(BotSettings.certificationPreset());
        fixture.setControllerField("strafeDirection", 1);
        fixture.setControllerField("strafeTicks", 100);

        fixture.controller.tick();

        assertEquals(0.18F, fixture.bot.aZ, 0.0F);
        assertEquals(1.0F, fixture.forwardInputs.get(0), 0.0F);
        fixture.assertFacingCurrentEyes();
        fixture.assertClientMotion(0.48D, -0.24D);
        fixture.assertServerMotion(0.50D * GROUND_DRAG, -0.25D * GROUND_DRAG);
    }

    @Test
    public void certificationBacksAwayWhileKeepingItsEyesOnTheOpponent() throws Exception {
        Fixture fixture = new Fixture(BotSettings.certificationPreset());
        fixture.target.locZ = 1.8D;

        fixture.controller.tick();

        assertEquals(-0.62F, fixture.forwardInputs.get(0), 0.0F);
        assertEquals(0.0F, fixture.bot.aZ, 0.0F);
        fixture.assertFacingCurrentEyes();
        fixture.assertClientMotion(0.80D, -0.40D);
        fixture.assertServerMotion(0.50D * GROUND_DRAG, -0.25D * GROUND_DRAG);
        verify(fixture.bot, never()).setSprinting(true);
    }

    @Test
    public void certificationPredictionReadsClientMomentumNotServerKbBaseline() throws Exception {
        Fixture fixture = new Fixture(BotSettings.certificationPreset());
        fixture.target.locZ = 2.55D;
        fixture.setClientMotion(0.0D, 0.20D);
        // The bot's actual client is closing the gap, while server KB points away.
        fixture.assertServerMotion(0.50D, -0.25D);

        fixture.controller.tick();

        assertEquals(-0.62F, fixture.forwardInputs.get(0), 0.0F);
        fixture.assertClientMotion(0.0D, 0.20D);
        fixture.assertServerMotion(0.50D * GROUND_DRAG, -0.25D * GROUND_DRAG);
    }

    @Test
    public void certificationImmediatelyPursuesAnOpponentWhoLeavesItsSpacingBand() throws Exception {
        Fixture fixture = new Fixture(BotSettings.certificationPreset());
        fixture.setClientMotion(0.0D, 0.0D);
        fixture.target.locZ = 2.2D;
        fixture.controller.tick();
        fixture.target.locZ = 3.2D;

        fixture.controller.tick();

        assertEquals(-0.62F, fixture.forwardInputs.get(0), 0.0F);
        assertEquals(1.0F, fixture.forwardInputs.get(1), 0.0F);
        fixture.assertFacingCurrentEyes();
    }

    @Test
    public void certificationReceivingKbStillHoldsWAndKeepsNativeVelocityHandling() throws Exception {
        Fixture fixture = new Fixture(BotSettings.certificationPreset());
        fixture.target.locZ = 1.8D;
        fixture.bot.noDamageTicks = 10;
        fixture.bot.motY = 0.34D;

        fixture.controller.tick();

        assertEquals(1.0F, fixture.forwardInputs.get(0), 0.0F);
        fixture.assertClientMotion(0.48D, -0.24D);
        fixture.assertServerMotion(0.50D * GROUND_DRAG, -0.25D * GROUND_DRAG);
        assertEquals(0.34D, fixture.bot.motY, EPSILON);
        verify(fixture.bot).setSprinting(true);
        verify(fixture.bot, times(1)).l();
    }

    @Test
    public void certificationChasesAPlayersBackWithoutStrafingOrBackingOff() throws Exception {
        Fixture fixture = new Fixture(BotSettings.certificationPreset());
        fixture.target.locZ = 1.8D;
        fixture.target.yaw = 0.0F;

        fixture.controller.tick();

        assertEquals(1.0F, fixture.forwardInputs.get(0), 0.0F);
        assertEquals(0.0F, fixture.bot.aZ, 0.0F);
        fixture.assertFacingCurrentEyes();
    }

    @Test
    public void certificationLandedHitImmediatelyStopsStrafeAndRetainsOneTickWReset() throws Exception {
        Fixture fixture = new Fixture(BotSettings.certificationPreset());
        fixture.acceptDamage = true;
        fixture.controller.tick();
        assertEquals(0.0F, fixture.bot.aZ, 0.0F);
        fixture.assertClientMotion(0.48D, -0.24D);
        fixture.assertServerMotion(0.30D * GROUND_DRAG, -0.15D * GROUND_DRAG);
        fixture.acceptDamage = false;
        fixture.setControllerField("clickCooldown", 0);
        fixture.controller.tick();
        fixture.setControllerField("clickCooldown", 0);
        fixture.controller.tick();

        assertEquals(1.0F, fixture.forwardInputs.get(0), 0.0F);
        assertEquals(0.0F, fixture.forwardInputs.get(1), 0.0F);
        assertEquals(1.0F, fixture.forwardInputs.get(2), 0.0F);
        assertEquals(0.0F, fixture.bot.aZ, 0.0F);
        assertEquals(1L, fixture.npc.getLandedMeleeHits());
        fixture.assertClientMotion(0.288D, -0.144D);
        fixture.assertServerMotion(0.30D * GROUND_DRAG * GROUND_DRAG * GROUND_DRAG,
                -0.15D * GROUND_DRAG * GROUND_DRAG * GROUND_DRAG);
    }

    @Test
    public void certificationLandedHitHoldsSpacingAfterTheOneTickWResetHasFinished() throws Exception {
        Fixture fixture = new Fixture(BotSettings.certificationPreset());
        fixture.setClientMotion(0.0D, 0.0D);
        fixture.target.locZ = 2.55D;
        fixture.acceptDamage = true;
        fixture.controller.tick();
        fixture.acceptDamage = false;
        fixture.controller.tick();
        fixture.controller.tick();

        assertEquals(0.28F, fixture.forwardInputs.get(0), 0.0F);
        assertEquals(0.0F, fixture.forwardInputs.get(1), 0.0F);
        assertEquals(0.0F, fixture.forwardInputs.get(2), 0.0F);
        assertEquals(0.0F, fixture.bot.aZ, 0.0F);
        assertEquals(1L, fixture.npc.getLandedMeleeHits());
        fixture.assertFacingCurrentEyes();
    }

    @Test
    public void certificationRejectedAttacksDoNotActivateComboSpacingOrWReset() throws Exception {
        Fixture fixture = new Fixture(BotSettings.certificationPreset());
        fixture.target.noDamageTicks = 20;
        fixture.setControllerField("strafeDirection", 1);
        fixture.setControllerField("strafeTicks", 100);
        fixture.controller.tick();
        fixture.setControllerField("clickCooldown", 0);
        fixture.controller.tick();

        assertEquals(1.0F, fixture.forwardInputs.get(0), 0.0F);
        assertEquals(1.0F, fixture.forwardInputs.get(1), 0.0F);
        assertEquals(0.18F, fixture.bot.aZ, 0.0F);
        assertEquals(0L, fixture.npc.getLandedMeleeHits());
        fixture.assertClientMotion(0.288D, -0.144D);
        fixture.assertServerMotion(0.50D * GROUND_DRAG * GROUND_DRAG,
                -0.25D * GROUND_DRAG * GROUND_DRAG);
        verify(fixture.bot, times(2)).attack(fixture.target);
    }

    @Test
    public void certificationOutOfReachSwingsCannotActivateComboSpacing() throws Exception {
        Fixture fixture = new Fixture(BotSettings.certificationPreset());
        fixture.target.locZ = 3.5D;
        fixture.acceptDamage = true;
        fixture.setControllerField("strafeDirection", 1);
        fixture.setControllerField("strafeTicks", 100);

        fixture.controller.tick();

        assertEquals(0.18F, fixture.bot.aZ, 0.0F);
        assertEquals(0L, fixture.npc.getLandedMeleeHits());
        verify(fixture.bot).bw();
        verify(fixture.bot, never()).attack(fixture.target);
    }

    @Test
    public void certificationHealingSuspendsComboSpacingAndPreservesAwayFacing() throws Exception {
        Fixture fixture = new Fixture(BotSettings.certificationPreset());
        BotCertificationMovement movement = fixture.certificationMovement();
        movement.recordLandedHit();
        movement.forwardInput(2.2D, 0.0D, false, false);
        fixture.bot.yaw = -180.0F;
        fixture.bot.pitch = 8.0F;
        fixture.setControllerField("healingTicks", 3);
        fixture.setControllerField("healingRetreatYaw", -180.0F);
        fixture.setControllerField("healingPotionThrown", true);

        fixture.controller.tick();

        assertEquals(-180.0F, fixture.bot.yaw, 0.00001F);
        assertEquals(1.0F, fixture.forwardInputs.get(0), 0.0F);
        assertEquals(0.0F, fixture.bot.aZ, 0.0F);
        assertEquals(0.28F, movement.forwardInput(2.4D, 0.0D, false, false), 0.0F);
        assertEquals(0.18F, movement.strafeInput(0.18D, 1, false, 0.28F), 0.0F);
        assertFalse((Boolean) fixture.getControllerField("directCombatFacing"));
        verify(fixture.bot, never()).attack(fixture.target);
    }

    @Test
    public void certificationPearlRecoveryClearsOldComboAndBackwardInputs() throws Exception {
        Fixture fixture = new Fixture(BotSettings.certificationPreset());
        BotCertificationMovement movement = fixture.certificationMovement();
        movement.recordLandedHit();
        movement.forwardInput(2.2D, 0.0D, false, false);
        fixture.setControllerField("pearlSwitchBackTicks", 2);

        fixture.controller.tick();

        assertEquals(0.28F, movement.forwardInput(2.4D, 0.0D, false, false), 0.0F);
        assertEquals(0.18F, movement.strafeInput(0.18D, 1, false, 0.28F), 0.0F);
        assertFalse((Boolean) fixture.getControllerField("directCombatFacing"));
        verify(fixture.bot, never()).attack(fixture.target);
    }

    @Test
    public void eachControllerTickCallsNativePhysicsAndReportsItsFallOnce() throws Exception {
        Fixture fixture = new Fixture(0);

        fixture.controller.tick();

        InOrder order = inOrder(fixture.bot);
        order.verify(fixture.bot).l();
        order.verify(fixture.bot).a(-0.125D, true);
        verify(fixture.bot, times(1)).l();
        verify(fixture.bot, times(1)).a(anyDouble(), anyBoolean());
    }

    @Test
    public void failingNativePhysicsStillRestoresTheServerMotionBaseline() throws Exception {
        Fixture fixture = new Fixture(0);
        RuntimeException failure = new RuntimeException("native tick failed");
        doThrow(failure).when(fixture.bot).l();

        try {
            fixture.controller.tick();
            fail("Expected native tick failure");
        } catch (RuntimeException actual) {
            assertSame(failure, actual);
        }

        fixture.assertServerMotion(0.50D, -0.25D);
        fixture.assertClientMotion(0.48D, -0.24D);
        verify(fixture.bot, never()).a(anyDouble(), anyBoolean());
    }

    @Test
    public void controlledFallContinuesAtGentleSpeedWithoutAnyMoreHits() throws Exception {
        Fixture fixture = new Fixture(0);
        fixture.target.locZ = 5.0D;
        fixture.bot.onGround = false;
        fixture.bot.motY = -0.5D;
        fixture.npc.setVerticalVelocityController(vertical -> -0.12D);
        doAnswer(invocation -> {
            assertEquals(-0.12D, fixture.bot.motY, EPSILON);
            fixture.bot.locY += fixture.bot.motY;
            fixture.bot.motY = (fixture.bot.motY - 0.08D) * 0.98D;
            return null;
        }).when(fixture.bot).l();

        for (int tick = 0; tick < 20; tick++) {
            fixture.controller.tick();
            assertEquals(-0.12D, fixture.bot.motY, EPSILON);
            fixture.assertClientMotion(0.80D, -0.40D);
            assertFalse(fixture.bot.velocityChanged);
        }

        assertEquals(64.0D - 20.0D * 0.12D, fixture.bot.locY, EPSILON);
        verify(fixture.bot, never()).attack(fixture.target);
    }

    @Test
    public void queuedUpwardKnockbackIsControlledAfterPacketConsumptionWithoutChangingHorizontalKb()
            throws Exception {
        Fixture fixture = new Fixture(0);
        fixture.target.locZ = 5.0D;
        fixture.bot.onGround = false;
        fixture.npc.setVerticalVelocityController(vertical -> -0.12D);
        fixture.network.handle(new PacketPlayOutEntityVelocity(fixture.bot.getId(), 0.61D, 0.45D, -0.2D));
        doAnswer(invocation -> {
            assertEquals(-0.12D, fixture.bot.motY, EPSILON);
            assertEquals(0.61D, fixture.bot.motX, EPSILON);
            assertEquals(-0.2D, fixture.bot.motZ, EPSILON);
            return null;
        }).when(fixture.bot).l();

        fixture.controller.tick();

        fixture.assertClientMotion(0.61D, -0.2D);
        fixture.assertServerMotion(0.50D * 0.91F, -0.25D * 0.91F);
        assertEquals(-0.12D, fixture.bot.motY, EPSILON);
        assertFalse(fixture.bot.velocityChanged);
    }

    @Test
    public void nativeAttackMotionCannotOverwriteVerticalControlBeforePhysics() throws Exception {
        Fixture fixture = new Fixture(0);
        fixture.bot.onGround = false;
        fixture.npc.setVerticalVelocityController(vertical -> -0.12D);
        doAnswer(invocation -> {
            fixture.bot.motY = 0.4D;
            return null;
        }).when(fixture.bot).attack(fixture.target);
        doAnswer(invocation -> {
            assertEquals(-0.12D, fixture.bot.motY, EPSILON);
            return null;
        }).when(fixture.bot).l();

        fixture.controller.tick();

        verify(fixture.bot).attack(fixture.target);
        assertEquals(-0.12D, fixture.bot.motY, EPSILON);
    }

    @Test
    public void ordinaryBotWithoutControllerStillUsesNativeGravity() throws Exception {
        Fixture fixture = new Fixture(0);
        fixture.target.locZ = 5.0D;
        fixture.bot.onGround = false;
        fixture.bot.motY = -0.3D;
        doAnswer(invocation -> {
            fixture.bot.locY += fixture.bot.motY;
            fixture.bot.motY = (fixture.bot.motY - 0.08D) * 0.98D;
            return null;
        }).when(fixture.bot).l();

        fixture.controller.tick();

        assertEquals(63.7D, fixture.bot.locY, EPSILON);
        assertEquals((-0.3D - 0.08D) * 0.98D, fixture.bot.motY, EPSILON);
    }

    @Test
    public void inactiveControllerLeavesOrdinaryUpwardMotionUnchanged() throws Exception {
        Fixture fixture = new Fixture(0);
        fixture.target.locZ = 5.0D;
        fixture.bot.onGround = false;
        fixture.bot.motY = 0.34D;
        fixture.npc.setVerticalVelocityController(vertical -> vertical);

        fixture.controller.tick();

        assertEquals(0.34D, fixture.bot.motY, EPSILON);
        fixture.assertClientMotion(0.80D, -0.40D);
    }

    @Test
    public void landingDuringNativePhysicsReleasesControlledFallImmediately() throws Exception {
        Fixture fixture = new Fixture(0);
        fixture.target.locZ = 5.0D;
        fixture.bot.onGround = false;
        fixture.npc.setVerticalVelocityController(vertical -> fixture.bot.onGround ? vertical : -0.12D);
        doAnswer(invocation -> {
            assertEquals(-0.12D, fixture.bot.motY, EPSILON);
            fixture.bot.onGround = true;
            fixture.bot.motY = -0.0784D;
            return null;
        }).when(fixture.bot).l();

        fixture.controller.tick();

        assertEquals(-0.0784D, fixture.bot.motY, EPSILON);
    }

    @Test
    public void removingNpcDetachesVerticalControllerEvenIfAlreadyNotSpawned() throws Exception {
        Fixture fixture = new Fixture(0);
        fixture.npc.setVerticalVelocityController(vertical -> -0.12D);
        Field spawned = BotNpc.class.getDeclaredField("spawned");
        spawned.setAccessible(true);
        spawned.setBoolean(fixture.npc, false);
        fixture.npc.remove(null);
        fixture.bot.motY = 0.34D;

        fixture.npc.applyVerticalVelocityControl();

        assertEquals(0.34D, fixture.bot.motY, EPSILON);
    }

    @Test
    public void invalidControllerResultCannotCorruptNativeMotion() throws Exception {
        Fixture fixture = new Fixture(0);
        fixture.bot.motY = 0.34D;
        fixture.npc.setVerticalVelocityController(vertical -> Double.NaN);
        fixture.npc.applyVerticalVelocityControl();
        fixture.npc.setVerticalVelocityController(vertical -> Double.NEGATIVE_INFINITY);
        fixture.npc.applyVerticalVelocityControl();

        assertEquals(0.34D, fixture.bot.motY, EPSILON);
        assertEquals(0.50D, fixture.bot.motX, EPSILON);
        assertEquals(-0.25D, fixture.bot.motZ, EPSILON);
        assertFalse(fixture.bot.velocityChanged);
    }

    private static final class Fixture {
        private final EntityPlayer bot = mock(EntityPlayer.class);
        private final EntityPlayer target = mock(EntityPlayer.class);
        private final CraftPlayer bukkitBot = mock(CraftPlayer.class);
        private final CraftPlayer bukkitTarget = mock(CraftPlayer.class);
        private final World world = mock(World.class);
        private final BotNetworkManager network = new BotNetworkManager();
        private final BotNpc npc;
        private final BotController controller;
        private final List<Float> forwardInputs = new ArrayList<Float>();
        private boolean acceptDamage;
        private boolean nativeSprinting;
        private boolean blocking;

        private Fixture(int resetTicks) throws Exception {
            this(resetTicks, config -> { });
        }

        private Fixture(int resetTicks, Consumer<YamlConfiguration> customize) throws Exception {
            this(practiceSettings(resetTicks, customize));
        }

        private Fixture(BotSettings settings) throws Exception {
            IBlockData ground = mock(IBlockData.class);
            Block block = mock(Block.class);
            block.frictionFactor = 0.6F;
            when(ground.getBlock()).thenReturn(block);
            when(world.getType(anyInt(), anyInt(), anyInt())).thenReturn(ground);
            bot.world = world;
            when(bot.getBukkitEntity()).thenReturn(bukkitBot);
            when(bukkitTarget.getHandle()).thenReturn(target);
            when(bukkitBot.getFoodLevel()).thenReturn(20);
            when(bukkitBot.getActivePotionEffects()).thenReturn(Collections.singletonList(
                    new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1)));
            when(bukkitBot.getHealth()).thenReturn(20.0D);
            when(bot.getHeadHeight()).thenReturn(1.62F);
            when(target.getHeadHeight()).thenReturn(1.62F);
            when(bot.hasLineOfSight(target)).thenReturn(true);
            when(bot.isSprinting()).thenAnswer(invocation -> nativeSprinting);
            when(bot.isBlocking()).thenAnswer(invocation -> blocking);
            doAnswer(invocation -> {
                blocking = false;
                return null;
            }).when(bot).bV();
            doAnswer(invocation -> {
                nativeSprinting = (Boolean) invocation.getArguments()[0];
                return null;
            }).when(bot).setSprinting(anyBoolean());
            bot.valid = true;
            bot.onGround = true;
            bot.locY = 64.0D;
            target.locY = 64.0D;
            target.locZ = 2.8D;
            target.yaw = 180.0F;
            target.onGround = true;

            network.bind(bot);
            Constructor<BotNpc> constructor = BotNpc.class.getDeclaredConstructor(
                    EntityPlayer.class, WorldServer.class, BotNetworkManager.class);
            constructor.setAccessible(true);
            npc = constructor.newInstance(bot, null, network);
            Field spawned = BotNpc.class.getDeclaredField("spawned");
            spawned.setAccessible(true);
            spawned.setBoolean(npc, true);

            bot.motX = 0.50D;
            bot.motZ = -0.25D;
            npc.runClientTick(() -> {
                bot.motX = 0.80D;
                bot.motZ = -0.40D;
            });
            doAnswer(invocation -> {
                if (acceptDamage) {
                    npc.recordMeleeHitLanded();
                    if (nativeSprinting) {
                        bot.motX *= 0.6D;
                        bot.motZ *= 0.6D;
                        bot.setSprinting(false);
                    }
                }
                return null;
            }).when(bot).attack(target);
            doAnswer(invocation -> {
                forwardInputs.add(bot.ba);
                bot.locY -= 0.125D;
                return null;
            }).when(bot).l();

            controller = new BotController(npc, bukkitTarget, settings, null, false);
        }

        private static BotSettings practiceSettings(int resetTicks,
                                                    Consumer<YamlConfiguration> customize) {
            YamlConfiguration config = new YamlConfiguration();
            config.set("bot.movement.strafe-enabled", false);
            config.set("bot.aim.error-degrees", 0.0D);
            config.set("bot.aim.prediction-ticks", 0.0D);
            config.set("bot.combat.minimum-cps", 20.0D);
            config.set("bot.combat.maximum-cps", 20.0D);
            config.set("bot.combat.sprint-reset-ticks", resetTicks);
            customize.accept(config);
            return BotSettings.load(config);
        }

        private void setClientMotion(double x, double z) {
            npc.runClientTick(() -> {
                bot.motX = x;
                bot.motZ = z;
            });
        }

        private BotCertificationMovement certificationMovement() throws Exception {
            return (BotCertificationMovement) getControllerField("certificationMovement");
        }

        private void setControllerField(String name, Object value) throws Exception {
            Field field = BotController.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(controller, value);
        }

        private Object getControllerField(String name) throws Exception {
            Field field = BotController.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(controller);
        }

        private void assertFacingCurrentEyes() {
            double deltaX = target.locX - bot.locX;
            double deltaZ = target.locZ - bot.locZ;
            double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            float expectedYaw = BotMovement.yawTo(deltaX, deltaZ);
            float expectedPitch = BotMovement.pitchTo(target.locY + target.getHeadHeight()
                    - bot.locY - bot.getHeadHeight(), horizontal);
            assertEquals(expectedYaw, bot.yaw, 0.00001F);
            assertEquals(expectedPitch, bot.pitch, 0.00001F);
            assertEquals(expectedYaw, bot.aK, 0.00001F);
            assertEquals(expectedYaw, bot.aI, 0.00001F);
            assertEquals(expectedYaw, bot.aJ, 0.00001F);
        }

        private void assertClientMotion(double x, double z) {
            npc.runClientTick(() -> {
                assertEquals(x, bot.motX, EPSILON);
                assertEquals(z, bot.motZ, EPSILON);
            });
        }

        private void assertServerMotion(double x, double z) {
            assertEquals(x, bot.motX, EPSILON);
            assertEquals(z, bot.motZ, EPSILON);
        }
    }
}
