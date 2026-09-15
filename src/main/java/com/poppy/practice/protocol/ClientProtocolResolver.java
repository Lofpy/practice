package com.poppy.practice.protocol;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.UUID;

/** Resolves the original client version before any backend protocol translation. */
public final class ClientProtocolResolver {
    private ClientProtocolResolver() {
    }

    /**
     * Invoke on the main thread when installing a packet handler. Failures are
     * deliberately propagated: callers must fail open, never assume native 1.8
     * when an installed translator cannot identify the connection.
     */
    public static int resolve(Player player, PluginManager plugins)
            throws ReflectiveOperationException {
        return resolve(player, plugins, Class::forName);
    }

    static int resolve(Player player, PluginManager plugins, ClassLookup classes)
            throws ReflectiveOperationException {
        if (enabled(plugins.getPlugin("ProtocolSupport"))) {
            Class<?> api = classes.load("protocolsupport.api.ProtocolSupportAPI");
            Object version = api.getMethod("getProtocolVersion", Player.class)
                    .invoke(null, player);
            return ((Number) version.getClass().getMethod("getId")
                    .invoke(version)).intValue();
        }
        if (enabled(plugins.getPlugin("ViaVersion"))) {
            Class<?> via = classes.load("com.viaversion.viaversion.api.Via");
            Object api = via.getMethod("getAPI").invoke(null);
            return ((Number) api.getClass().getMethod("getPlayerVersion", UUID.class)
                    .invoke(api, player.getUniqueId())).intValue();
        }
        return 47;
    }

    private static boolean enabled(Plugin plugin) {
        return plugin != null && plugin.isEnabled();
    }

    interface ClassLookup {
        Class<?> load(String name) throws ClassNotFoundException;
    }
}
