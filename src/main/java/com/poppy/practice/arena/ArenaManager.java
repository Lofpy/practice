package com.poppy.practice.arena;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.config.ArenaConfigLoader;

import java.io.File;
import java.util.Collection;
import java.util.Map;

public final class ArenaManager {
    private final PracticePlugin plugin;
    private final ArenaConfigLoader loader;
    private final ArenaRegistry arenas = new ArenaRegistry();

    public ArenaManager(PracticePlugin plugin) {
        this.plugin = plugin;
        this.loader = new ArenaConfigLoader(plugin);
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "arenas.yml");
        Map<String, Arena> loaded = loader.load(file);
        arenas.replaceDefinitions(loaded);
        plugin.getLogger().info("Loaded " + arenas.size() + " arena(s).");
    }

    public Arena acquireAvailable(String kitId) {
        return arenas.acquireAvailable(kitId);
    }

    public void release(String arenaId) {
        Arena arena = arenas.get(arenaId);
        if (arena == null || arena.getState() != ArenaState.IN_USE) return;
        try {
            ArenaItemCleanup.clear(arena);
        } finally {
            arenas.release(arenaId);
        }
    }

    public Arena get(String arenaId) {
        return arenas.get(arenaId);
    }

    public Collection<Arena> all() {
        return arenas.all();
    }

    public int size() {
        return arenas.size();
    }
}
