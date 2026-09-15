package com.poppy.practice.chatter;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class ChatterKbDefaultModeTest {
    @Test
    public void bundledConfigurationDefaultsToChatterOnly() throws Exception {
        InputStream resource = getClass().getResourceAsStream("/config.yml");
        assertNotNull(resource);
        try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            assertEquals(ChatterKbMode.CHATTER_ONLY,
                    ChatterKbConfig.load(YamlConfiguration.loadConfiguration(reader)).mode);
        }
    }

    @Test
    public void emptyConfigurationDefaultsToChatterOnly() {
        assertEquals(ChatterKbMode.CHATTER_ONLY,
                ChatterKbConfig.load(new YamlConfiguration()).mode);
    }

    @Test
    public void missingModeInExistingConfigurationUsesTheNewDefault() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("chatter-kb.compensation.correction-scale", 0.75D);
        ChatterKbConfig config = ChatterKbConfig.load(yaml);
        assertEquals(ChatterKbMode.CHATTER_ONLY, config.mode);
        assertEquals(0.75D, config.correctionScale, 0.0D);
    }

    @Test
    public void explicitlyConfiguredModesAreNotOverwrittenByTheDefault() {
        for (ChatterKbMode mode : ChatterKbMode.values()) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("chatter-kb.mode", mode.name());
            assertEquals(mode, ChatterKbConfig.load(yaml).mode);
        }
    }

    @Test
    public void defaultCancellationRequiresConfirmedChattering() {
        ChatterKbConfig config = ChatterKbConfig.load(new YamlConfiguration());
        assertFalse(ChatterKbService.shouldDisableConfirmedDuplicate(
                config.mode, config.cancelConfirmedDuplicates, false));
        assertTrue(ChatterKbService.shouldDisableConfirmedDuplicate(
                config.mode, config.cancelConfirmedDuplicates, true));
    }
}
