package com.poppy.practice.arena;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Keeps running matches reserved while configuration changes wait for release. */
final class ArenaRegistry {
    private final Map<String, Arena> arenas = new LinkedHashMap<String, Arena>();
    private final Map<String, Arena> pendingDefinitions = new LinkedHashMap<String, Arena>();

    void replaceDefinitions(Map<String, Arena> definitions) {
        Map<String, Arena> reconciled = new LinkedHashMap<String, Arena>(definitions);
        pendingDefinitions.clear();
        for (Arena current : arenas.values()) {
            if (current.getState() == ArenaState.IN_USE) {
                // A null pending definition means the arena was removed from the config.
                pendingDefinitions.put(current.getId(), definitions.get(current.getId()));
                reconciled.put(current.getId(), current);
            }
        }
        arenas.clear();
        arenas.putAll(reconciled);
    }

    Arena acquireAvailable(String kitId) {
        for (Arena arena : arenas.values()) {
            if (arena.getState() == ArenaState.AVAILABLE && arena.supportsKit(kitId)) {
                arena.setState(ArenaState.IN_USE);
                return arena;
            }
        }
        return null;
    }

    void release(String arenaId) {
        Arena arena = arenas.get(arenaId);
        if (arena == null || arena.getState() != ArenaState.IN_USE) {
            return;
        }
        if (pendingDefinitions.containsKey(arenaId)) {
            Arena replacement = pendingDefinitions.remove(arenaId);
            if (replacement == null) {
                arenas.remove(arenaId);
            } else {
                arenas.put(arenaId, replacement);
            }
        } else {
            arena.setState(ArenaState.AVAILABLE);
        }
    }

    Arena get(String arenaId) {
        return arenas.get(arenaId);
    }

    Collection<Arena> all() {
        return new ArrayList<Arena>(arenas.values());
    }

    int size() {
        return arenas.size();
    }
}
