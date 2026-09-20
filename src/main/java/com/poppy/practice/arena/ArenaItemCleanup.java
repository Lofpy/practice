package com.poppy.practice.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;

import java.util.ArrayList;
import java.util.List;

/** Removes dropped stacks only inside the released arena, never across its world. */
public final class ArenaItemCleanup {
    private ArenaItemCleanup() { }

    public static int clear(Arena arena) {
        if (arena == null) return 0;
        World world = arena.getFirstSpawn().getWorld();
        if (world == null) return 0;
        Location first = arena.getFirstSpawn(), second = arena.getSecondSpawn();
        double centerX = (first.getX() + second.getX()) / 2.0D;
        double centerZ = (first.getZ() + second.getZ()) / 2.0D;
        double halfX = halfExtent(first.getX(), second.getX(), 50.0D);
        double halfZ = halfExtent(first.getZ(), second.getZ(), 75.0D);
        List<int[]> temporarilyLoaded = new ArrayList<int[]>();
        try {
            // Both fighters may have left a far corner long enough for its chunks
            // to unload. Load existing arena chunks only; never generate the void
            // between arenas, and return unneeded chunks to the unload queue.
            for (int x = chunk(centerX - halfX); x <= chunk(centerX + halfX); x++) {
                for (int z = chunk(centerZ - halfZ); z <= chunk(centerZ + halfZ); z++) {
                    if (!world.isChunkLoaded(x, z) && world.loadChunk(x, z, false)) {
                        temporarilyLoaded.add(new int[] {x, z});
                    }
                }
            }
            int removed = 0;
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item && contains(arena, entity.getLocation())) {
                    entity.remove();
                    removed++;
                }
            }
            return removed;
        } finally {
            for (int[] coordinate : temporarilyLoaded) {
                world.unloadChunkRequest(coordinate[0], coordinate[1]);
            }
        }
    }

    public static boolean contains(Arena arena, Location location) {
        if (arena == null || location == null) return false;
        Location first = arena.getFirstSpawn(), second = arena.getSecondSpawn();
        if (first.getWorld() == null || !first.getWorld().equals(location.getWorld())
                || !first.getWorld().equals(second.getWorld())) return false;
        if (!Double.isFinite(location.getX()) || !Double.isFinite(location.getZ())) return false;
        double centerX = (first.getX() + second.getX()) / 2.0D;
        double centerZ = (first.getZ() + second.getZ()) / 2.0D;
        // The generated arenas are 100 x 150. Include the one-block enclosure,
        // but not the 1000-block gaps or other arenas in the shared practice world.
        double halfX = halfExtent(first.getX(), second.getX(), 50.0D);
        double halfZ = halfExtent(first.getZ(), second.getZ(), 75.0D);
        return Math.abs(location.getX() - centerX) <= halfX
                && Math.abs(location.getZ() - centerZ) <= halfZ;
    }

    private static double halfExtent(double first, double second, double minimum) {
        return Math.max(minimum, Math.abs(first - second) / 2.0D + 20.0D) + 1.0D;
    }

    private static int chunk(double coordinate) { return ((int) Math.floor(coordinate)) >> 4; }
}
