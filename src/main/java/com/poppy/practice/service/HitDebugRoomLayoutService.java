package com.poppy.practice.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Builds a physically isolated room for repeatable hit/knockback diagnostics. */
public final class HitDebugRoomLayoutService {
    public static final String WORLD_NAME = "practice";
    public static final int SURFACE_Y = 3;
    public static final int MINIMUM_X = 11960;
    public static final int MAXIMUM_X = 12040;
    public static final int MINIMUM_Z = -30;
    public static final int MAXIMUM_Z = 30;
    public static final double PASSIVE_BOT_X = 11970.5D;
    public static final double NORMAL_ATTACK_BOT_X = 11990.5D;
    public static final double SPRINT_ATTACK_BOT_X = 12008.5D;
    public static final double PEARL_BOT_X = 12028.5D;
    public static final double BOT_Y = SURFACE_Y + 1.0D;
    public static final double BOT_Z = -6.5D;
    public static final double PEARL_SECOND_Z = 8.5D;

    private static final String MARKER_NAME = ".poppy-hit-debug-room-v3";
    private static final int WALL_HEIGHT = 20;
    private final JavaPlugin plugin;

    public HitDebugRoomLayoutService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean ensureLayout() {
        World world = Bukkit.getWorld(WORLD_NAME);
        if (world == null) {
            plugin.getLogger().severe("Could not build the hit debug room because world '"
                    + WORLD_NAME + "' is not loaded.");
            return false;
        }
        File marker = new File(world.getWorldFolder(), MARKER_NAME);
        if (marker.isFile() && isLayoutPresent(world)) {
            plugin.getLogger().info("Verified existing hit debug room.");
            return false;
        }

        long startedAt = System.currentTimeMillis();
        plugin.getLogger().info("Building isolated hit debug room...");
        Set<Long> chunks = new LinkedHashSet<Long>();
        loadChunks(world, chunks);
        buildFloor(world);
        buildWalls(world);
        buildStations(world);
        world.save();
        saveAndUnload(world, chunks);
        try {
            Files.write(marker.toPath(), Collections.singletonList(
                    "PoppyPractice hit debug room version 3"), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            plugin.getLogger().warning("Hit debug room marker could not be saved: "
                    + exception.getMessage());
        }
        plugin.getLogger().info("Built hit debug room in "
                + (System.currentTimeMillis() - startedAt) + "ms.");
        return true;
    }

    public static Location playerSpawn(World world) {
        return new Location(world, 12000.5D, BOT_Y, 22.5D, 180.0F, 0.0F);
    }

    public static Location passiveBotSpawn(World world) {
        return new Location(world, PASSIVE_BOT_X, BOT_Y, BOT_Z, 0.0F, 0.0F);
    }

    public static Location normalAttackBotSpawn(World world) {
        return new Location(world, NORMAL_ATTACK_BOT_X, BOT_Y, BOT_Z, 0.0F, 0.0F);
    }

    public static Location sprintAttackBotSpawn(World world) {
        return new Location(world, SPRINT_ATTACK_BOT_X, BOT_Y, BOT_Z, 0.0F, 0.0F);
    }

    public static Location pearlBotFirstSpawn(World world) {
        return new Location(world, PEARL_BOT_X, BOT_Y, BOT_Z, 0.0F, 0.0F);
    }

    public static Location pearlBotSecondTarget(World world) {
        return new Location(world, PEARL_BOT_X, BOT_Y, PEARL_SECOND_Z, 180.0F, 0.0F);
    }

    public static boolean contains(Location location) {
        return location != null && location.getWorld() != null
                && WORLD_NAME.equals(location.getWorld().getName())
                && location.getX() >= MINIMUM_X - 1.0D
                && location.getX() <= MAXIMUM_X + 1.0D
                && location.getZ() >= MINIMUM_Z - 1.0D
                && location.getZ() <= MAXIMUM_Z + 1.0D
                && location.getY() >= 0.0D
                && location.getY() <= SURFACE_Y + WALL_HEIGHT + 2.0D;
    }

    private boolean isLayoutPresent(World world) {
        return floorColumn(world, MINIMUM_X, MINIMUM_Z)
                && floorColumn(world, MAXIMUM_X, MAXIMUM_Z)
                && floorColumn(world, 12000, 0)
                && wallColumn(world, MINIMUM_X - 1, MINIMUM_Z - 1)
                && wallColumn(world, MAXIMUM_X + 1, MAXIMUM_Z + 1)
                && world.getBlockAt((int) PASSIVE_BOT_X, SURFACE_Y,
                        (int) BOT_Z).getType() == Material.WOOL
                && world.getBlockAt((int) NORMAL_ATTACK_BOT_X, SURFACE_Y,
                        (int) BOT_Z).getType() == Material.WOOL
                && world.getBlockAt((int) SPRINT_ATTACK_BOT_X, SURFACE_Y,
                        (int) BOT_Z).getType() == Material.WOOL
                && world.getBlockAt((int) PEARL_BOT_X, SURFACE_Y,
                        (int) BOT_Z).getType() == Material.WOOL;
    }

    private boolean floorColumn(World world, int x, int z) {
        return world.getBlockAt(x, 0, z).getType() == Material.BEDROCK
                && world.getBlockAt(x, 1, z).getType() == Material.STONE
                && world.getBlockAt(x, 2, z).getType() == Material.STONE
                && world.getBlockAt(x, SURFACE_Y, z).getType() != Material.AIR;
    }

    private boolean wallColumn(World world, int x, int z) {
        return world.getBlockAt(x, SURFACE_Y + 1, z).getType() == Material.GLASS
                && world.getBlockAt(x, SURFACE_Y + WALL_HEIGHT, z).getType() == Material.GLASS;
    }

    private void buildFloor(World world) {
        for (int x = MINIMUM_X; x <= MAXIMUM_X; x++) {
            for (int z = MINIMUM_Z; z <= MAXIMUM_Z; z++) {
                set(world.getBlockAt(x, 0, z), Material.BEDROCK, (byte) 0);
                set(world.getBlockAt(x, 1, z), Material.STONE, (byte) 0);
                set(world.getBlockAt(x, 2, z), Material.STONE, (byte) 0);
                set(world.getBlockAt(x, SURFACE_Y, z), Material.QUARTZ_BLOCK, (byte) 0);
            }
        }
    }

    private void buildWalls(World world) {
        int wallMinX = MINIMUM_X - 1;
        int wallMaxX = MAXIMUM_X + 1;
        int wallMinZ = MINIMUM_Z - 1;
        int wallMaxZ = MAXIMUM_Z + 1;
        for (int y = SURFACE_Y + 1; y <= SURFACE_Y + WALL_HEIGHT; y++) {
            for (int x = wallMinX; x <= wallMaxX; x++) {
                set(world.getBlockAt(x, y, wallMinZ), Material.GLASS, (byte) 0);
                set(world.getBlockAt(x, y, wallMaxZ), Material.GLASS, (byte) 0);
            }
            for (int z = MINIMUM_Z; z <= MAXIMUM_Z; z++) {
                set(world.getBlockAt(wallMinX, y, z), Material.GLASS, (byte) 0);
                set(world.getBlockAt(wallMaxX, y, z), Material.GLASS, (byte) 0);
            }
        }
    }

    private void buildStations(World world) {
        buildPad(world, (int) PASSIVE_BOT_X, (int) BOT_Z, (byte) 5);
        buildPad(world, (int) NORMAL_ATTACK_BOT_X, (int) BOT_Z, (byte) 14);
        buildPad(world, (int) SPRINT_ATTACK_BOT_X, (int) BOT_Z, (byte) 4);
        buildPad(world, (int) PEARL_BOT_X, (int) BOT_Z, (byte) 10);
        buildPad(world, (int) PEARL_BOT_X, (int) PEARL_SECOND_Z, (byte) 10);
        createSign(world, (int) PASSIVE_BOT_X + 5, (int) BOT_Z + 4,
                "[Passive]", "Takes normal KB", "No resistance", "Hit dummy");
        createSign(world, (int) NORMAL_ATTACK_BOT_X + 5, (int) BOT_Z + 4,
                "[No Sprint]", "No knockback", "Attacks in 3m", "Normal hit");
        createSign(world, (int) SPRINT_ATTACK_BOT_X + 5, (int) BOT_Z + 4,
                "[Sprint]", "No knockback", "Attacks in 3m", "Dash hit");
        createSign(world, (int) PEARL_BOT_X + 5, (int) BOT_Z + 4,
                "[Pearl]", "Every 3 sec", "Ping-pong route", "No attacks");
    }

    private void buildPad(World world, int centerX, int centerZ, byte color) {
        for (int x = centerX - 3; x <= centerX + 3; x++) {
            for (int z = centerZ - 3; z <= centerZ + 3; z++) {
                set(world.getBlockAt(x, SURFACE_Y, z), Material.WOOL, color);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void createSign(World world, int x, int z, String first,
                            String second, String third, String fourth) {
        Block block = world.getBlockAt(x, SURFACE_Y + 1, z);
        block.setTypeIdAndData(Material.SIGN_POST.getId(), (byte) 8, false);
        if (block.getState() instanceof Sign) {
            Sign sign = (Sign) block.getState();
            sign.setLine(0, first);
            sign.setLine(1, second);
            sign.setLine(2, third);
            sign.setLine(3, fourth);
            sign.update(true, false);
        }
    }

    private void loadChunks(World world, Set<Long> chunks) {
        for (int chunkX = (MINIMUM_X - 1) >> 4; chunkX <= (MAXIMUM_X + 1) >> 4; chunkX++) {
            for (int chunkZ = (MINIMUM_Z - 1) >> 4; chunkZ <= (MAXIMUM_Z + 1) >> 4; chunkZ++) {
                world.loadChunk(chunkX, chunkZ, true);
                chunks.add(((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL));
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void saveAndUnload(World world, Set<Long> chunks) {
        for (Long key : chunks) {
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) (long) key;
            world.unloadChunk(chunkX, chunkZ, true, true);
        }
    }

    @SuppressWarnings("deprecation")
    private void set(Block block, Material material, byte data) {
        block.setTypeIdAndData(material.getId(), data, false);
    }
}
