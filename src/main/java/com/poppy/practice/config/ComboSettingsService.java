package com.poppy.practice.config;

import com.poppy.practice.combat.ComboCombatService;
import com.poppy.practice.command.ComboKbCommand;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Main-thread command updates: validate, update live matches, then save with rollback on failure. */
public final class ComboSettingsService implements ComboKbCommand.Settings {
    private final ComboCombatService combatService;
    private final Supplier<FileConfiguration> configuration;
    private final Path configFile;
    private final Logger logger;

    public ComboSettingsService(ComboCombatService combatService,
                                Supplier<FileConfiguration> configuration, File configFile) {
        this(combatService, configuration, configFile, Logger.getLogger(ComboSettingsService.class.getName()));
    }

    public ComboSettingsService(ComboCombatService combatService,
                                Supplier<FileConfiguration> configuration, File configFile, Logger logger) {
        if (combatService == null || configuration == null || configFile == null || logger == null) {
            throw new IllegalArgumentException("Combo settings dependencies must not be null");
        }
        this.combatService = combatService;
        this.configuration = configuration;
        this.configFile = configFile.toPath().toAbsolutePath().normalize();
        this.logger = logger;
    }

    @Override
    public Map<String, Object> getSettings() {
        // A failed /practice reload can leave invalid YAML in memory. Report what is actually used.
        return combatService.getConfiguration().getSettings();
    }

    @Override
    public void set(String property, String value) throws IOException {
        String path = ComboConfig.configurationPath(property);
        FileConfiguration current = configuration.get();
        YamlConfiguration candidate = new YamlConfiguration();
        try {
            candidate.loadFromString(current.saveToString());
        } catch (InvalidConfigurationException exception) {
            throw new IOException("Could not copy the current configuration", exception);
        }

        // Validate the complete candidate, not just the one field (notably vertical min/max).
        candidate.set(path, ComboConfig.parseSettingValue(property, value));
        ComboConfig updated = ComboConfig.load(candidate);
        Object typedValue = updated.getSettings().get(property);
        candidate.set(path, typedValue);
        ComboCombatService.LiveUpdate liveUpdate;
        try {
            liveUpdate = combatService.updateLive(updated);
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, "Could not apply live Combo settings", failure);
            throw new IOException("Could not apply live Combo settings", failure);
        }
        try {
            // All of this runs in one main-thread command; no combat tick sees an uncommitted change.
            saveAtomically(candidate.saveToString());
        } catch (IOException | RuntimeException failure) {
            try {
                liveUpdate.rollback();
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            logger.log(Level.WARNING, "Could not save Combo settings; attempted live rollback", failure);
            throw failure;
        }

        // Do not use JavaPlugin.saveConfig(): it catches IOExceptions and could report false success.
        current.set(path, typedValue);
    }

    private void saveAtomically(String yaml) throws IOException {
        Path directory = configFile.getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "combo-settings-", ".tmp");
        try {
            Files.write(temporary, yaml.getBytes(StandardCharsets.UTF_8));
            // Fail closed on file systems without atomic replacement; never truncate the old config.
            Files.move(temporary, configFile,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }
}
