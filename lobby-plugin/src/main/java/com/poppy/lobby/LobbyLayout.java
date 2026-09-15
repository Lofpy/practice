package com.poppy.lobby;

import org.bukkit.Material;

/** A finite, deterministic lobby: every block outside the platform is air. */
public final class LobbyLayout {
    public static final int HALF_SIZE = 32;
    public static final int FLOOR_Y = 64;
    public static final int SPAWN_Y = FLOOR_Y + 1;

    private LobbyLayout() { }

    public static Material blockAt(int x, int y, int z) {
        int ax = Math.abs(x);
        int az = Math.abs(z);
        if (x < -HALF_SIZE || x > HALF_SIZE || z < -HALF_SIZE || z > HALF_SIZE) {
            return Material.AIR;
        }
        if (y == FLOOR_Y - 1) {
            return Material.SMOOTH_BRICK;
        }
        if (y == FLOOR_Y) {
            return ax == HALF_SIZE || az == HALF_SIZE || ax <= 2 || az <= 2
                    ? Material.QUARTZ_BLOCK : Material.SMOOTH_BRICK;
        }
        if ((ax == HALF_SIZE || az == HALF_SIZE) && y >= SPAWN_Y && y <= SPAWN_Y + 1) {
            return Material.GLASS;
        }
        if (ax == 24 && az == 24) {
            if (y >= SPAWN_Y && y < SPAWN_Y + 4) {
                return Material.QUARTZ_BLOCK;
            }
            if (y == SPAWN_Y + 4) {
                return Material.GLOWSTONE;
            }
        }
        if (z == -16 && ((ax == 6 && y >= SPAWN_Y && y <= SPAWN_Y + 7)
                || (ax <= 6 && y == SPAWN_Y + 8))) {
            return Material.QUARTZ_BLOCK;
        }
        if (y == FLOOR_Y + 1 && ax == 10 && az == 10) {
            return Material.GLOWSTONE;
        }
        return Material.AIR;
    }

    public static boolean needsRescue(double x, double y, double z) {
        return !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || y < FLOOR_Y - 3 || Math.abs(x) > HALF_SIZE + 4 || Math.abs(z) > HALF_SIZE + 4;
    }
}
