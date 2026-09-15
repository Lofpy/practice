package com.poppy.practice.config;

import dev.cobblesword.nachospigot.knockback.KnockbackProfile;
import ga.windpvp.windspigot.knockback.CraftKnockbackProfile;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** Immutable, independent Combo tuning; no server-wide KB profile is consulted. */
public final class ComboConfig {
    private static final String KNOCKBACK_PATH = "combo.knockback.";
    private static final int DEFAULT_NO_DAMAGE_TICKS = 2;
    private static final int MAX_NO_DAMAGE_TICKS = 100;
    private static final double DEFAULT_FALL_HEIGHT = 3.0D;
    private static final double MAX_FALL_HEIGHT = 50.0D;
    private static final double DEFAULT_FALL_SPEED = 0.08D;
    // Stay below the native floating-movement threshold without granting flight permission.
    private static final double MIN_FALL_SPEED = 0.04D;
    private static final double MAX_FALL_SPEED = 0.5D;
    private static final List<String> SETTING_NAMES = createSettingNames();

    private final int noDamageTicks;
    private final boolean stopSprint;
    private final double fallHeight;
    private final double fallSpeed;
    private final Map<KnockbackValue, Double> knockbackValues;

    private ComboConfig(int noDamageTicks, boolean stopSprint, double fallHeight, double fallSpeed,
                        Map<KnockbackValue, Double> knockbackValues) {
        this.noDamageTicks = noDamageTicks;
        this.stopSprint = stopSprint;
        this.fallHeight = fallHeight;
        this.fallSpeed = fallSpeed;
        this.knockbackValues = new EnumMap<KnockbackValue, Double>(knockbackValues);
    }

    /** Reject the complete update when a setting is invalid; never apply a partial profile. */
    public static ComboConfig load(FileConfiguration config) {
        if (config == null) {
            throw new IllegalArgumentException("Combo configuration must not be null");
        }
        double interval = number(config, "combo.no-damage-ticks", DEFAULT_NO_DAMAGE_TICKS);
        if (!Double.isFinite(interval) || interval != Math.rint(interval)
                || interval < 0.0D || interval > MAX_NO_DAMAGE_TICKS) {
            throw new IllegalArgumentException("combo.no-damage-ticks must be a whole number from 0 to "
                    + MAX_NO_DAMAGE_TICKS);
        }

        Object stopSprintValue = config.get(KNOCKBACK_PATH + "stop-sprint");
        if (stopSprintValue != null && !(stopSprintValue instanceof Boolean)) {
            throw new IllegalArgumentException(KNOCKBACK_PATH + "stop-sprint must be true or false");
        }
        boolean stopSprint = stopSprintValue == null || (Boolean) stopSprintValue;
        double fallHeight = number(config, KNOCKBACK_PATH + "fall-height", DEFAULT_FALL_HEIGHT);
        if (!Double.isFinite(fallHeight) || fallHeight < 0.0D || fallHeight > MAX_FALL_HEIGHT) {
            throw new IllegalArgumentException(KNOCKBACK_PATH
                    + "fall-height must be a finite number from 0.0 to " + MAX_FALL_HEIGHT);
        }
        double fallSpeed = number(config, KNOCKBACK_PATH + "fall-speed", DEFAULT_FALL_SPEED);
        if (!Double.isFinite(fallSpeed) || fallSpeed < MIN_FALL_SPEED || fallSpeed > MAX_FALL_SPEED) {
            throw new IllegalArgumentException(KNOCKBACK_PATH
                    + "fall-speed must be a finite number from " + MIN_FALL_SPEED + " to " + MAX_FALL_SPEED);
        }
        Map<KnockbackValue, Double> values = new EnumMap<KnockbackValue, Double>(KnockbackValue.class);
        for (KnockbackValue setting : KnockbackValue.values()) {
            String path = KNOCKBACK_PATH + setting.path;
            double value = number(config, path, setting.defaultValue);
            if (!Double.isFinite(value) || value < setting.minimum || value > setting.maximum) {
                throw new IllegalArgumentException(path + " must be a finite number from "
                        + setting.minimum + " to " + setting.maximum);
            }
            values.put(setting, value);
        }
        if (values.get(KnockbackValue.VERTICAL_MIN) > values.get(KnockbackValue.VERTICAL_MAX)) {
            throw new IllegalArgumentException(KNOCKBACK_PATH
                    + "vertical-min must not exceed vertical-max");
        }
        return new ComboConfig((int) interval, stopSprint, fallHeight, fallSpeed, values);
    }

