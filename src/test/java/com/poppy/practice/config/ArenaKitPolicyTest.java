package com.poppy.practice.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ArenaKitPolicyTest {
    @Test
    public void freshDefinitionsShareExactlyTenPhysicalArenas() throws Exception {
        YamlConfiguration configuration = defaults();
        ConfigurationSection arenas = configuration.getConfigurationSection("arenas");

        assertEquals(10, arenas.getKeys(false).size());
        for (String id : arenas.getKeys(false)) {
            assertEquals(Arrays.asList("nodebuff", "boxing", "combo"),
                    ArenaKitPolicy.kitIds(id, arenas.getConfigurationSection(id)));
        }
    }

    @Test
    public void legacyGeneratedArenasSupportBoxingAndComboWithoutMutatingTheirSettings() throws Exception {
        YamlConfiguration configuration = defaults();
        ConfigurationSection arenas = configuration.getConfigurationSection("arenas");
        for (String id : arenas.getKeys(false)) {
            ConfigurationSection arena = arenas.getConfigurationSection(id);
            arena.set("kits", null);
            arena.set("kit", "nodebuff");
        }
        String before = configuration.saveToString();

        for (String id : arenas.getKeys(false)) {
            assertEquals(Arrays.asList("nodebuff", "boxing", "combo"),
                    ArenaKitPolicy.kitIds(id, arenas.getConfigurationSection(id)));
        }
        assertEquals(before, configuration.saveToString());
    }

    @Test
    public void explicitKitListOverridesLegacyAndCanRestrictModes() throws Exception {
        ConfigurationSection arena = defaults().getConfigurationSection("arenas.nodebuff_01");
        arena.set("kit", "nodebuff");
        arena.set("kits", Collections.singletonList("boxing"));

        assertEquals(Collections.singletonList("boxing"), ArenaKitPolicy.kitIds("nodebuff_01", arena));
        arena.set("kits", Collections.singletonList("nodebuff"));
        assertEquals(Collections.singletonList("nodebuff"), ArenaKitPolicy.kitIds("nodebuff_01", arena));
        arena.set("kits", Arrays.asList("nodebuff", "boxing"));
        assertEquals(Arrays.asList("nodebuff", "boxing"), ArenaKitPolicy.kitIds("nodebuff_01", arena));
        arena.set("kits", Collections.singletonList("combo"));
        assertEquals(Collections.singletonList("combo"), ArenaKitPolicy.kitIds("nodebuff_01", arena));
        arena.set("kits", Collections.emptyList());
        assertTrue(ArenaKitPolicy.kitIds("nodebuff_01", arena).isEmpty());
    }

    @Test
    public void customLegacyArenasAreNotSilentlyShared() throws Exception {
        ConfigurationSection arena = defaults().getConfigurationSection("arenas.nodebuff_01");
        arena.set("kits", null);
        arena.set("kit", "nodebuff");

        assertEquals(Collections.singletonList("nodebuff"), ArenaKitPolicy.kitIds("custom", arena));
        arena.set("first-spawn.x", 1500.0D);
        assertEquals(Collections.singletonList("nodebuff"), ArenaKitPolicy.kitIds("nodebuff_01", arena));
        arena.set("first-spawn.x", 999.5D);
        arena.set("second-spawn.world", "custom-world");
        assertEquals(Collections.singletonList("nodebuff"), ArenaKitPolicy.kitIds("nodebuff_01", arena));
    }

    @Test
    public void normalizesKitNamesAndLeavesMissingDefinitionsInvalid() {
        YamlConfiguration arena = new YamlConfiguration();
        assertTrue(ArenaKitPolicy.kitIds("custom", arena).isEmpty());
        assertFalse(ArenaKitPolicy.isLegacyDefaultArena("nodebuff_01", arena));
        arena.set("kits", Arrays.asList(" BOXING ", "Nodebuff", "boxing", " Combo ", "combo", " "));

        assertEquals(Arrays.asList("boxing", "nodebuff", "combo"), ArenaKitPolicy.kitIds("custom", arena));
    }

    private YamlConfiguration defaults() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/arenas.yml");
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }
}
