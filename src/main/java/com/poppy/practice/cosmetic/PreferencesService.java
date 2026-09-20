package com.poppy.practice.cosmetic;

import com.poppy.practice.language.PlayerLanguage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Loaded and changed on the owning server thread; a failed save never changes preferences. */
public final class PreferencesService {
    private final Path file;
    private final Logger logger;
    private final Thread ownerThread;
    private final Map<UUID, KillEffect> effects = new HashMap<UUID, KillEffect>();
    private final Map<UUID, PlayerLanguage> languages = new HashMap<UUID, PlayerLanguage>();
    private YamlConfiguration document = new YamlConfiguration();
    private boolean writable = true;

    public PreferencesService(File dataDirectory, Logger logger) {
        if (dataDirectory == null || logger == null) throw new IllegalArgumentException("Missing preference storage");
        this.file = dataDirectory.toPath().resolve("cosmetic-preferences.yml");
        this.logger = logger;
        this.ownerThread = Thread.currentThread();
        load();
    }

    public KillEffect getKillEffect(UUID playerId) {
        requireOwnerThread();
        KillEffect effect = effects.get(playerId);
        return effect == null ? KillEffect.LIGHTNING : effect;
    }

    public boolean setKillEffect(UUID playerId, KillEffect effect) {
        requireOwnerThread();
        if (playerId == null || effect == null) throw new IllegalArgumentException("Missing player or effect");
        if (!writable) return false;
        if (effect == effects.get(playerId)) return true;
        try {
            YamlConfiguration candidate = new YamlConfiguration();
            candidate.loadFromString(document.saveToString());
            candidate.set("version", 1);
            candidate.set("players." + playerId + ".kill-effect", effect.name());
            save(candidate.saveToString());
            document = candidate;
            effects.put(playerId, effect);
            return true;
        } catch (Exception error) {
            logger.log(Level.SEVERE, "Could not save cosmetic preference for " + playerId
                    + "; the previous selection was retained.", error);
            return false;
        }
    }

    public PlayerLanguage getLanguage(UUID playerId) {
        requireOwnerThread();
        PlayerLanguage language = languages.get(playerId);
        return language == null ? PlayerLanguage.JAPANESE : language;
    }

    public boolean setLanguage(UUID playerId, PlayerLanguage language) {
        requireOwnerThread();
        if (playerId == null || language == null) throw new IllegalArgumentException("Missing player or language");
        if (!writable) return false;
        if (language == languages.get(playerId)) return true;
        try {
            YamlConfiguration candidate = new YamlConfiguration();
            candidate.loadFromString(document.saveToString());
            candidate.set("version", 1);
            String entry = "players." + playerId;
            // Retain the existing snapshot schema, including a kill effect for locale-only users.
            candidate.set(entry + ".kill-effect", getKillEffect(playerId).name());
            candidate.set(entry + ".locale", language.getCode());
            save(candidate.saveToString());
            document = candidate;
            languages.put(playerId, language);
            return true;
        } catch (Exception error) {
            logger.log(Level.SEVERE, "Could not save language for " + playerId
                    + "; the previous selection was retained.", error);
            return false;
        }
    }

    public boolean isWritable() {
        requireOwnerThread();
        return writable;
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            YamlConfiguration loaded = new YamlConfiguration();
            loaded.load(file.toFile());
            Object version = loaded.get("version");
            if (!(version instanceof Number) || ((Number) version).doubleValue() != 1.0D) {
                throw new IOException("Unsupported cosmetic preferences version");
            }
            ConfigurationSection players = loaded.getConfigurationSection("players");
            if (loaded.contains("players") && players == null) {
                throw new IOException("players must be a section");
            }
            Map<UUID, KillEffect> loadedEffects = new HashMap<UUID, KillEffect>();
            Map<UUID, PlayerLanguage> loadedLanguages = new HashMap<UUID, PlayerLanguage>();
            if (players != null) {
                for (String key : players.getKeys(false)) {
                    UUID playerId = UUID.fromString(key);
                    if (!playerId.toString().equals(key)) throw new IOException("Invalid canonical UUID: " + key);
                    ConfigurationSection entry = players.getConfigurationSection(key);
                    if (entry == null || !entry.isString("kill-effect")) {
                        throw new IOException("Missing kill-effect for " + key);
                    }
                    loadedEffects.put(playerId, KillEffect.valueOf(entry.getString("kill-effect")));
                    if (entry.contains("locale")) {
                        if (!entry.isString("locale")) throw new IOException("Invalid locale for " + key);
                        loadedLanguages.put(playerId, PlayerLanguage.fromCode(entry.getString("locale")));
                    }
                }
            }
            document = loaded;
            effects.putAll(loadedEffects);
            languages.putAll(loadedLanguages);
        } catch (Exception error) {
            writable = false;
            logger.log(Level.SEVERE, "Cannot load " + file
                    + "; cosmetic settings are read-only until the file is repaired and the server restarted."
                    + " Existing data has not been modified.", error);
        }
    }

    private void save(String contents) throws IOException {
        Path directory = file.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "cosmetic-preferences-", ".tmp");
        try {
            Files.write(temporary, contents.getBytes(StandardCharsets.UTF_8));
            // Never fall back to a non-atomic overwrite of a player's saved preferences.
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("Cosmetic preferences must be accessed on the owning server thread");
        }
    }
}
