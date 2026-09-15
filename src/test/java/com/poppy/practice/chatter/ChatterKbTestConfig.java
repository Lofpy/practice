package com.poppy.practice.chatter;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

final class ChatterKbTestConfig {
    private ChatterKbTestConfig() {
    }

    static ChatterKbConfig defaults() {
        return ChatterKbConfig.load(YamlConfiguration.loadConfiguration(
                new File("src/main/resources/config.yml")));
    }
}
