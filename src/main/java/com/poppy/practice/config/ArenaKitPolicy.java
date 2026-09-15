package com.poppy.practice.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reads multi-kit definitions without rewriting existing arena files or world blocks. */
final class ArenaKitPolicy {
    private ArenaKitPolicy() {
    }

    static List<String> kitIds(String arenaId, ConfigurationSection section) {
        Set<String> result = new LinkedHashSet<String>();
        if (section.contains("kits")) {
            // An explicit list always wins, even if empty: never widen a custom restriction.
            for (String kitId : section.getStringList("kits")) {
                addKit(result, kitId);
            }
        } else {
            addKit(result, section.getString("kit"));
            if (result.contains("nodebuff") && isLegacyDefaultArena(arenaId, section)) {
                result.add("boxing");
                result.add("combo");
            }
        }
        return new ArrayList<String>(result);
    }

    private static void addKit(Set<String> kitIds, String kitId) {
        if (kitId != null && !kitId.trim().isEmpty()) {
            kitIds.add(kitId.trim().toLowerCase(Locale.ROOT));
        }
    }

    static boolean isLegacyDefaultArena(String arenaId, ConfigurationSection section) {
        if (arenaId == null || !arenaId.matches("nodebuff_(0[1-9]|10)")) {
            return false;
        }
        int arenaIndex = Integer.parseInt(arenaId.substring("nodebuff_".length()));
        double x = arenaIndex * 1000.0D - 0.5D;
        return isDefaultSpawn(section.getConfigurationSection("first-spawn"), x, -55.5D, 0.0D)
                && isDefaultSpawn(section.getConfigurationSection("second-spawn"), x, 55.5D, 180.0D);
    }

    private static boolean isDefaultSpawn(ConfigurationSection section, double x, double z, double yaw) {
        return section != null && "practice".equals(section.getString("world"))
                && section.getDouble("x") == x
                && section.getDouble("y") == 4.0D
                && section.getDouble("z") == z
                && section.getDouble("yaw") == yaw
                && section.getDouble("pitch") == 0.0D;
    }
}
