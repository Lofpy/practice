package com.poppy.practice.bot;

import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityVelocity;
import net.minecraft.server.v1_8_R3.PacketPlayOutKeepAlive;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class BotNetworkManagerTest {
    private static final int BOT_ENTITY_ID = 73;
    private static final double EPSILON = 0.000000001D;

    @Test
    public void receivedPacketBecomesCachedClientMotionAndLeavesServerHorizontalUnchanged() {
        Fixture fixture = new Fixture();
        fixture.velocity(0.61D, 0.34D, -0.2D);
        assertMotion(fixture.player, 0.0D, 0.0D, 0.0D);

        fixture.network.runClientTick(() -> {
            fixture.network.applyPendingVelocity();
            assertMotion(fixture.player, 0.61D, 0.34D, -0.2D);
            fixture.player.motX *= 0.91D;
            fixture.player.motZ *= 0.91D;
        });
        assertMotion(fixture.player, 0.0D, 0.34D, 0.0D);
        fixture.network.runClientTick(() -> {
            fixture.network.applyPendingVelocity();
            assertMotion(fixture.player, 0.61D * 0.91D, 0.34D, -0.2D * 0.91D);
        });
        assertMotion(fixture.player, 0.0D, 0.34D, 0.0D);
    }

    @Test
    public void consecutiveKnockbackHitsDoNotFeedPreviousClientMomentumIntoNextHit() {
        Fixture fixture = new Fixture();

        for (int hit = 0; hit < 12; hit++) {
            double nextKnockbackX = fixture.player.motX / 2.0D + 0.28D + 0.33D;
            fixture.velocity(nextKnockbackX, 0.34D, 0.0D);
            fixture.network.runClientTick(() -> {
                fixture.network.applyPendingVelocity();
                assertMotion(fixture.player, 0.61D, 0.34D, 0.0D);
                fixture.player.motX *= 0.91D;
            });
            assertEquals(0.0D, fixture.player.motX, EPSILON);
        }
    }

    @Test
    public void consumingVelocityPacketDoesNotRebroadcastItAsANewImpulse() {
        Fixture fixture = new Fixture();
        fixture.velocity(0.4D, 0.34D, 0.0D);

        assertFalse(fixture.player.velocityChanged);
        fixture.network.runClientTick(() -> {
            fixture.network.applyPendingVelocity();
            assertFalse(fixture.player.velocityChanged);
        });
        assertFalse(fixture.player.velocityChanged);
    }

    @Test
    public void consumingVelocityDoesNotClearASeparatePendingServerImpulseFlag() {
        Fixture fixture = new Fixture();
        fixture.player.velocityChanged = true;
        fixture.velocity(0.4D, 0.34D, 0.0D);

        fixture.network.runClientTick(fixture.network::applyPendingVelocity);

        assertTrue(fixture.player.velocityChanged);
    }

    @Test
    public void anotherEntityVelocityAndUnrelatedPacketsCannotOverwriteOwnPendingVelocity() {
        Fixture fixture = new Fixture();
        fixture.velocity(0.4D, 0.34D, -0.1D);
        fixture.network.handle(new PacketPlayOutEntityVelocity(BOT_ENTITY_ID + 1,
                2.0D, 2.0D, 2.0D));
        fixture.network.handle(new PacketPlayOutKeepAlive(123));

        fixture.network.runClientTick(() -> {
            fixture.network.applyPendingVelocity();
            assertMotion(fixture.player, 0.4D, 0.34D, -0.1D);
        });
    }

    @Test
    public void lastOwnVelocityPacketWinsBeforeNextClientTick() {
        Fixture fixture = new Fixture();
        fixture.velocity(0.61D, 0.34D, 0.0D);
        fixture.velocity(-0.25D, 0.36D, 0.125D);

        fixture.network.runClientTick(() -> {
            fixture.network.applyPendingVelocity();
            assertMotion(fixture.player, -0.25D, 0.36D, 0.125D);
        });
        assertMotion(fixture.player, 0.0D, 0.36D, 0.0D);
    }

    @Test
    public void nativeAndLocalAttackSlowdownUseSeparateHorizontalMomentum() {
        Fixture fixture = new Fixture();
        fixture.player.motX = 0.2D;
        fixture.player.motZ = -0.1D;
        fixture.velocity(0.5D, 0.34D, -0.4D);

        fixture.network.runClientTick(() -> {
            fixture.network.applyPendingVelocity();
            fixture.network.runServerAttack(() -> {
                assertMotion(fixture.player, 0.2D, 0.34D, -0.1D);
                fixture.player.motX *= 0.6D;
                fixture.player.motZ *= 0.6D;
            });
            assertMotion(fixture.player, 0.5D, 0.34D, -0.4D);
            fixture.network.applyClientAttackSlowdown();
            assertMotion(fixture.player, 0.3D, 0.34D, -0.24D);
            fixture.player.motY = -0.1D;
        });
        assertMotion(fixture.player, 0.12D, -0.1D, -0.06D);
        fixture.network.runClientTick(() -> assertMotion(fixture.player, 0.3D, -0.1D, -0.24D));
    }

    @Test
    public void rejectedNativeAttackStillReducesOnlyClientHorizontalMomentum() {
        Fixture fixture = new Fixture();
        fixture.velocity(0.5D, 0.34D, -0.4D);

        fixture.network.runClientTick(() -> {
            fixture.network.applyPendingVelocity();
            fixture.network.runServerAttack(() -> assertMotion(fixture.player, 0.0D, 0.34D, 0.0D));
            fixture.network.applyClientAttackSlowdown();
            assertMotion(fixture.player, 0.3D, 0.34D, -0.24D);
        });
        assertMotion(fixture.player, 0.0D, 0.34D, 0.0D);
    }

    @Test
    public void discardingPendingPacketPreservesPreviouslySimulatedClientMomentum() {
        Fixture fixture = new Fixture();
        fixture.velocity(0.4D, 0.34D, -0.1D);
        fixture.network.runClientTick(fixture.network::applyPendingVelocity);
        fixture.velocity(1.0D, 1.0D, 1.0D);
        fixture.network.discardPendingVelocity();

        fixture.network.runClientTick(() -> {
            fixture.network.applyPendingVelocity();
            assertMotion(fixture.player, 0.4D, 0.34D, -0.1D);
        });
    }

    @Test
    public void velocityBeforeBindingCannotLeakIntoTheFirstClientTick() {
        EntityPlayer player = Mockito.mock(EntityPlayer.class);
        when(player.getId()).thenReturn(BOT_ENTITY_ID);
        BotNetworkManager network = new BotNetworkManager();
        network.handle(new PacketPlayOutEntityVelocity(BOT_ENTITY_ID, 1.0D, 1.0D, 1.0D));
        network.bind(player);

        network.runClientTick(() -> {
            network.applyPendingVelocity();
            assertMotion(player, 0.0D, 0.0D, 0.0D);
        });
    }

    private static void assertMotion(EntityPlayer player, double x, double y, double z) {
        assertEquals(x, player.motX, EPSILON);
        assertEquals(y, player.motY, EPSILON);
        assertEquals(z, player.motZ, EPSILON);
    }

    private static final class Fixture {
        private final EntityPlayer player = Mockito.mock(EntityPlayer.class);
        private final BotNetworkManager network = new BotNetworkManager();

        private Fixture() {
            when(player.getId()).thenReturn(BOT_ENTITY_ID);
            network.bind(player);
        }

        private void velocity(double x, double y, double z) {
            network.handle(new PacketPlayOutEntityVelocity(BOT_ENTITY_ID, x, y, z));
        }
    }
}
