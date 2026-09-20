package com.ascendingmc.network;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

@Plugin(id = "ascending-network", name = "AscendingNetwork", version = "0.1.0", authors = {"AscendingMC"})
public final class AscendingNetworkPlugin {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    // Never let a connection race startup/config validation.
    private volatile SurvivalAccessPolicy policy = SurvivalAccessPolicy.closed(new Properties());

    @Inject
    public AscendingNetworkPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        Properties properties = new Properties();
        try {
            Files.createDirectories(dataDirectory);
            Path file = dataDirectory.resolve("config.properties");
            if (Files.notExists(file)) {
                try (InputStream defaults = getClass().getResourceAsStream("/config.properties")) {
                    if (defaults == null) throw new IOException("Missing bundled config.properties");
                    Files.copy(defaults, file);
                }
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            SurvivalAccessPolicy loaded = SurvivalAccessPolicy.load(properties);
            ProtocolVersion runtimeVersion = ProtocolVersion.getProtocolVersion(loaded.protocol());
            if (!runtimeVersion.isSupported() || !runtimeVersion.getVersionsSupportedBy().contains(loaded.version())) {
                throw new IllegalArgumentException("This Velocity build does not support the configured release/protocol pair");
            }
            if (proxy.getServer(loaded.server()).isEmpty() || proxy.getServer(loaded.fallback()).isEmpty()) {
                throw new IllegalArgumentException("Survival or fallback backend is missing from velocity.toml");
            }
            policy = loaded;
            logger.info("AscendingNetwork enabled: Survival requires Minecraft {} (protocol {})", loaded.version(), loaded.protocol());
        } catch (IOException | IllegalArgumentException exception) {
            policy = SurvivalAccessPolicy.closed(properties);
            logger.error("AscendingNetwork: Survival admission is CLOSED because configuration validation failed", exception);
        }
    }

    /** Check the actual client protocol, not a ViaVersion-translated backend handshake. */
    @Subscribe(priority = Short.MIN_VALUE)
    public void onConnect(ServerPreConnectEvent event) {
        Optional<RegisteredServer> selected = event.getResult().getServer();
        if (selected.isEmpty()) return; // Preserve another plugin's cancellation.
        SurvivalAccessPolicy active = policy;
        String target = selected.get().getServerInfo().getName();
        Player player = event.getPlayer();
        ProtocolVersion version = player.getProtocolVersion();
        if (active.allows(target, version.getProtocol(), version.isSupported())) return;

        event.setResult(ServerPreConnectEvent.ServerResult.denied());
        Component reason = Component.text(active.isReady()
                ? "Survival は Minecraft Java " + active.version() + " 専用です。このバージョンで接続し直してください。"
                : "Survival は現在準備中です。ロビーまたは Practice をご利用ください。", NamedTextColor.RED);
        player.sendMessage(reason);

        // An initial forced-host connection has no previous backend to remain on.
        if (event.getPreviousServer() == null) {
            Optional<RegisteredServer> fallback = proxy.getServer(active.fallback());
            if (fallback.isPresent() && !active.protects(fallback.get().getServerInfo().getName())) {
                event.setResult(ServerPreConnectEvent.ServerResult.allowed(fallback.get()));
            } else {
                player.disconnect(reason);
            }
        }
    }
}
