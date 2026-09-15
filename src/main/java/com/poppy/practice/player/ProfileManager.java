package com.poppy.practice.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ProfileManager {
    private final Map<UUID, PlayerProfile> profiles = new HashMap<UUID, PlayerProfile>();

    public PlayerProfile create(UUID playerId) {
        PlayerProfile profile = new PlayerProfile(playerId);
        profiles.put(playerId, profile);
        return profile;
    }

    public PlayerProfile getOrCreate(UUID playerId) {
        PlayerProfile profile = profiles.get(playerId);
        return profile == null ? create(playerId) : profile;
    }

    public PlayerProfile get(UUID playerId) {
        return profiles.get(playerId);
    }

    public PlayerProfile remove(UUID playerId) {
        return profiles.remove(playerId);
    }

    public int size() {
        return profiles.size();
    }

    public Collection<PlayerProfile> all() {
        return new ArrayList<PlayerProfile>(profiles.values());
    }

    public void clear() {
        profiles.clear();
    }
}
