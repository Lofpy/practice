package com.poppy.practice.reach;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class ViewerEntityTracker {
    private final LinkedHashMap<Integer, TrackedEntityState> entities =
            new LinkedHashMap<Integer, TrackedEntityState>(16, 0.75F, true);
    private final LinkedHashMap<Integer, UUID> destroyedEntities =
            new LinkedHashMap<Integer, UUID>(16, 0.75F, true);
    private final LinkedHashMap<Integer, UUID> evictedEntities =
            new LinkedHashMap<Integer, UUID>(16, 0.75F, true);
    private int maximumTargets;

    ViewerEntityTracker(int maximumTargets) {
        this.maximumTargets = Math.max(4, maximumTargets);
    }

    void setMaximumTargets(int maximumTargets) {
        this.maximumTargets = Math.max(4, maximumTargets);
        trim();
    }

    TrackedEntityState spawn(int entityId, UUID uuid, double x, double y, double z,
                             int serverTick) {
        TrackedEntityState state = new TrackedEntityState(entityId, uuid, x, y, z,
                serverTick);
        destroyedEntities.remove(entityId);
        evictedEntities.remove(entityId);
        entities.put(entityId, state);
        trim();
        return state;
    }

    TrackedEntityState seed(int entityId, UUID uuid, double x, double y, double z,
                            int serverTick) {
        TrackedEntityState state = new TrackedEntityState(entityId, uuid,
                x, y, z, serverTick, true);
        destroyedEntities.remove(entityId);
        evictedEntities.remove(entityId);
        entities.put(entityId, state);
        trim();
        return state;
    }

    TrackedEntityState get(int entityId) {
        return entities.get(entityId);
    }

    void destroy(int entityId) {
        TrackedEntityState state = entities.remove(entityId);
        UUID evictedUuid = evictedEntities.remove(entityId);
        if (state != null || evictedUuid != null) {
            UUID uuid = state == null ? evictedUuid : state.getEntityUuid();
            if (state != null) {
                state.invalidate();
            }
            destroyedEntities.put(entityId, uuid);
            trim();
        }
    }

    void clear() {
        entities.clear();
        destroyedEntities.clear();
        evictedEntities.clear();
    }

    void confirmThrough(long sequence, long now) {
        for (TrackedEntityState state : entities.values()) {
            state.confirmThrough(sequence, now);
        }
    }

    int size() {
        return entities.size();
    }

    UUID destroyedUuid(int entityId) {
        return destroyedEntities.get(entityId);
    }

    UUID evictedUuid(int entityId) {
        return evictedEntities.get(entityId);
    }

    private void trim() {
        while (entities.size() > maximumTargets) {
            Map.Entry<Integer, TrackedEntityState> eldest =
                    entities.entrySet().iterator().next();
            entities.remove(eldest.getKey());
            evictedEntities.put(eldest.getKey(), eldest.getValue().getEntityUuid());
        }
        while (destroyedEntities.size() > maximumTargets) {
            Integer eldest = destroyedEntities.keySet().iterator().next();
            destroyedEntities.remove(eldest);
        }
        while (evictedEntities.size() > maximumTargets) {
            Integer eldest = evictedEntities.keySet().iterator().next();
            evictedEntities.remove(eldest);
        }
    }
}
