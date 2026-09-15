package com.poppy.practice.bot;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.text.DecimalFormat;

public enum BotSetting {
    COPY_PLAYER_SKIN("copy-player-skin", 1, Material.SKULL_ITEM,
            "General: Copy Player Skin", "Use the player's skin for the bot.", true),
    MAXIMUM_HEALTH("maximum-health", 2, Material.GOLDEN_APPLE,
            "General: Maximum Health", "Bot health points (20 = 10 hearts).",
            20.0D, 1.0D, 200.0D, 1.0D, 5.0D, false),

    STRAFE_ENABLED("movement.strafe-enabled", 10, Material.LEATHER_BOOTS,
            "Movement: Strafe Enabled", "Allow sideways movement while fighting.", true),
    PREFERRED_DISTANCE("movement.preferred-distance", 11, Material.COMPASS,
            "Movement: Preferred Distance", "Distance the bot tries to maintain.",
            2.65D, 1.0D, 5.0D, 0.05D, 0.25D, false),
    RETREAT_DISTANCE("movement.retreat-distance", 12, Material.IRON_BOOTS,
            "Movement: Retreat Distance", "Distance where the bot backs away.",
            1.55D, 0.5D, 5.0D, 0.05D, 0.25D, false),
    STRAFE_INPUT("movement.strafe-input", 13, Material.FEATHER,
            "Movement: Strafe Input", "Strength of sideways movement.",
            0.828D, 0.0D, 1.0D, 0.02D, 0.10D, false),
    STRAFE_SWITCH_MINIMUM("movement.strafe-switch-minimum-ticks", 14, Material.WATCH,
            "Movement: Min Strafe Ticks", "Minimum ticks before changing strafe.",
            11.0D, 2.0D, 100.0D, 1.0D, 5.0D, true),
    STRAFE_SWITCH_MAXIMUM("movement.strafe-switch-maximum-ticks", 15, Material.WATCH,
            "Movement: Max Strafe Ticks", "Maximum ticks before changing strafe.",
            20.0D, 2.0D, 200.0D, 1.0D, 5.0D, true),

    MAXIMUM_YAW_CHANGE("aim.maximum-yaw-change-per-tick", 19, Material.EYE_OF_ENDER,
            "Aim: Yaw Speed", "Turn speed outside melee and during healing.",
            43.2D, 1.0D, 180.0D, 1.0D, 5.0D, false),
    MAXIMUM_PITCH_CHANGE("aim.maximum-pitch-change-per-tick", 20, Material.EYE_OF_ENDER,
            "Aim: Pitch Speed", "Vertical turn speed outside melee and during healing.",
            32.4D, 1.0D, 180.0D, 1.0D, 5.0D, false),
    PREDICTION_TICKS("aim.prediction-ticks", 21, Material.ARROW,
            "Aim: Prediction Ticks", "Movement prediction while approaching; melee faces directly.",
            1.0D, 0.0D, 5.0D, 0.1D, 0.5D, false),
    AIM_ERROR_DEGREES("aim.error-degrees", 22, Material.BOW,
            "Aim: Error Degrees", "Approach aim error; melee faces the opponent directly.",
            0.14D, 0.0D, 15.0D, 0.02D, 0.10D, false),

    SWING_LEAD_DISTANCE("combat.swing-lead-distance", 28, Material.WOOD_SWORD,
            "Combat: Swing Lead", "Extra distance before damage reach to start swinging.",
            1.0D, 0.0D, 3.0D, 0.1D, 0.5D, false),
    ATTACK_RANGE("combat.attack-range", 29, Material.DIAMOND_SWORD,
            "Combat: Attack Range", "Maximum melee reach in blocks.",
            3.0D, 1.0D, 6.0D, 0.05D, 0.25D, false),
    MINIMUM_CPS("combat.minimum-cps", 30, Material.IRON_SWORD,
            "Combat: Minimum CPS", "Lowest attack click rate.",
            16.8D, 1.0D, 20.0D, 0.2D, 1.0D, false),
    MAXIMUM_CPS("combat.maximum-cps", 31, Material.IRON_SWORD,
            "Combat: Maximum CPS", "Highest attack click rate.",
            19.2D, 1.0D, 20.0D, 0.2D, 1.0D, false),
    SPRINT_RESET_TICKS("combat.sprint-reset-ticks", 32, Material.STICK,
            "Combat: Sprint Reset Ticks", "W-tap sprint release duration.",
            1.0D, 0.0D, 10.0D, 1.0D, 2.0D, true),

