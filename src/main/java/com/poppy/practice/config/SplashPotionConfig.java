package com.poppy.practice.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class SplashPotionConfig {
    private static final double DEFAULT_HITBOX_SIZE = 1.0D;
    private static final double DEFAULT_X_SPEED = 1.0D;
    private static final double DEFAULT_Y_SPEED = 1.0D;
    private static final double DEFAULT_SPAWN_FORWARD_OFFSET = 0.0D;
    private static final double DEFAULT_SPAWN_HEIGHT_OFFSET = 0.0D;
    private static final double MAX_ABSOLUTE_SPAWN_OFFSET = 2.0D;
    private static final int DEFAULT_SELF_COLLISION_DELAY_TICKS = 0;
    private static final int MAX_SELF_COLLISION_DELAY_TICKS = 4;

    private final Tuning withoutSpeed;
    private final Tuning withSpeedTwo;

    private SplashPotionConfig(Tuning withoutSpeed, Tuning withSpeedTwo) {
        this.withoutSpeed = withoutSpeed;
        this.withSpeedTwo = withSpeedTwo;
    }

    public static SplashPotionConfig load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        double hitboxSize = positiveValue(plugin, config, "splash-potion.hitbox-size",
                DEFAULT_HITBOX_SIZE);
        double xSpeed = nonNegativeValue(plugin, config, "splash-potion.x-speed",
                DEFAULT_X_SPEED);
        double ySpeed = nonNegativeValue(plugin, config, "splash-potion.y-speed",
                DEFAULT_Y_SPEED);
        double spawnForwardOffset = spawnOffsetValue(plugin, config,
                "splash-potion.spawn-forward-offset", DEFAULT_SPAWN_FORWARD_OFFSET);
        double spawnHeightOffset = spawnOffsetValue(plugin, config,
                "splash-potion.spawn-height-offset", DEFAULT_SPAWN_HEIGHT_OFFSET);
        int legacySelfCollisionDelayTicks = selfCollisionDelayValue(plugin, config,
                "splash-potion.self-collision-delay-ticks",
                DEFAULT_SELF_COLLISION_DELAY_TICKS);
        int selfCollisionDelayWithoutSpeedTicks = selfCollisionDelayValue(plugin, config,
                "splash-potion.self-collision-delay-ticks-without-speed",
                legacySelfCollisionDelayTicks);
        int selfCollisionDelayWithSpeedTwoTicks = selfCollisionDelayValue(plugin, config,
                "splash-potion.self-collision-delay-ticks-with-speed-ii",
                legacySelfCollisionDelayTicks);
        Tuning legacyWithoutSpeed = new Tuning(hitboxSize, xSpeed, ySpeed,
                spawnForwardOffset, spawnHeightOffset, selfCollisionDelayWithoutSpeedTicks);
        Tuning legacyWithSpeedTwo = new Tuning(hitboxSize, xSpeed, ySpeed,
                spawnForwardOffset, spawnHeightOffset, selfCollisionDelayWithSpeedTwoTicks);
        Tuning withoutSpeed = loadTuning(plugin, config,
                "splash-potion.without-speed", legacyWithoutSpeed);
        Tuning withSpeedTwo = loadTuning(plugin, config,
                "splash-potion.with-speed-ii", legacyWithSpeedTwo);
        return new SplashPotionConfig(withoutSpeed, withSpeedTwo);
    }

    private static Tuning loadTuning(JavaPlugin plugin, FileConfiguration config,
                                     String path, Tuning fallback) {
        double hitboxSize = positiveValue(plugin, config, path + ".hitbox-size",
                fallback.getHitboxSize());
        double xSpeed = nonNegativeValue(plugin, config, path + ".x-speed",
                fallback.getXSpeed());
        double ySpeed = nonNegativeValue(plugin, config, path + ".y-speed",
                fallback.getYSpeed());
        double spawnForwardOffset = spawnOffsetValue(plugin, config,
                path + ".spawn-forward-offset", fallback.getSpawnForwardOffset());
        double spawnHeightOffset = spawnOffsetValue(plugin, config,
                path + ".spawn-height-offset", fallback.getSpawnHeightOffset());
        int selfCollisionDelayTicks = selfCollisionDelayValue(plugin, config,
                path + ".self-collision-delay-ticks", fallback.getSelfCollisionDelayTicks());
        return new Tuning(hitboxSize, xSpeed, ySpeed,
                spawnForwardOffset, spawnHeightOffset, selfCollisionDelayTicks);
    }

    public Tuning getWithoutSpeed() {
        return withoutSpeed;
    }

    public Tuning getWithSpeedTwo() {
        return withSpeedTwo;
    }

    public Tuning getTuning(boolean hasSpeedTwo) {
        return hasSpeedTwo ? withSpeedTwo : withoutSpeed;
    }

    private static double positiveValue(JavaPlugin plugin, FileConfiguration config,
                                        String path, double defaultValue) {
        double value = config.getDouble(path, defaultValue);
        if (isValidHitboxSize(value)) {
            return value;
        }
        plugin.getLogger().warning(path + " must be a finite number greater than 0. Using "
                + defaultValue + '.');
        return defaultValue;
    }

    private static double nonNegativeValue(JavaPlugin plugin, FileConfiguration config,
                                           String path, double defaultValue) {
        double value = config.getDouble(path, defaultValue);
        if (isValidSpeed(value)) {
            return value;
        }
        plugin.getLogger().warning(path + " must be a finite number greater than or equal to 0. Using "
                + defaultValue + '.');
        return defaultValue;
    }

    private static double spawnOffsetValue(JavaPlugin plugin, FileConfiguration config,
                                           String path, double defaultValue) {
        double value = config.getDouble(path, defaultValue);
        if (isValidSpawnOffset(value)) {
            return value;
        }
        plugin.getLogger().warning(path + " must be a finite number between -"
                + MAX_ABSOLUTE_SPAWN_OFFSET + " and " + MAX_ABSOLUTE_SPAWN_OFFSET
                + ". Using " + defaultValue + '.');
        return defaultValue;
    }

    private static int selfCollisionDelayValue(JavaPlugin plugin, FileConfiguration config,
                                               String path, int defaultValue) {
        double value = config.getDouble(path, defaultValue);
        if (isValidSelfCollisionDelay(value)) {
            return (int) value;
        }
        plugin.getLogger().warning(path + " must be a whole number between 0 and "
                + MAX_SELF_COLLISION_DELAY_TICKS + ". Using " + defaultValue + '.');
        return defaultValue;
    }

    public static boolean isValidHitboxSize(double value) {
        return Double.isFinite(value) && value > 0.0D;
    }

    public static boolean isValidSpeed(double value) {
        return Double.isFinite(value) && value >= 0.0D;
    }

    public static boolean isValidSpawnOffset(double value) {
        return Double.isFinite(value) && Math.abs(value) <= MAX_ABSOLUTE_SPAWN_OFFSET;
    }

    public static boolean isValidSelfCollisionDelay(double value) {
        return Double.isFinite(value)
                && value == Math.rint(value)
                && value >= 0.0D
                && value <= MAX_SELF_COLLISION_DELAY_TICKS;
    }

    public static final class Tuning {
        private final double hitboxSize;
        private final double xSpeed;
        private final double ySpeed;
        private final double spawnForwardOffset;
        private final double spawnHeightOffset;
        private final int selfCollisionDelayTicks;

        private Tuning(double hitboxSize, double xSpeed, double ySpeed,
                       double spawnForwardOffset, double spawnHeightOffset,
                       int selfCollisionDelayTicks) {
            this.hitboxSize = hitboxSize;
            this.xSpeed = xSpeed;
            this.ySpeed = ySpeed;
            this.spawnForwardOffset = spawnForwardOffset;
            this.spawnHeightOffset = spawnHeightOffset;
            this.selfCollisionDelayTicks = selfCollisionDelayTicks;
        }

        public double getHitboxSize() {
            return hitboxSize;
        }

        public double getXSpeed() {
            return xSpeed;
        }

        public double getYSpeed() {
            return ySpeed;
        }

        public double getSpawnForwardOffset() {
            return spawnForwardOffset;
        }

        public double getSpawnHeightOffset() {
            return spawnHeightOffset;
        }

        public int getSelfCollisionDelayTicks() {
            return selfCollisionDelayTicks;
        }
    }
}
