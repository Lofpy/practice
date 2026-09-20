package com.poppy.practice.bot;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class BotSettings {
    private static final String KIT_ID = "nodebuff";
    private static final String BOT_NAME = "PracticeBot";

    private final boolean copyPlayerSkin;
    private final int enderPearlCooldownTicks;
    private final double maximumHealth;
    private final boolean strafeEnabled;
    private final boolean certificationMovement;
    private final double preferredDistance;
    private final double retreatDistance;
    private final double strafeInput;
    private final int strafeSwitchMinimumTicks;
    private final int strafeSwitchMaximumTicks;
    private final double maximumYawChange;
    private final double maximumPitchChange;
    private final double predictionTicks;
    private final double aimErrorDegrees;
    private final double swingRange;
    private final double attackRange;
    private final double minimumCps;
    private final double maximumCps;
    private final int sprintResetTicks;
    private final boolean healingEnabled;
    private final double healingHealth;
    private final int healingCooldownTicks;
    private final int healingPotionCount;
    private final int healingRetreatBeforeThrowTicks;
    private final double healingDoublePotionHealthThreshold;
    private final int healingEmergencyUnansweredHits;
    private final int healingEmergencyRetreatBeforeThrowTicks;
    private final boolean healingCanHealOpponent;

    private BotSettings(ConfigurationSection root, boolean certificationMovement) {
        this.certificationMovement = certificationMovement;
        copyPlayerSkin = root.getBoolean("copy-player-skin", true);
        int enderPearlCooldownSeconds = root.getRoot() == null ? 16
                : root.getRoot().getInt("match.ender-pearl-cooldown-seconds", 16);
        enderPearlCooldownTicks = BotPearlTargeting.cooldownTicks(clamp(
                enderPearlCooldownSeconds, 0, 300));
        maximumHealth = clamp(root.getDouble("maximum-health", 20.0D), 1.0D, 200.0D);

        strafeEnabled = root.getBoolean("movement.strafe-enabled", true);
        preferredDistance = clamp(root.getDouble("movement.preferred-distance", 2.65D),
                1.0D, 5.0D);
        retreatDistance = clamp(root.getDouble("movement.retreat-distance", 1.55D),
                0.5D, preferredDistance);
        strafeInput = clamp(root.getDouble("movement.strafe-input", 0.828D),
                0.0D, 1.0D);
        int minimumSwitch = clamp(root.getInt("movement.strafe-switch-minimum-ticks", 11),
                2, 100);
        int maximumSwitch = clamp(root.getInt("movement.strafe-switch-maximum-ticks", 20),
                2, 200);
        strafeSwitchMinimumTicks = Math.min(minimumSwitch, maximumSwitch);
        strafeSwitchMaximumTicks = Math.max(minimumSwitch, maximumSwitch);

        maximumYawChange = clamp(root.getDouble("aim.maximum-yaw-change-per-tick", 43.2D),
                1.0D, 180.0D);
        maximumPitchChange = clamp(root.getDouble("aim.maximum-pitch-change-per-tick", 32.4D),
                1.0D, 180.0D);
        predictionTicks = clamp(root.getDouble("aim.prediction-ticks", 1.0D),
                0.0D, 5.0D);
        aimErrorDegrees = clamp(root.getDouble("aim.error-degrees", 0.14D),
                0.0D, 15.0D);

        attackRange = clamp(root.getDouble("combat.attack-range", 3.0D), 1.0D, 6.0D);
        swingRange = attackRange + clamp(
                root.getDouble("combat.swing-lead-distance", 1.0D), 0.0D, 3.0D);
        double configuredMinimumCps = clamp(root.getDouble("combat.minimum-cps", 16.8D),
                1.0D, 20.0D);
        double configuredMaximumCps = clamp(root.getDouble("combat.maximum-cps", 19.2D),
                1.0D, 20.0D);
        minimumCps = Math.min(configuredMinimumCps, configuredMaximumCps);
        maximumCps = Math.max(configuredMinimumCps, configuredMaximumCps);
        sprintResetTicks = clamp(root.getInt("combat.sprint-reset-ticks", 1), 0, 10);

        healingEnabled = root.getBoolean("healing.enabled", true);
        healingHealth = clamp(root.getDouble("healing.health-threshold", 12.0D),
                1.0D, maximumHealth);
        healingCooldownTicks = clamp(root.getInt("healing.cooldown-ticks", 18), 1, 200);
        healingPotionCount = clamp(root.getInt("healing.potion-count", 29), 0, 36);
        healingRetreatBeforeThrowTicks = clamp(
                root.getInt("healing.retreat-before-throw-ticks", 0), 0, 100);
        healingDoublePotionHealthThreshold = clamp(
                root.getDouble("healing.double-potion-health-threshold", 6.0D),
                1.0D, healingHealth);
        healingEmergencyUnansweredHits = clamp(
                root.getInt("healing.emergency-unanswered-hits", 2),
                1, 10);
        healingEmergencyRetreatBeforeThrowTicks = clamp(
                root.getInt("healing.emergency-retreat-before-throw-ticks", 0),
                0, healingRetreatBeforeThrowTicks);
        healingCanHealOpponent = root.getBoolean("healing.can-heal-opponent", true);
    }

    public static BotSettings load(JavaPlugin plugin) {
        return load(plugin.getConfig());
    }

    /** Hard combat settings with fixed, forward/back combo spacing for certification. */
    public static BotSettings certificationPreset() {
        YamlConfiguration isolated = new YamlConfiguration();
        BotSetting.resetAll(isolated);
        isolated.set("match.ender-pearl-cooldown-seconds", 16);
        isolated.set("bot.movement.strafe-input", 0.18D);
        isolated.set("bot.movement.retreat-distance", 2.35D);
        return new BotSettings(isolated.getConfigurationSection("bot"), true);
    }

    static BotSettings load(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("bot");
        if (section == null) {
            section = config.createSection("bot");
        }
        return new BotSettings(section, false);
    }

    public static boolean ensureManagedDefaults(JavaPlugin plugin) {
        boolean changed = ensureManagedDefaults(plugin.getConfig());
        if (changed) {
            plugin.saveConfig();
        }
        return changed;
    }

    static boolean ensureManagedDefaults(FileConfiguration config) {
        boolean changed = false;
        for (BotSetting setting : BotSetting.values()) {
            if (!config.contains("bot." + setting.getPath())) {
                setting.reset(config);
                changed = true;
            }
        }
        if (config.contains("bot.combat.combo-retreat")) {
            config.set("bot.combat.combo-retreat", null);
            changed = true;
        }
        if (changed) {
            BotSetting.normalizeRelationships(config, null);
        }
        return changed;
    }

    /**
     * Converts the legacy base values plus difficulty multipliers into explicit
     * Hard-equivalent values, then removes the difficulty concept and identity
     * settings that are now fixed by the practice mode.
     */
    public static boolean migrateLegacyDifficulty(JavaPlugin plugin) {
        boolean changed = migrateLegacyDifficulty(plugin.getConfig());
        if (changed) {
            plugin.saveConfig();
        }
        return changed;
    }

    static boolean migrateLegacyDifficulty(FileConfiguration config) {
        if (!config.contains("bot.difficulty")) {
            return false;
        }

        scale(config, "movement.strafe-input", 0.72D, 1.15D);
        scaleInteger(config, "movement.strafe-switch-minimum-ticks", 8, 0.70D, 2);
        scaleInteger(config, "movement.strafe-switch-maximum-ticks", 28, 0.70D, 2);
        scale(config, "aim.maximum-yaw-change-per-tick", 32.0D, 1.35D);
        scale(config, "aim.maximum-pitch-change-per-tick", 24.0D, 1.35D);
        scale(config, "aim.prediction-ticks", 0.8D, 1.25D);
        scale(config, "aim.error-degrees", 0.35D, 0.40D);
        scale(config, "combat.minimum-cps", 8.0D, 1.20D);
        scale(config, "combat.maximum-cps", 12.0D, 1.20D);
        config.set("bot.combat.sprint-reset-ticks",
                Math.max(0, config.getInt("bot.combat.sprint-reset-ticks", 2) - 1));
        scale(config, "healing.health-threshold", 10.0D, 1.20D);
        scaleInteger(config, "healing.cooldown-ticks", 24, 0.75D, 1);
        config.set("bot.healing.retreat-before-throw-ticks", 0);

        for (BotSetting setting : BotSetting.values()) {
            if (!config.contains("bot." + setting.getPath())) {
                setting.reset(config);
            }
        }
        config.set("bot.difficulty", null);
        config.set("bot.enabled", null);
        config.set("bot.kit", null);
        config.set("bot.name", null);
        BotSetting.normalizeRelationships(config, null);
        return true;
    }

    public String getKitId() { return KIT_ID; }
    public String getName() { return BOT_NAME; }
    public boolean isCopyPlayerSkin() { return copyPlayerSkin; }
    public int getEnderPearlCooldownTicks() { return enderPearlCooldownTicks; }
    public double getMaximumHealth() { return maximumHealth; }
    public boolean isStrafeEnabled() { return strafeEnabled; }
    boolean isCertificationMovement() { return certificationMovement; }
    public double getPreferredDistance() { return preferredDistance; }
    public double getRetreatDistance() { return retreatDistance; }
    public double getStrafeInput() { return strafeInput; }
    public int getStrafeSwitchMinimumTicks() { return strafeSwitchMinimumTicks; }
    public int getStrafeSwitchMaximumTicks() { return strafeSwitchMaximumTicks; }
    public double getMaximumYawChange() { return maximumYawChange; }
    public double getMaximumPitchChange() { return maximumPitchChange; }
    public double getPredictionTicks() { return predictionTicks; }
    public double getAimErrorDegrees() { return aimErrorDegrees; }
    public double getSwingRange() { return swingRange; }
    public double getAttackRange() { return attackRange; }
    public double getMinimumCps() { return minimumCps; }
    public double getMaximumCps() { return maximumCps; }
    public int getSprintResetTicks() { return sprintResetTicks; }
    public boolean isHealingEnabled() { return healingEnabled; }
    public double getHealingHealth() { return healingHealth; }
    public int getHealingCooldownTicks() { return healingCooldownTicks; }
    public int getHealingPotionCount() { return healingPotionCount; }
    public int getHealingRetreatBeforeThrowTicks() { return healingRetreatBeforeThrowTicks; }
    public double getHealingDoublePotionHealthThreshold() {
        return healingDoublePotionHealthThreshold;
    }
    public int getHealingEmergencyUnansweredHits() { return healingEmergencyUnansweredHits; }
    public int getHealingEmergencyRetreatBeforeThrowTicks() {
        return healingEmergencyRetreatBeforeThrowTicks;
    }
    public boolean canHealingPotionHealOpponent() { return healingCanHealOpponent; }

    private static void scale(FileConfiguration config, String path,
                              double legacyDefault, double multiplier) {
        double value = config.getDouble("bot." + path, legacyDefault) * multiplier;
        config.set("bot." + path, Math.round(value * 1000.0D) / 1000.0D);
    }

    private static void scaleInteger(FileConfiguration config, String path, int legacyDefault,
                                     double multiplier, int minimum) {
        int value = (int) Math.round(config.getInt("bot." + path, legacyDefault) * multiplier);
        config.set("bot." + path, Math.max(minimum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
