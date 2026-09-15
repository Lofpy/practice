package com.poppy.practice.chatter;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ChatterKbProtocolConfigTest {
    @Test
    public void defaultsSupportMinecraft1710And18() {
        ChatterKbConfig config = ChatterKbTestConfig.defaults();

        assertTrue(config.supportsProtocol(5));
        assertTrue(config.supportsProtocol(47));
        assertFalse(config.supportsProtocol(4));
        assertFalse(config.supportsProtocol(48));
        assertFalse(config.supportsProtocol(774));
        assertFalse(config.supportsProtocol(12345));
        assertFalse(config.supportsProtocol(-1));
    }

    @Test
    public void legacySingleProtocolSettingIsStillAccepted() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new File("src/main/resources/config.yml"));
        yaml.set("chatter-kb.target.protocols", null);
        yaml.set("chatter-kb.target.protocol", 5);

        ChatterKbConfig config = ChatterKbConfig.load(yaml);

        assertTrue(config.supportsProtocol(5));
        assertFalse(config.supportsProtocol(47));
    }
}
