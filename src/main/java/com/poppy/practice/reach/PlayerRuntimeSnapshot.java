package com.poppy.practice.reach;

import java.util.UUID;

public final class PlayerRuntimeSnapshot {
    private final int entityId;
    private final UUID worldId;
    private final double x;
    private final double y;
    private final double z;
    private final boolean alive;
    private final boolean creativeOrSpectator;
    private final boolean inVehicle;
    private final boolean bypass;
    private final int ping;
    private final int serverTick;

    public PlayerRuntimeSnapshot(int entityId, UUID worldId, double x, double y, double z,
                                 boolean alive, boolean creativeOrSpectator,
                                 boolean inVehicle, boolean bypass, int ping, int serverTick) {
        this.entityId = entityId;
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.alive = alive;
        this.creativeOrSpectator = creativeOrSpectator;
        this.inVehicle = inVehicle;
        this.bypass = bypass;
        this.ping = Math.max(0, ping);
        this.serverTick = serverTick;
    }

    public int getEntityId() { return entityId; }
    public UUID getWorldId() { return worldId; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public boolean isAlive() { return alive; }
    public boolean isCreativeOrSpectator() { return creativeOrSpectator; }
    public boolean isInVehicle() { return inVehicle; }
    public boolean hasBypass() { return bypass; }
    public int getPing() { return ping; }
    public int getServerTick() { return serverTick; }
}
