package com.poppy.practice.service;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ArenaWorldLayoutService {
    private static final String WORLD_NAME = "practice";
    private static final String MARKER_NAME = ".poppy-arena-layout-v2";
    private static final int SURFACE_Y = 3;
    private static final int GLASS_WALL_HEIGHT = 50;
    private static final int ARENA_COUNT = 10;
    private static final int ARENA_SPACING = 1000;
    private static final int ARENA_WIDTH = 100;
    private static final int ARENA_LENGTH = 150;
    private static final int LOBBY_MIN_X = -30;
    private static final int LOBBY_MAX_X = 30;
    private static final int LOBBY_MIN_Z = 70;
    private static final int LOBBY_MAX_Z = 130;

    private final JavaPlugin plugin;

    public ArenaWorldLayoutService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean ensureLayout() {
        World world = Bukkit.getWorld(WORLD_NAME);
        if (world == null) {
            plugin.getLogger().severe("Could not build the arena layout because world '"
                    + WORLD_NAME + "' is not loaded.");
            return false;
        }
        File marker = new File(world.getWorldFolder(), MARKER_NAME);
        if (marker.isFile() && isLayoutPresent(world)) {
            plugin.getLogger().info("Verified existing Void lobby and "
                    + ARENA_COUNT + " PvP arenas.");
            return false;
        }
        if (marker.isFile()) {
            plugin.getLogger().warning("Arena layout marker exists, but floor blocks are missing. Rebuilding.");
        }

        long startedAt = System.currentTimeMillis();
        plugin.getLogger().info("Building Void lobby and " + ARENA_COUNT + " PvP arenas...");
        Set<Long> touchedChunks = new LinkedHashSet<Long>();
        buildPlatform(world, LOBBY_MIN_X, LOBBY_MAX_X, LOBBY_MIN_Z, LOBBY_MAX_Z,
                touchedChunks);
        buildGlassEnclosure(world, LOBBY_MIN_X, LOBBY_MAX_X, LOBBY_MIN_Z, LOBBY_MAX_Z,
                touchedChunks);
        for (int arenaIndex = 0; arenaIndex < ARENA_COUNT; arenaIndex++) {
            int minimumX = arenaMinimumX(arenaIndex);
            int maximumX = minimumX + ARENA_WIDTH - 1;
            int minimumZ = -(ARENA_LENGTH / 2);
            int maximumZ = ARENA_LENGTH / 2 - 1;
            buildPlatform(world, minimumX, maximumX, minimumZ, maximumZ, touchedChunks);
            buildGlassEnclosure(world, minimumX, maximumX, minimumZ, maximumZ, touchedChunks);
        }
        world.setSpawnLocation(0, SURFACE_Y + 1, 100);
        world.setTime(6000L);
        world.setStorm(false);
        world.setThundering(false);
        world.save();
        int savedChunks = saveAndUnloadChunks(world, touchedChunks);
        try {
            Files.write(marker.toPath(),
                    Collections.singletonList("PoppyPractice arena layout version 2"),
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            plugin.getLogger().warning("Arena layout was built, but its marker could not be saved: "
                    + exception.getMessage());
        }
        plugin.getLogger().info("Built " + totalPlacedBlocks() + " lobby/arena blocks and saved "
                + savedChunks + " layout chunks in "
                + (System.currentTimeMillis() - startedAt) + "ms.");
        return true;
    }

    private boolean isLayoutPresent(World world) {
        if (!isPlatformPresent(world, LOBBY_MIN_X, LOBBY_MAX_X,
                LOBBY_MIN_Z, LOBBY_MAX_Z)
                || !isGlassEnclosurePresent(world, LOBBY_MIN_X, LOBBY_MAX_X,
                LOBBY_MIN_Z, LOBBY_MAX_Z)) {
            return false;
        }
        for (int arenaIndex = 0; arenaIndex < ARENA_COUNT; arenaIndex++) {
            int minimumX = arenaMinimumX(arenaIndex);
            int maximumX = minimumX + ARENA_WIDTH - 1;
            int minimumZ = -(ARENA_LENGTH / 2);
            int maximumZ = ARENA_LENGTH / 2 - 1;
            if (!isPlatformPresent(world, minimumX, maximumX, minimumZ, maximumZ)
                    || !isGlassEnclosurePresent(world, minimumX, maximumX,
                    minimumZ, maximumZ)) {
                return false;
            }
            if (world.getBlockAt(minimumX - 1, SURFACE_Y, 0).getType() != Material.AIR
                    || world.getBlockAt(maximumX + 1, SURFACE_Y, 0).getType() != Material.AIR) {
                return false;
            }
        }
        return world.getBlockAt(ARENA_SPACING + ARENA_WIDTH, SURFACE_Y,
                ARENA_LENGTH).getType() == Material.AIR;
    }

    private boolean isPlatformPresent(World world, int minimumX, int maximumX,
                                      int minimumZ, int maximumZ) {
        int middleX = (minimumX + maximumX) / 2;
        int middleZ = (minimumZ + maximumZ) / 2;
        return isFloorColumn(world, minimumX, minimumZ)
                && isFloorColumn(world, minimumX, maximumZ)
                && isFloorColumn(world, maximumX, minimumZ)
                && isFloorColumn(world, maximumX, maximumZ)
                && isFloorColumn(world, middleX, middleZ);
    }

    private boolean isFloorColumn(World world, int x, int z) {
        return world.getBlockAt(x, 0, z).getType() == Material.BEDROCK
                && world.getBlockAt(x, 1, z).getType() == Material.DIRT
                && world.getBlockAt(x, 2, z).getType() == Material.DIRT
                && world.getBlockAt(x, SURFACE_Y, z).getType() == Material.GRASS;
    }

    private void buildPlatform(World world, int minimumX, int maximumX,
                               int minimumZ, int maximumZ, Set<Long> touchedChunks) {
        loadChunks(world, minimumX, maximumX, minimumZ, maximumZ, touchedChunks);
        for (int x = minimumX; x <= maximumX; x++) {
            for (int z = minimumZ; z <= maximumZ; z++) {
                setType(world.getBlockAt(x, 0, z), Material.BEDROCK);
                setType(world.getBlockAt(x, 1, z), Material.DIRT);
                setType(world.getBlockAt(x, 2, z), Material.DIRT);
                setType(world.getBlockAt(x, SURFACE_Y, z), Material.GRASS);
            }
        }
    }

    private void buildGlassEnclosure(World world, int minimumX, int maximumX,
                                     int minimumZ, int maximumZ, Set<Long> touchedChunks) {
        int wallMinimumX = minimumX - 1;
        int wallMaximumX = maximumX + 1;
        int wallMinimumZ = minimumZ - 1;
        int wallMaximumZ = maximumZ + 1;
        loadChunks(world, wallMinimumX, wallMaximumX, wallMinimumZ, wallMaximumZ,
                touchedChunks);
        for (int y = SURFACE_Y + 1; y <= SURFACE_Y + GLASS_WALL_HEIGHT; y++) {
            for (int x = wallMinimumX; x <= wallMaximumX; x++) {
                setType(world.getBlockAt(x, y, wallMinimumZ), Material.GLASS);
                setType(world.getBlockAt(x, y, wallMaximumZ), Material.GLASS);
            }
            for (int z = minimumZ; z <= maximumZ; z++) {
                setType(world.getBlockAt(wallMinimumX, y, z), Material.GLASS);
                setType(world.getBlockAt(wallMaximumX, y, z), Material.GLASS);
            }
        }
    }

    private boolean isGlassEnclosurePresent(World world, int minimumX, int maximumX,
                                             int minimumZ, int maximumZ) {
        int middleX = (minimumX + maximumX) / 2;
        int middleZ = (minimumZ + maximumZ) / 2;
        int wallMinimumX = minimumX - 1;
        int wallMaximumX = maximumX + 1;
        int wallMinimumZ = minimumZ - 1;
        int wallMaximumZ = maximumZ + 1;
        return isGlassWallColumn(world, wallMinimumX, wallMinimumZ)
                && isGlassWallColumn(world, wallMaximumX, wallMinimumZ)
                && isGlassWallColumn(world, wallMinimumX, wallMaximumZ)
                && isGlassWallColumn(world, wallMaximumX, wallMaximumZ)
                && isGlassWallColumn(world, middleX, wallMinimumZ)
                && isGlassWallColumn(world, middleX, wallMaximumZ)
                && isGlassWallColumn(world, wallMinimumX, middleZ)
                && isGlassWallColumn(world, wallMaximumX, middleZ);
    }

    private boolean isGlassWallColumn(World world, int x, int z) {
        int bottomY = SURFACE_Y + 1;
        int middleY = SURFACE_Y + (GLASS_WALL_HEIGHT / 2);
        int topY = SURFACE_Y + GLASS_WALL_HEIGHT;
        return world.getBlockAt(x, bottomY, z).getType() == Material.GLASS
                && world.getBlockAt(x, middleY, z).getType() == Material.GLASS
                && world.getBlockAt(x, topY, z).getType() == Material.GLASS;
    }

    private void loadChunks(World world, int minimumX, int maximumX,
                            int minimumZ, int maximumZ, Set<Long> touchedChunks) {
        int minimumChunkX = minimumX >> 4;
        int maximumChunkX = maximumX >> 4;
        int minimumChunkZ = minimumZ >> 4;
        int maximumChunkZ = maximumZ >> 4;
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                world.loadChunk(chunkX, chunkZ, true);
                touchedChunks.add(chunkKey(chunkX, chunkZ));
            }
        }
    }

    @SuppressWarnings("deprecation")
    private int saveAndUnloadChunks(World world, Set<Long> touchedChunks) {
        int saved = 0;
        for (Long key : touchedChunks) {
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) (long) key;
            if (world.unloadChunk(chunkX, chunkZ, true, true)) {
                saved++;
            }
        }
        return saved;
    }

    private long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    @SuppressWarnings("deprecation")
    private void setType(Block block, Material material) {
        block.setTypeIdAndData(material.getId(), (byte) 0, false);
    }

    static int arenaMinimumX(int arenaIndex) {
        return (arenaIndex + 1) * ARENA_SPACING - ARENA_WIDTH / 2;
    }

    static int totalPlacedBlocks() {
        int lobbyWidth = LOBBY_MAX_X - LOBBY_MIN_X + 1;
        int lobbyLength = LOBBY_MAX_Z - LOBBY_MIN_Z + 1;
        int floorBlocks = (lobbyWidth * lobbyLength
                + ARENA_COUNT * ARENA_WIDTH * ARENA_LENGTH) * (SURFACE_Y + 1);
        int lobbyWallBlocks = enclosureBlockCount(lobbyWidth, lobbyLength);
        int arenaWallBlocks = ARENA_COUNT * enclosureBlockCount(ARENA_WIDTH, ARENA_LENGTH);
        return floorBlocks + lobbyWallBlocks + arenaWallBlocks;
    }

    static int enclosureBlockCount(int width, int length) {
        int perimeter = 2 * width + 2 * length + 4;
        return perimeter * GLASS_WALL_HEIGHT;
    }
}
