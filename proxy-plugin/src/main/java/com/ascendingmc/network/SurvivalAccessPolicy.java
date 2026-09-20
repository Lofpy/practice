package com.ascendingmc.network;

import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

/** Immutable allowlist. In particular, newer protocols and snapshots are not automatically admitted. */
final class SurvivalAccessPolicy {
    private final String server;
    private final String fallback;
    private final String version;
    private final int protocol;

    private SurvivalAccessPolicy(String server, String fallback, String version, int protocol) {
        this.server = server;
        this.fallback = fallback;
        this.version = version;
        this.protocol = protocol;
    }

    static SurvivalAccessPolicy load(Properties properties) {
        Objects.requireNonNull(properties, "properties");
        String server = backend(required(properties, "survival-server"));
        String fallback = backend(required(properties, "fallback-server"));
        if (server.equalsIgnoreCase(fallback) || "survival".equalsIgnoreCase(fallback)) {
            throw new IllegalArgumentException("fallback-server must not be the Survival backend");
        }
        String version = required(properties, "required-version");
        if (!version.matches("[0-9]{1,2}\\.[0-9]{1,2}(?:\\.[0-9]{1,2})?")) {
            throw new IllegalArgumentException("required-version must identify a release, not a snapshot");
        }
        int protocol;
        try {
            protocol = Integer.parseInt(required(properties, "required-protocol"));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("required-protocol must be a release protocol number", ex);
        }
        if (protocol < 1 || protocol >= (1 << 30)) {
            throw new IllegalArgumentException("required-protocol must be a positive, non-snapshot protocol");
        }
        return new SurvivalAccessPolicy(server, fallback, version, protocol);
    }

    static SurvivalAccessPolicy closed(Properties properties) {
        String configured = properties.getProperty("survival-server", "survival").trim();
        String fallback = properties.getProperty("fallback-server", "lobby").trim();
        if (!configured.matches("[A-Za-z0-9_-]{1,64}")) configured = "survival";
        if (!fallback.matches("[A-Za-z0-9_-]{1,64}") || configured.equalsIgnoreCase(fallback)
                || "survival".equalsIgnoreCase(fallback)) fallback = "lobby";
        return new SurvivalAccessPolicy(configured, fallback, "26.3", -1);
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Missing " + key);
        return value.trim();
    }

    private static String backend(String name) {
        if (!name.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid backend name: " + name);
        return name;
    }

    boolean protects(String target) {
        // Retain the canonical name even if a configuration typo selects an extra backend.
        return target != null && (server.equalsIgnoreCase(target) || "survival".equals(target.toLowerCase(Locale.ROOT)));
    }

    boolean allows(String target, int clientProtocol, boolean supported) {
        return !protects(target) || protocol > 0 && supported && clientProtocol == protocol;
    }

    String server() { return server; }
    String fallback() { return fallback; }
    String version() { return version; }
    int protocol() { return protocol; }
    boolean isReady() { return protocol > 0; }
}
