package com.ascendingmc.survival;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public final class SafeSpawnSearchTest {
    @Test public void checksCenterFirstAndStopsAtFirstSafeSurface() {
        AtomicInteger attempts = new AtomicInteger();
        SafeSpawnSearch.Position found = SafeSpawnSearch.find(10, -20, 32,
                (x, z) -> { attempts.incrementAndGet(); return 70; }, ignored -> true).orElseThrow();
        assertEquals(new SafeSpawnSearch.Position(10, 71, -20), found);
        assertEquals(1, attempts.get());
    }

    @Test public void checksEachColumnOnceInBoundedAreaWhenNoSafeSpotExists() {
        Set<String> visited = new HashSet<>();
        assertTrue(SafeSpawnSearch.find(0, 0, 32, (x, z) -> {
            assertTrue(Math.abs(x) <= 32 && Math.abs(z) <= 32);
            assertTrue(visited.add(x + ":" + z));
            return 60;
        }, ignored -> false).isEmpty());
        assertEquals(65 * 65, visited.size());
    }

    @Test public void negativeCoordinatesAndDifferentSurfaceHeightsArePreserved() {
        SafeSpawnSearch.Position found = SafeSpawnSearch.find(-2, -3, 2,
                (x, z) -> x == -1 && z == -4 ? 95 : 60,
                pos -> pos.x() == -1 && pos.z() == -4).orElseThrow();
        assertEquals(new SafeSpawnSearch.Position(-1, 96, -4), found);
    }

    @Test public void doesNotSilentlyExpandRadiusOrOverflowCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> SafeSpawnSearch.find(0, 0, 33, (x,z) -> 60, p -> true));
        assertThrows(IllegalArgumentException.class, () -> SafeSpawnSearch.find(0, 0, -1, (x,z) -> 60, p -> true));
        assertThrows(ArithmeticException.class, () -> SafeSpawnSearch.find(Integer.MAX_VALUE, 0, 1,
                (x,z) -> 60, p -> false));
    }
}