    private static double number(FileConfiguration config, String path, double fallback) {
        Object value = config.get(path);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(path + " must be a number");
        }
        return ((Number) value).doubleValue();
    }

    /** Canonical command names, in the same stable order used when displaying settings. */
    public static List<String> getSettingNames() {
        return SETTING_NAMES;
    }

    /** Effective typed values, including defaults; callers cannot modify this configuration. */
    public Map<String, Object> getSettings() {
        Map<String, Object> settings = new LinkedHashMap<String, Object>();
        settings.put("no-damage-ticks", noDamageTicks);
        settings.put("stop-sprint", stopSprint);
        settings.put("fall-height", fallHeight);
        settings.put("fall-speed", fallSpeed);
        for (KnockbackValue setting : KnockbackValue.values()) {
            settings.put(setting.path, knockbackValues.get(setting));
        }
        return Collections.unmodifiableMap(settings);
    }

    /** Only known Combo settings can be addressed; arbitrary configuration paths are rejected. */
    public static String configurationPath(String name) {
        if (!SETTING_NAMES.contains(name)) {
            throw new IllegalArgumentException("Unknown Combo setting: " + name);
        }
        return "no-damage-ticks".equals(name) ? "combo.no-damage-ticks" : KNOCKBACK_PATH + name;
    }

    /**
     * Parse a known setting into a YAML-compatible value. This does not validate ranges or
     * relationships between fields; callers must load the complete candidate before applying it.
     */
    public static Object parseSettingValue(String name, String rawValue) {
        String path = configurationPath(name);
        if (rawValue == null) {
            throw new IllegalArgumentException(path + " requires a value");
        }
        String text = rawValue.trim();
        if ("stop-sprint".equals(name)) {
            if (!"true".equalsIgnoreCase(text) && !"false".equalsIgnoreCase(text)) {
                throw new IllegalArgumentException(path + " must be true or false");
            }
            return Boolean.valueOf(text);
        } else {
            try {
                return Double.valueOf(text);
            } catch (NumberFormatException invalidNumber) {
                throw new IllegalArgumentException(path + " must be a number");
            }
        }
    }

    /** Parse and validate the complete candidate without mutating this config or any profile. */
    public ComboConfig withSetting(String name, String rawValue) {
        String path = configurationPath(name);
        Object value = parseSettingValue(name, rawValue);
        YamlConfiguration candidate = new YamlConfiguration();
        for (Map.Entry<String, Object> setting : getSettings().entrySet()) {
            candidate.set(configurationPath(setting.getKey()), setting.getValue());
        }
        candidate.set(path, value);
        return load(candidate);
    }

    private static List<String> createSettingNames() {
        List<String> names = new ArrayList<String>();
        names.add("no-damage-ticks");
        names.add("stop-sprint");
        names.add("fall-height");
        names.add("fall-speed");
        for (KnockbackValue setting : KnockbackValue.values()) {
            names.add(setting.path);
        }
        return Collections.unmodifiableList(names);
    }

    /** Tick interval before another equal-strength hit can deal full damage. */
    public int getNoDamageTicks() {
        return noDamageTicks;
    }

    /** WindSpigot rejects equal damage only during the first half of this counter. */
    public int getMaximumNoDamageTicks() {
        return noDamageTicks * 2;
    }

    /** Rise in blocks that suppresses upward knockback until landing; zero disables the rule. */
    public double getFallHeight() {
        return fallHeight;
    }

    /** Constant downward speed in blocks per tick after the height limit is reached. */
    public double getFallSpeed() {
        return fallSpeed;
    }

    /** Each participant gets a fresh, unregistered profile, including all projectile values. */
    public KnockbackProfile newKnockbackProfile() {
        KnockbackProfile profile = new CraftKnockbackProfile("poppy-combo");
        profile.setStopSprint(stopSprint);
        for (Map.Entry<KnockbackValue, Double> entry : knockbackValues.entrySet()) {
            entry.getKey().setter.accept(profile, entry.getValue());
        }
        // Never save/register this temporary profile or replace KnockbackConfig.currentKb.
        return profile;
    }

    private enum KnockbackValue {
        FRICTION_HORIZONTAL("friction-horizontal", 2.0D, 1.0D, 100.0D,
                KnockbackProfile::setFrictionHorizontal),
        FRICTION_VERTICAL("friction-vertical", 2.0D, 1.0D, 100.0D,
                KnockbackProfile::setFrictionVertical),
        HORIZONTAL("horizontal", 0.30D, 0.0D, 4.0D, KnockbackProfile::setHorizontal),
        VERTICAL("vertical", 0.10D, 0.0D, 4.0D, KnockbackProfile::setVertical),
        VERTICAL_MIN("vertical-min", -1.0D, -4.0D, 4.0D, KnockbackProfile::setVerticalMin),
        VERTICAL_MAX("vertical-max", 0.15D, -4.0D, 4.0D, KnockbackProfile::setVerticalMax),
        EXTRA_HORIZONTAL("extra-horizontal", 0.10D, 0.0D, 4.0D,
                KnockbackProfile::setExtraHorizontal),
        EXTRA_VERTICAL("extra-vertical", 0.0D, -4.0D, 4.0D, KnockbackProfile::setExtraVertical),
        WTAP_EXTRA_HORIZONTAL("wtap-extra-horizontal", 0.10D, 0.0D, 4.0D,
                KnockbackProfile::setWTapExtraHorizontal),
        WTAP_EXTRA_VERTICAL("wtap-extra-vertical", 0.0D, -4.0D, 4.0D,
                KnockbackProfile::setWTapExtraVertical),
        ADD_HORIZONTAL("add-horizontal", 0.0D, -4.0D, 4.0D, KnockbackProfile::setAddHorizontal),
        ADD_VERTICAL("add-vertical", 0.0D, -4.0D, 4.0D, KnockbackProfile::setAddVertical),
        ROD_HORIZONTAL("projectiles.rod.horizontal", 0.4D, 0.0D, 4.0D,
                KnockbackProfile::setRodHorizontal),
        ROD_VERTICAL("projectiles.rod.vertical", 0.4D, 0.0D, 4.0D,
                KnockbackProfile::setRodVertical),
        ARROW_HORIZONTAL("projectiles.arrow.horizontal", 0.4D, 0.0D, 4.0D,
                KnockbackProfile::setArrowHorizontal),
        ARROW_VERTICAL("projectiles.arrow.vertical", 0.4D, 0.0D, 4.0D,
                KnockbackProfile::setArrowVertical),
        PEARL_HORIZONTAL("projectiles.pearl.horizontal", 0.4D, 0.0D, 4.0D,
                KnockbackProfile::setPearlHorizontal),
        PEARL_VERTICAL("projectiles.pearl.vertical", 0.4D, 0.0D, 4.0D,
                KnockbackProfile::setPearlVertical),
        SNOWBALL_HORIZONTAL("projectiles.snowball.horizontal", 0.4D, 0.0D, 4.0D,
                KnockbackProfile::setSnowballHorizontal),
        SNOWBALL_VERTICAL("projectiles.snowball.vertical", 0.4D, 0.0D, 4.0D,
                KnockbackProfile::setSnowballVertical),
        EGG_HORIZONTAL("projectiles.egg.horizontal", 0.4D, 0.0D, 4.0D,
                KnockbackProfile::setEggHorizontal),
        EGG_VERTICAL("projectiles.egg.vertical", 0.4D, 0.0D, 4.0D,
                KnockbackProfile::setEggVertical);

        private final String path;
        private final double defaultValue;
        private final double minimum;
        private final double maximum;
        private final BiConsumer<KnockbackProfile, Double> setter;

        KnockbackValue(String path, double defaultValue, double minimum, double maximum,
                       BiConsumer<KnockbackProfile, Double> setter) {
            this.path = path;
            this.defaultValue = defaultValue;
            this.minimum = minimum;
            this.maximum = maximum;
            this.setter = setter;
        }
    }
}
