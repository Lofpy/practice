package com.ascendingmc.survival;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

/** A deliberately narrow, same-seed operation; contains identifiers, never filesystem paths. */
public final class RegenerationPlan {
    public enum Operation { REGENERATE, RESTORE }

    private final String id;
    private final String levelName;
    private final List<String> dimensions;
    private final Operation operation;
    private final String sourceArchiveId;
    private final String createdAt;

    private RegenerationPlan(String id, String levelName, List<String> dimensions,
                             Operation operation, String sourceArchiveId, String createdAt) {
        validateId(id);
        validateName(levelName, false);
        if (dimensions == null || dimensions.isEmpty() || dimensions.size() > 64) {
            throw new IllegalArgumentException("Select between 1 and 64 dimensions.");
        }
        for (String dimension : dimensions) validateName(dimension, true);
        if (new HashSet<>(dimensions).size() != dimensions.size()) {
            throw new IllegalArgumentException("Duplicate dimensions are not allowed.");
        }
        if (operation == Operation.RESTORE) validateId(sourceArchiveId);
        else if (sourceArchiveId != null && !sourceArchiveId.isEmpty()) {
            throw new IllegalArgumentException("Regeneration cannot reference a restore archive.");
        }
        Instant.parse(createdAt);
        this.id = id;
        this.levelName = levelName;
        this.dimensions = List.copyOf(dimensions);
        this.operation = operation;
        this.sourceArchiveId = sourceArchiveId == null ? "" : sourceArchiveId;
        this.createdAt = createdAt;
    }

    public static RegenerationPlan regenerate(String levelName, List<String> dimensions) {
        return new RegenerationPlan(UUID.randomUUID().toString(), levelName, dimensions,
                Operation.REGENERATE, "", Instant.now().toString());
    }

    static RegenerationPlan restore(RegenerationPlan source) {
        return new RegenerationPlan(UUID.randomUUID().toString(), source.levelName, source.dimensions,
                Operation.RESTORE, source.id, Instant.now().toString());
    }

    public String id() { return id; }
    public String levelName() { return levelName; }
    public List<String> dimensions() { return dimensions; }
    public Operation operation() { return operation; }
    public String sourceArchiveId() { return sourceArchiveId; }
    public String createdAt() { return createdAt; }

    public Properties toProperties() {
        Properties properties = new Properties();
        properties.setProperty("format", "1");
        properties.setProperty("id", id);
        properties.setProperty("level-name", levelName);
        properties.setProperty("operation", operation.name());
        properties.setProperty("dimensions", String.join(",", dimensions));
        properties.setProperty("source-archive", sourceArchiveId);
        properties.setProperty("created-at", createdAt);
        return properties;
    }

    public static RegenerationPlan fromProperties(Properties properties) throws IOException {
        try {
            if (!"1".equals(properties.getProperty("format"))) {
                throw new IllegalArgumentException("Unsupported regeneration format.");
            }
            List<String> dimensions = new ArrayList<>(List.of(required(properties, "dimensions").split(",", -1)));
            return new RegenerationPlan(required(properties, "id"), required(properties, "level-name"), dimensions,
                    Operation.valueOf(required(properties, "operation")),
                    properties.getProperty("source-archive", ""), required(properties, "created-at"));
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid regeneration plan: " + invalid.getMessage(), invalid);
        }
    }

    static void validateId(String id) {
        if (id == null || !id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
                || !UUID.fromString(id).toString().equals(id)) {
            throw new IllegalArgumentException("Invalid regeneration archive identifier.");
        }
    }

    static void validateDimension(String name) { validateName(name, true); }

    private static void validateName(String name, boolean lowercase) {
        if (name == null || !name.matches(lowercase ? "[a-z0-9_-]{1,64}" : "[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Invalid world or dimension name.");
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        if (normalized.matches("con|prn|aux|nul|com[1-9]|lpt[1-9]")) {
            throw new IllegalArgumentException("Reserved filesystem name.");
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key + ".");
        return value;
    }
}
