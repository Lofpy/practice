package com.poppy.practice.chatter;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class PlayerStateRegistry {
    private final ConcurrentMap<UUID, PlayerCombatState> states =
            new ConcurrentHashMap<UUID, PlayerCombatState>();

    PlayerCombatState getOrCreate(UUID playerId, int entityId) {
        PlayerCombatState existing = states.get(playerId);
        if (existing != null) {
            existing.entityId = entityId;
            return existing;
        }
        PlayerCombatState created = new PlayerCombatState(playerId, entityId);
        PlayerCombatState raced = states.putIfAbsent(playerId, created);
        return raced == null ? created : raced;
    }

    PlayerCombatState get(UUID playerId) {
        return states.get(playerId);
    }

    void remove(UUID playerId) {
        states.remove(playerId);
    }

    Collection<PlayerCombatState> all() {
        return states.values();
    }

    void resetAll() {
        for (PlayerCombatState state : states.values()) {
            synchronized (state) {
                state.resetCombat();
            }
        }
    }

    void clear() {
        states.clear();
    }
}
