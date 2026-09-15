package com.poppy.practice.player;

import java.util.UUID;

public final class PlayerProfile {
    private final UUID playerId;
    private PlayerState state;
    private String selectedKitId;
    private String queuedKitId;
    private long enderPearlCooldownUntil;
    private long combatSessionVersion;

    public PlayerProfile(UUID playerId) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId cannot be null");
        }
        this.playerId = playerId;
        this.state = PlayerState.LOBBY;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public PlayerState getState() {
        return state;
    }

    public void setState(PlayerState state) {
        if (state == null) {
            throw new IllegalArgumentException("state cannot be null");
        }
        if (isCombatState(this.state) != isCombatState(state)) {
            combatSessionVersion++;
        }
        this.state = state;
    }

    /** Identifies one countdown/fight, including the STARTING to FIGHTING transition. */
    public long getCombatSessionVersion() {
        return combatSessionVersion;
    }

    /** Prevents delayed inventory/projectile work from leaking into a later match. */
    public boolean isSameCombatSession(long version) {
        return isCombatState(state) && combatSessionVersion == version;
    }

    private static boolean isCombatState(PlayerState state) {
        return state == PlayerState.STARTING || state == PlayerState.FIGHTING;
    }

    public String getSelectedKitId() {
        return selectedKitId;
    }

    public void setSelectedKitId(String selectedKitId) {
        this.selectedKitId = selectedKitId;
    }

    public String getQueuedKitId() {
        return queuedKitId;
    }

    public void setQueuedKitId(String queuedKitId) {
        this.queuedKitId = queuedKitId;
    }

    public void leaveQueue() {
        this.queuedKitId = null;
        if (state == PlayerState.QUEUE) {
            setState(PlayerState.LOBBY);
        }
    }

    public long getEnderPearlCooldownUntil() {
        return enderPearlCooldownUntil;
    }

    public void startEnderPearlCooldown(long currentTimeMillis, long durationMillis) {
        enderPearlCooldownUntil = currentTimeMillis + Math.max(0L, durationMillis);
    }

    public void resetEnderPearlCooldown() {
        enderPearlCooldownUntil = 0L;
    }
}