    HEALING_ENABLED("healing.enabled", 36, Material.POTION,
            "Healing: Enabled", "Allow the bot to use healing potions.", true),
    HEALING_HEALTH("healing.health-threshold", 37, Material.POTION,
            "Healing: Health Threshold", "Health points where healing starts.",
            12.0D, 1.0D, 200.0D, 1.0D, 2.0D, false),
    HEALING_COOLDOWN("healing.cooldown-ticks", 38, Material.WATCH,
            "Healing: Cooldown Ticks", "Minimum delay between healing actions.",
            18.0D, 1.0D, 200.0D, 1.0D, 5.0D, true),
    HEALING_POTION_COUNT("healing.potion-count", 39, Material.GLASS_BOTTLE,
            "Healing: Potion Count", "Number of healing potions held by bot.",
            29.0D, 0.0D, 36.0D, 1.0D, 5.0D, true),
    HEALING_RETREAT_TICKS("healing.retreat-before-throw-ticks", 40, Material.IRON_BOOTS,
            "Healing: Retreat Ticks", "Ticks to run away before healing.",
            0.0D, 0.0D, 100.0D, 1.0D, 5.0D, true),
    DOUBLE_POTION_HEALTH("healing.double-potion-health-threshold", 41, Material.POTION,
            "Healing: Double-Pot Health", "Health points where two pots are used.",
            6.0D, 1.0D, 200.0D, 1.0D, 2.0D, false),
    EMERGENCY_UNANSWERED_HITS("healing.emergency-unanswered-hits", 42, Material.REDSTONE,
            "Healing: Emergency Hits", "Unanswered hits that force healing.",
            2.0D, 1.0D, 10.0D, 1.0D, 2.0D, true),
    EMERGENCY_RETREAT_TICKS("healing.emergency-retreat-before-throw-ticks", 43,
            Material.IRON_BOOTS, "Healing: Emergency Retreat", "Retreat ticks during emergency healing.",
            0.0D, 0.0D, 100.0D, 1.0D, 5.0D, true),
    HEAL_OPPONENT("healing.can-heal-opponent", 44, Material.SPECKLED_MELON,
            "Healing: Heal Opponent", "Allow bot potions to heal the opponent.", false);

    private static final String ROOT = "bot.";
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("0.###");

    private final String path;
    private final int slot;
    private final Material material;
    private final String displayName;
    private final String description;
    private final boolean booleanSetting;
    private final boolean defaultBoolean;
    private final double defaultValue;
    private final double minimum;
    private final double maximum;
    private final double smallStep;
    private final double largeStep;
    private final boolean integerSetting;

    BotSetting(String path, int slot, Material material, String displayName,
               String description, boolean defaultBoolean) {
        this.path = path;
        this.slot = slot;
        this.material = material;
        this.displayName = displayName;
        this.description = description;
        this.booleanSetting = true;
        this.defaultBoolean = defaultBoolean;
        this.defaultValue = 0.0D;
        this.minimum = 0.0D;
        this.maximum = 0.0D;
        this.smallStep = 0.0D;
        this.largeStep = 0.0D;
        this.integerSetting = false;
    }

    BotSetting(String path, int slot, Material material, String displayName,
               String description, double defaultValue, double minimum, double maximum,
               double smallStep, double largeStep, boolean integerSetting) {
        this.path = path;
        this.slot = slot;
        this.material = material;
        this.displayName = displayName;
        this.description = description;
        this.booleanSetting = false;
        this.defaultBoolean = false;
        this.defaultValue = defaultValue;
        this.minimum = minimum;
        this.maximum = maximum;
        this.smallStep = smallStep;
        this.largeStep = largeStep;
        this.integerSetting = integerSetting;
    }

