package com.poppy.practice.result;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class MatchParticipantSnapshot {
    private static final short SPLASH_HEALING_TWO_DATA = (short) 16421;
    private static final int INVENTORY_SIZE = 36;
    private static final int ARMOR_SIZE = 4;

    private final UUID playerId;
    private final String playerName;
    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final double health;
    private final int foodLevel;
    private final int remainingHealingPotions;
    private final int hits;
    private final int criticals;
    private final int guards;
    private final int healingPotionsThrown;
    private final int healingPotionsMissed;
    private final double potionAccuracyPercent;
    private final double healedHealth;
    private final double overhealedHealth;
    private final double opponentHealedHealth;

    private MatchParticipantSnapshot(UUID playerId, String playerName,
                                     ItemStack[] contents, ItemStack[] armor,
                                     double health, int foodLevel, int remainingHealingPotions,
                                     MatchParticipantStats stats) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.contents = contents;
        this.armor = armor;
        this.health = health;
        this.foodLevel = foodLevel;
        this.remainingHealingPotions = remainingHealingPotions;
        this.hits = stats.getHits();
        this.criticals = stats.getCriticals();
        this.guards = stats.getGuards();
        this.healingPotionsThrown = stats.getHealingPotionsThrown();
        this.healingPotionsMissed = stats.getFinalHealingPotionsMissed();
        this.potionAccuracyPercent = stats.getPotionAccuracyPercent();
        this.healedHealth = stats.getHealedHealth();
        this.overhealedHealth = stats.getOverhealedHealth();
        this.opponentHealedHealth = stats.getOpponentHealedHealth();
    }

    public static MatchParticipantSnapshot capture(UUID playerId, String playerName,
                                                   Player player, MatchParticipantStats stats) {
        return capture(playerId, playerName, player, stats, null, false);
    }

    public static MatchParticipantSnapshot capture(UUID playerId, String playerName,
                                                   Player player, MatchParticipantStats stats,
                                                   Integer remainingPotionOverride) {
        return capture(playerId, playerName, player, stats, remainingPotionOverride, false);
    }

    public static MatchParticipantSnapshot capture(UUID playerId, String playerName,
                                                   Player player, MatchParticipantStats stats,
                                                   Integer remainingPotionOverride,
                                                   boolean defeated) {
        ItemStack[] contents = cloneItems(player == null
                ? null : player.getInventory().getContents(), INVENTORY_SIZE);
        ItemStack[] armor = cloneItems(player == null
                ? null : player.getInventory().getArmorContents(), ARMOR_SIZE);
        int remaining = remainingPotionOverride == null
                ? countHealingPotions(contents) : Math.max(0, remainingPotionOverride);
        if (remainingPotionOverride != null) {
            trimHealingPotions(contents, remaining);
        }
        double health = player == null || defeated ? 0.0D : Math.max(0.0D, player.getHealth());
        int foodLevel = player == null ? 0 : player.getFoodLevel();
        return new MatchParticipantSnapshot(playerId, playerName, contents, armor,
                health, foodLevel, remaining, stats);
    }

    static int countHealingPotions(ItemStack[] contents) {
        int count = 0;
        if (contents == null) {
            return count;
        }
        for (ItemStack item : contents) {
            if (isSplashHealingTwo(item)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private static void trimHealingPotions(ItemStack[] contents, int remaining) {
        int toKeep = remaining;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!isSplashHealingTwo(item)) {
                continue;
            }
            if (toKeep <= 0) {
                contents[slot] = null;
                continue;
            }
            int kept = Math.min(toKeep, item.getAmount());
            item.setAmount(kept);
            toKeep -= kept;
        }
    }

    private static boolean isSplashHealingTwo(ItemStack item) {
        return item != null && item.getType() == Material.POTION
                && item.getDurability() == SPLASH_HEALING_TWO_DATA;
    }

    private static ItemStack[] cloneItems(ItemStack[] source, int size) {
        ItemStack[] copy = new ItemStack[size];
        if (source == null) {
            return copy;
        }
        int length = Math.min(source.length, size);
        for (int index = 0; index < length; index++) {
            copy[index] = source[index] == null ? null : source[index].clone();
        }
        return copy;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public ItemStack[] getContents() {
        return cloneItems(contents, contents.length);
    }

    public ItemStack[] getArmor() {
        return cloneItems(armor, armor.length);
    }

    public double getHealth() {
        return health;
    }

    public int getFoodLevel() {
        return foodLevel;
    }

    public int getRemainingHealingPotions() {
        return remainingHealingPotions;
    }

    public int getHits() {
        return hits;
    }

    public int getCriticals() {
        return criticals;
    }

    public int getGuards() {
        return guards;
    }

    public int getHealingPotionsThrown() {
        return healingPotionsThrown;
    }

    public int getHealingPotionsMissed() {
        return healingPotionsMissed;
    }

    public double getPotionAccuracyPercent() {
        return potionAccuracyPercent;
    }

    public double getHealedHealth() {
        return healedHealth;
    }

    public double getOverhealedHealth() {
        return overhealedHealth;
    }

    public double getOpponentHealedHealth() {
        return opponentHealedHealth;
    }
}
