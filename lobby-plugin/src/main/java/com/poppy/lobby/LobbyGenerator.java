package com.poppy.lobby;

import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;

public final class LobbyGenerator extends ChunkGenerator {
    @Override
    public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biomes) {
        ChunkData data = createChunkData(world);
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                biomes.setBiome(localX, localZ, Biome.PLAINS);
                int x = chunkX * 16 + localX;
                int z = chunkZ * 16 + localZ;
                for (int y = LobbyLayout.FLOOR_Y - 1; y <= LobbyLayout.SPAWN_Y + 8; y++) {
                    Material material = LobbyLayout.blockAt(x, y, z);
                    if (material != Material.AIR) {
                        data.setBlock(localX, y, localZ, material);
                    }
                }
            }
        }
        return data;
    }

    @Override
    public boolean canSpawn(World world, int x, int z) {
        return Math.abs(x) < 20 && Math.abs(z) < 20;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, LobbyLayout.SPAWN_Y, 8.5, 180.0F, 0.0F);
    }
}