    public String getPath() { return path; }
    public int getSlot() { return slot; }
    public Material getMaterial() { return material; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public boolean isBooleanSetting() { return booleanSetting; }
    public double getSmallStep() { return smallStep; }
    public double getLargeStep() { return largeStep; }

    public BotSettingCategory getCategory() {
        if (path.startsWith("movement.")) {
            return BotSettingCategory.MOVEMENT;
        }
        if (path.startsWith("aim.")) {
            return BotSettingCategory.AIM;
        }
        if (path.startsWith("combat.")) {
            return BotSettingCategory.COMBAT;
        }
        if (path.startsWith("healing.")) {
            return BotSettingCategory.HEALING;
        }
        return BotSettingCategory.GENERAL;
    }

    public String formattedValue(FileConfiguration config) {
        if (booleanSetting) {
            return booleanValue(config) ? "Enabled" : "Disabled";
        }
        return integerSetting
                ? Integer.toString((int) Math.round(numberValue(config)))
                : NUMBER_FORMAT.format(numberValue(config));
    }

    public boolean booleanValue(FileConfiguration config) {
        return config.getBoolean(ROOT + path, defaultBoolean);
    }

    public double numberValue(FileConfiguration config) {
        return clamp(config.getDouble(ROOT + path, defaultValue), minimum, maximum);
    }

    public void adjust(FileConfiguration config, boolean increase, boolean largeStep) {
        String fullPath = ROOT + path;
        if (booleanSetting) {
            config.set(fullPath, !booleanValue(config));
            return;
        }
        double step = largeStep ? this.largeStep : this.smallStep;
        double adjusted = numberValue(config) + (increase ? step : -step);
        setNumber(config, adjusted);
    }

    public void reset(FileConfiguration config) {
        if (booleanSetting) {
            config.set(ROOT + path, defaultBoolean);
        } else {
            setNumber(config, defaultValue);
        }
    }

    public static BotSetting fromPath(String path) {
        if (path == null) {
            return null;
        }
        for (BotSetting setting : values()) {
            if (setting.path.equalsIgnoreCase(path)) {
                return setting;
            }
        }
        return null;
    }

    public static void resetAll(FileConfiguration config) {
        for (BotSetting setting : values()) {
            setting.reset(config);
        }
        normalizeRelationships(config, null);
    }

    public static void normalizeRelationships(FileConfiguration config, BotSetting changed) {
        linkPair(config, PREFERRED_DISTANCE, RETREAT_DISTANCE, changed);
        linkPair(config, STRAFE_SWITCH_MAXIMUM, STRAFE_SWITCH_MINIMUM, changed);
        linkPair(config, MAXIMUM_CPS, MINIMUM_CPS, changed);
        linkPair(config, MAXIMUM_HEALTH, HEALING_HEALTH, changed);
        linkPair(config, HEALING_HEALTH, DOUBLE_POTION_HEALTH, changed);
        linkPair(config, HEALING_RETREAT_TICKS, EMERGENCY_RETREAT_TICKS, changed);
    }

    private static void linkPair(FileConfiguration config, BotSetting upper,
                                 BotSetting lower, BotSetting changed) {
        double upperValue = upper.numberValue(config);
        double lowerValue = lower.numberValue(config);
        if (lowerValue <= upperValue) {
            return;
        }
        if (changed == upper) {
            lower.setNumber(config, upperValue);
        } else {
            upper.setNumber(config, lowerValue);
        }
    }

    private void setNumber(FileConfiguration config, double value) {
        double rounded = integerSetting
                ? Math.round(value)
                : Math.round(value * 1000.0D) / 1000.0D;
        config.set(ROOT + path, clamp(rounded, minimum, maximum));
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }
}
