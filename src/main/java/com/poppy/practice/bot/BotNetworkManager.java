package com.poppy.practice.bot;

import net.minecraft.server.v1_8_R3.EnumProtocolDirection;
import net.minecraft.server.v1_8_R3.IChatBaseComponent;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.NetworkManager;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityVelocity;

import java.lang.reflect.Field;

/** A connection sink for an EntityPlayer that has no Minecraft client. */
final class BotNetworkManager extends NetworkManager {
    private static final Field ENTITY_ID = field("a");
    private static final Field MOTION_X = field("b");
    private static final Field MOTION_Y = field("c");
    private static final Field MOTION_Z = field("d");
    private EntityPlayer player;
    private double pendingX;
    private double pendingY;
    private double pendingZ;
    private boolean hasPendingVelocity;
    private final BotHorizontalMotion horizontalMotion = new BotHorizontalMotion(
            new BotHorizontalMotion.MotionAccess() {
                @Override
                public double getX() { return player.motX; }

                @Override
                public double getZ() { return player.motZ; }

                @Override
                public void setXZ(double x, double z) {
                    player.motX = x;
                    player.motZ = z;
                }
            });

    BotNetworkManager() {
        super(EnumProtocolDirection.SERVERBOUND);
    }

    @Override
    public void handle(Packet packet) {
        if (!(packet instanceof PacketPlayOutEntityVelocity) || player == null) {
            return;
        }
        try {
            PacketPlayOutEntityVelocity velocity = (PacketPlayOutEntityVelocity) packet;
            if (ENTITY_ID.getInt(velocity) != player.getId()) {
                return;
            }
            // A real client applies this packet locally. Store it until the NMS attack
            // method has restored its pre-hit motion, then apply it on the next AI tick.
            pendingX = MOTION_X.getInt(velocity) / 8000.0D;
            pendingY = MOTION_Y.getInt(velocity) / 8000.0D;
            pendingZ = MOTION_Z.getInt(velocity) / 8000.0D;
            hasPendingVelocity = true;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Could not read WindSpigot velocity packet", exception);
        }
    }

    void bind(EntityPlayer player) {
        this.player = player;
    }

    void runClientTick(Runnable tick) {
        horizontalMotion.runClientTick(tick);
    }

    void runServerAttack(Runnable attack) {
        horizontalMotion.runServerAttack(attack);
    }

    void applyClientAttackSlowdown() {
        horizontalMotion.applyClientAttackSlowdown();
    }

    void advanceServerMotion(double horizontalDrag) {
        horizontalMotion.advanceServerMotion(horizontalDrag);
    }

    void applyPendingVelocity() {
        if (!hasPendingVelocity || player == null) {
            return;
        }
        hasPendingVelocity = false;
        player.motX = pendingX;
        player.motY = pendingY;
        player.motZ = pendingZ;
        // Receiving a velocity packet is a client-side assignment, not a new
        // server impulse. Re-broadcasting it would feed it back through the
        // fake connection (or broadcast the separate server motion).
    }

    void discardPendingVelocity() {
        hasPendingVelocity = false;
        pendingX = 0.0D;
        pendingY = 0.0D;
        pendingZ = 0.0D;
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public void close(IChatBaseComponent reason) {
    }

    @Override
    public void disableAutomaticFlush() {
    }

    @Override
    public void enableAutomaticFlush() {
    }

    private static Field field(String name) {
        try {
            Field field = PacketPlayOutEntityVelocity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
