package com.poppy.practice.arena;

import org.bukkit.Location;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class Arena {
    private final String id;
    private final Set<String> kitIds;
    private final Location firstSpawn;
    private final Location secondSpawn;
    private ArenaState state;

    public Arena(String id, String kitId, Location firstSpawn, Location secondSpawn, ArenaState state) {
        this(id, Collections.singletonList(kitId), firstSpawn, secondSpawn, state);
    }

    public Arena(String id, Collection<String> kitIds, Location firstSpawn,
                 Location secondSpawn, ArenaState state) {
        this.id = id;
        LinkedHashSet<String> normalized = new LinkedHashSet<String>();
        for (String kitId : kitIds) {
            if (kitId != null && !kitId.trim().isEmpty()) {
                normalized.add(kitId.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("An arena must support at least one kit");
        }
        this.kitIds = Collections.unmodifiableSet(normalized);
        this.firstSpawn = firstSpawn.clone();
        this.secondSpawn = secondSpawn.clone();
        this.state = state;
    }

    public String getId() {
        return id;
    }

    public String getKitId() {
        return kitIds.iterator().next();
    }

    public Set<String> getKitIds() {
        return kitIds;
    }

    public boolean supportsKit(String kitId) {
        return kitId != null && kitIds.contains(kitId.trim().toLowerCase(Locale.ROOT));
    }

    public Location getFirstSpawn() {
        return firstSpawn.clone();
    }

    public Location getSecondSpawn() {
        return secondSpawn.clone();
    }

    public ArenaState getState() {
        return state;
    }

    public void setState(ArenaState state) {
        this.state = state;
    }
}
