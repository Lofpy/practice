package com.ascendingmc.survival;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ManagedWorldCreatorTest {
    @Test public void usesRealPaper263KeyOnlyConstructorWithoutServerInstance() {
        WorldCreator creator = ManagedWorldCreator.create("smoke_meadow");
        assertEquals(NamespacedKey.minecraft("smoke_meadow"), creator.key());
        assertEquals("smoke_meadow", creator.name());
    }

    @Test public void mapsNamesToLowercaseStorageIdentityAndKeepsOptions() {
        WorldCreator creator = ManagedWorldCreator.create("Event_26").environment(World.Environment.NETHER).seed(20260920);
        assertEquals(NamespacedKey.minecraft("event_26"), creator.key());
        assertEquals("event_26", creator.name());
        assertEquals(World.Environment.NETHER, creator.environment());
        assertEquals(20260920, creator.seed());
    }

    @Test public void validatesNamesBeforeCreatingPaperWorldOptions() {
        assertThrows(IllegalArgumentException.class, () -> ManagedWorldCreator.create("../outside"));
        assertThrows(IllegalArgumentException.class, () -> ManagedWorldCreator.create("plugins"));
    }
}
