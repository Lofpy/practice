package com.poppy.practice.arena;

import org.bukkit.Location;
import org.junit.Test;

import java.util.Collections;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ArenaRegistryTest {
    @Test
    public void reloadKeepsOccupiedArenaUntilReleaseAndThenAppliesNewSpawns() {
        ArenaRegistry registry = new ArenaRegistry();
        Arena original = arena("one", ArenaState.AVAILABLE, 10.0D);
        registry.replaceDefinitions(definitions(original));
        assertSame(original, registry.acquireAvailable("nodebuff"));

        Arena updated = arena("one", ArenaState.AVAILABLE, 500.0D);
        registry.replaceDefinitions(definitions(updated));

        assertSame(original, registry.get("one"));
        assertEquals(10.0D, registry.get("one").getFirstSpawn().getX(), 0.0D);
        assertNull(registry.acquireAvailable("nodebuff"));
        registry.release("one");
        assertSame(updated, registry.acquireAvailable("nodebuff"));
        assertEquals(500.0D, registry.get("one").getFirstSpawn().getX(), 0.0D);
    }

    @Test
    public void removedOccupiedArenaStaysAddressableButCannotBeReacquiredAfterRelease() {
        ArenaRegistry registry = new ArenaRegistry();
        Arena original = arena("one", ArenaState.AVAILABLE, 0.0D);
        registry.replaceDefinitions(definitions(original));
        registry.acquireAvailable("nodebuff");
        registry.replaceDefinitions(Collections.<String, Arena>emptyMap());

        assertSame(original, registry.get("one"));
        assertNull(registry.acquireAvailable("nodebuff"));
        registry.release("one");
        assertNull(registry.get("one"));
        assertEquals(0, registry.size());
    }

    @Test
    public void latestReloadWinsAndDisabledArenaDoesNotBecomeAvailableOnRelease() {
        ArenaRegistry registry = new ArenaRegistry();
        Arena original = arena("one", ArenaState.AVAILABLE, 0.0D);
        registry.replaceDefinitions(definitions(original));
        registry.acquireAvailable("nodebuff");
        registry.replaceDefinitions(Collections.<String, Arena>emptyMap());
        Arena disabled = arena("one", ArenaState.DISABLED, 50.0D);
        registry.replaceDefinitions(definitions(disabled));

        assertSame(original, registry.get("one"));
        registry.release("one");
        registry.release("one");
        assertSame(disabled, registry.get("one"));
        assertEquals(ArenaState.DISABLED, registry.get("one").getState());
        assertNull(registry.acquireAvailable("nodebuff"));
    }

    @Test
    public void reloadMakesNewArenasAvailableAlongsideReservedOnes() {
        ArenaRegistry registry = new ArenaRegistry();
        Arena original = arena("one", ArenaState.AVAILABLE, 0.0D);
        registry.replaceDefinitions(definitions(original));
        registry.acquireAvailable("nodebuff");
        Arena second = arena("two", ArenaState.AVAILABLE, 1000.0D);
        registry.replaceDefinitions(definitions(arena("one", ArenaState.AVAILABLE, 20.0D), second));

        assertSame(second, registry.acquireAvailable("NODEBUFF"));
        assertSame(original, registry.get("one"));
        assertNull(registry.acquireAvailable("nodebuff"));
    }

    @Test
    public void returnedCollectionCannotRemoveRegisteredArenas() {
        ArenaRegistry registry = new ArenaRegistry();
        registry.replaceDefinitions(definitions(arena("one", ArenaState.AVAILABLE, 0.0D)));
        registry.all().clear();
        assertEquals(1, registry.size());
    }

    @Test
    public void sharedArenaCannotHostBoxingAndNoDebuffAtTheSameTime() {
        ArenaRegistry registry = new ArenaRegistry();
        Arena shared = new Arena("shared", Arrays.asList("nodebuff", "boxing"),
                new Location(null, 1000.0D, 4.0D, -55.5D),
                new Location(null, 1000.0D, 4.0D, 55.5D), ArenaState.AVAILABLE);
        registry.replaceDefinitions(definitions(shared));

        assertSame(shared, registry.acquireAvailable("BOXING"));
        assertNull(registry.acquireAvailable("nodebuff"));
        assertNull(registry.acquireAvailable("boxing"));
        registry.release("shared");
        assertSame(shared, registry.acquireAvailable("nodebuff"));
        assertNull(registry.acquireAvailable("boxing"));
    }

    @Test
    public void comboReservesTheSamePhysicalArenaAsOtherModes() {
        ArenaRegistry registry = new ArenaRegistry();
        Arena shared = new Arena("shared", Arrays.asList("nodebuff", "boxing", "combo"),
                new Location(null, 1000.0D, 4.0D, -55.5D),
                new Location(null, 1000.0D, 4.0D, 55.5D), ArenaState.AVAILABLE);
        registry.replaceDefinitions(definitions(shared));

        assertSame(shared, registry.acquireAvailable("COMBO"));
        assertNull(registry.acquireAvailable("nodebuff"));
        assertNull(registry.acquireAvailable("boxing"));
        assertNull(registry.acquireAvailable("combo"));
        registry.release("shared");
        assertSame(shared, registry.acquireAvailable("boxing"));
        assertNull(registry.acquireAvailable("combo"));
        registry.release("shared");
        assertSame(shared, registry.acquireAvailable("combo"));
    }

    @Test
    public void kitSupportIsNormalizedAndDoesNotAliasTheInputCollection() {
        List<String> kits = new ArrayList<String>(Arrays.asList(" NoDebuff ", "BOXING"));
        Arena shared = new Arena("shared", kits, new Location(null, 0, 4, 0),
                new Location(null, 0, 4, 10), ArenaState.AVAILABLE);
        kits.clear();

        assertEquals("nodebuff", shared.getKitId());
        assertEquals(2, shared.getKitIds().size());
        assertTrue(shared.supportsKit("Boxing"));
        assertFalse(shared.supportsKit("unknown"));
        assertFalse(shared.supportsKit(null));
        assertFalse(arena("single", ArenaState.AVAILABLE, 0).supportsKit("boxing"));
    }

    @Test
    public void reloadedKitRestrictionsApplyOnlyAfterSharedMatchReleasesArena() {
        ArenaRegistry registry = new ArenaRegistry();
        Arena shared = new Arena("shared", Arrays.asList("nodebuff", "boxing"),
                new Location(null, 1000, 4, 0), new Location(null, 1000, 4, 10), ArenaState.AVAILABLE);
        registry.replaceDefinitions(definitions(shared));
        registry.acquireAvailable("boxing");
        registry.replaceDefinitions(definitions(arena("shared", ArenaState.AVAILABLE, 1000)));

        assertSame(shared, registry.get("shared"));
        assertNull(registry.acquireAvailable("nodebuff"));
        registry.release("shared");
        assertNull(registry.acquireAvailable("boxing"));
        assertTrue(registry.get("shared").supportsKit("nodebuff"));
    }

    private Arena arena(String id, ArenaState state, double x) {
        return new Arena(id, "nodebuff", new Location(null, x, 65.0D, 0.0D),
                new Location(null, x, 65.0D, 50.0D), state);
    }

    private Map<String, Arena> definitions(Arena... arenas) {
        Map<String, Arena> result = new LinkedHashMap<String, Arena>();
        for (Arena arena : arenas) {
            result.put(arena.getId(), arena);
        }
        return result;
    }
}
