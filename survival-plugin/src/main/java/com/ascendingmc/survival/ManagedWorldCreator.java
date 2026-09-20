package com.ascendingmc.survival;

import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.WorldCreator;

/** Paper 26.3 accepts either a name or a key, never both. New dimensions use the key API. */
public final class ManagedWorldCreator {
    private ManagedWorldCreator() { }

    public static WorldCreator create(String name) {
        ManagedWorldPaths.validateName(name);
        return WorldCreator.ofKey(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
    }
}
