package com.ascendingmc.survival;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/** The offline engine publishes this marker only after a complete, durable generation swap. */
record RegenerationCompletion(String id, Set<String> dimensions) {
    RegenerationCompletion {
        dimensions = Set.copyOf(dimensions);
    }

    static Optional<RegenerationCompletion> load(Path dataRoot) throws IOException {
        Path root = dataRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)
                || !root.toRealPath().equals(root)) {
            throw new IOException("Unsafe Survival plugin data directory.");
        }
        Path file = root.resolve("regeneration-completed.properties");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)
                || Files.size(file) > 65536) {
            throw new IOException("Unsafe or oversized regeneration completion marker.");
        }
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            values.load(reader);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid regeneration completion marker.", invalid);
        }
        return Optional.of(parse(values));
    }

    static RegenerationCompletion parse(Properties values) throws IOException {
        if (!"1".equals(values.getProperty("format"))) {
            throw new IOException("Unknown regeneration completion marker format.");
        }
        String id = values.getProperty("id", "");
        try {
            if (!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException("Noncanonical UUID");
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid regeneration completion ID.", invalid);
        }
        Set<String> dimensions = new LinkedHashSet<>();
        String names = values.getProperty("dimensions", "");
        for (String name : names.split(",", -1)) {
            if (!name.matches("[a-z0-9_-]{1,64}")) {
                throw new IOException("Invalid dimension in regeneration completion marker.");
            }
            if (!dimensions.add(name)) throw new IOException("Repeated dimension in regeneration completion marker.");
        }
        return new RegenerationCompletion(id, dimensions);
    }

    boolean unseen(String previousEpoch) {
        return !id.equals(previousEpoch);
    }

    boolean affects(String namespacedDimension) {
        return namespacedDimension != null && namespacedDimension.startsWith("minecraft:")
                && dimensions.contains(namespacedDimension.substring("minecraft:".length()));
    }
}
