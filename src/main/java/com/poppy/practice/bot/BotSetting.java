package com.poppy.practice.bot;

import com.poppy.practice.language.PlayerLanguage;

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
            "Healing: Heal Opponent", "Allow bot potions to heal the opponent.", true);

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
    public String getDisplayName(PlayerLanguage language) {
        if (language == PlayerLanguage.ENGLISH) return getDisplayName();
        switch (this) {
            case COPY_PLAYER_SKIN: return "基本: プレイヤーのスキンを使用";
            case MAXIMUM_HEALTH: return "基本: 最大体力";
            case STRAFE_ENABLED: return "移動: 横移動";
            case PREFERRED_DISTANCE: return "移動: 維持する距離";
            case RETREAT_DISTANCE: return "移動: 後退する距離";
            case STRAFE_INPUT: return "移動: 横移動の強さ";
            case STRAFE_SWITCH_MINIMUM: return "移動: 横移動切替の最短tick";
            case STRAFE_SWITCH_MAXIMUM: return "移動: 横移動切替の最長tick";
            case MAXIMUM_YAW_CHANGE: return "エイム: 水平旋回速度";
            case MAXIMUM_PITCH_CHANGE: return "エイム: 上下旋回速度";
            case PREDICTION_TICKS: return "エイム: 予測tick";
            case AIM_ERROR_DEGREES: return "エイム: 誤差角度";
            case SWING_LEAD_DISTANCE: return "戦闘: 先行スイング距離";
            case ATTACK_RANGE: return "戦闘: 攻撃距離";
            case MINIMUM_CPS: return "戦闘: 最小CPS";
            case MAXIMUM_CPS: return "戦闘: 最大CPS";
            case SPRINT_RESET_TICKS: return "戦闘: スプリント解除tick";
            case HEALING_ENABLED: return "回復: 有効";
            case HEALING_HEALTH: return "回復: 開始体力";
            case HEALING_COOLDOWN: return "回復: クールダウンtick";
            case HEALING_POTION_COUNT: return "回復: ポーション数";
            case HEALING_RETREAT_TICKS: return "回復: 後退tick";
            case DOUBLE_POTION_HEALTH: return "回復: 2個使用する体力";
            case EMERGENCY_UNANSWERED_HITS: return "回復: 緊急回復の連続被弾数";
            case EMERGENCY_RETREAT_TICKS: return "回復: 緊急後退tick";
            case HEAL_OPPONENT: return "回復: 相手も回復する";
            default: return getDisplayName();
        }
    }

    public String getDescription(PlayerLanguage language) {
        if (language == PlayerLanguage.ENGLISH) return getDescription();
        switch (this) {
            case COPY_PLAYER_SKIN: return "Botにプレイヤーのスキンを使用します。";
            case MAXIMUM_HEALTH: return "Botの最大体力（20 = ハート10個）。";
            case STRAFE_ENABLED: return "戦闘中の横移動を許可します。";
            case PREFERRED_DISTANCE: return "Botが維持しようとする距離。";
            case RETREAT_DISTANCE: return "Botが後退を始める距離。";
            case STRAFE_INPUT: return "横移動入力の強さ。";
            case STRAFE_SWITCH_MINIMUM: return "横移動を切り替える最短間隔。";
            case STRAFE_SWITCH_MAXIMUM: return "横移動を切り替える最長間隔。";
            case MAXIMUM_YAW_CHANGE: return "接近中・回復中の水平方向の旋回速度。";
            case MAXIMUM_PITCH_CHANGE: return "接近中・回復中の上下方向の旋回速度。";
            case PREDICTION_TICKS: return "接近時の移動予測。近接時は相手を直視。";
            case AIM_ERROR_DEGREES: return "接近時の照準誤差。近接時は相手を直視。";
            case SWING_LEAD_DISTANCE: return "攻撃射程の手前でスイングを始める距離。";
            case ATTACK_RANGE: return "近接攻撃の最大射程（ブロック）。";
            case MINIMUM_CPS: return "1秒あたりの最小攻撃クリック数。";
            case MAXIMUM_CPS: return "1秒あたりの最大攻撃クリック数。";
            case SPRINT_RESET_TICKS: return "Wタップでスプリントを解除する時間。";
            case HEALING_ENABLED: return "Botの回復ポーション使用を許可します。";
            case HEALING_HEALTH: return "回復を開始する体力。";
            case HEALING_COOLDOWN: return "回復行動の最短間隔。";
            case HEALING_POTION_COUNT: return "Botの所持する回復ポーション数。";
            case HEALING_RETREAT_TICKS: return "回復前に後退する時間。";
            case DOUBLE_POTION_HEALTH: return "ポーションを2個使う体力。";
            case EMERGENCY_UNANSWERED_HITS: return "緊急回復を始める一方的な連続被弾数。";
            case EMERGENCY_RETREAT_TICKS: return "緊急回復時に後退する時間。";
            case HEAL_OPPONENT: return "Botのポーションが相手も回復します。";
            default: return getDescription();
        }
    }
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
