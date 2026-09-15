package com.poppy.practice.config;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.arena.Arena;
import com.poppy.practice.arena.ArenaState;
import com.poppy.practice.util.LocationSerializer;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArenaConfigLoader {
    private final PracticePlugin plugin;

    public ArenaConfigLoader(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    public Map<String, Arena> load(File file) {
        Map<String, Arena> result = new LinkedHashMap<String, Arena>();
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = configuration.getConfigurationSection("arenas");
        if (root == null) {
            plugin.getLogger().warning("arenas.yml does not contain an 'arenas' section.");
            return result;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            List<String> kitIds = ArenaKitPolicy.kitIds(id, section);
            Location firstSpawn = LocationSerializer.read(section.getConfigurationSection("first-spawn"));
            Location secondSpawn = LocationSerializer.read(section.getConfigurationSection("second-spawn"));
            if (kitIds.isEmpty() || firstSpawn == null || secondSpawn == null) {
                plugin.getLogger().warning("Skipping invalid arena '" + id
                        + "'. Check its kits (or legacy kit), worlds, and spawn locations.");
                continue;
            }
            boolean enabled = section.getBoolean("enabled", true);
            ArenaState state = enabled ? ArenaState.AVAILABLE : ArenaState.DISABLED;
            result.put(id, new Arena(id, kitIds, firstSpawn, secondSpawn, state));
        }
        return result;
    }
}
