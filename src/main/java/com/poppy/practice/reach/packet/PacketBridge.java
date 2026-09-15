package com.poppy.practice.reach.packet;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Installs the transport-specific packet bridge used by ReachGuard.
 *
 * <p>{@link #register()} and {@link #install(Player)} must be called from the
 * Bukkit main thread. Packet callbacks themselves are delivered on the
 * player's Netty event loop.</p>
 */
public interface PacketBridge {
    void register();

    void unregister();

    void install(Player player);

    void remove(Player player);

    /** Sends a real absolute target position through the normal packet path. */
    void resyncPlayer(Player viewer, Player target);

    boolean isInstalled(UUID playerId);
}
