package com.ascendingmc.survival;

import java.util.Optional;

/** Bounded, nearest-first surface search; no blocks are placed or removed. */
final class SafeSpawnSearch {
    private SafeSpawnSearch() { }

    record Position(int x, int feetY, int z) { }
    @FunctionalInterface interface ColumnHeight { int highestY(int x, int z); }
    @FunctionalInterface interface Standable { boolean test(Position position); }

    static Optional<Position> find(int originX, int originZ, int radius,
            ColumnHeight height, Standable standable) {
        if (radius < 0 || radius > 32) throw new IllegalArgumentException("Safety search radius must be 0-32.");
        for (int ring = 0; ring <= radius; ring++) {
            for (int dz = -ring; dz <= ring; dz++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    int x = Math.addExact(originX, dx);
                    int z = Math.addExact(originZ, dz);
                    Position position = new Position(x, Math.addExact(height.highestY(x, z), 1), z);
                    if (standable.test(position)) return Optional.of(position);
                }
            }
        }
        return Optional.empty();
    }
}
