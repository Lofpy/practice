package com.poppy.practice.bot;

import org.bukkit.ChatColor;
import org.bukkit.Material;

public enum BotSettingCategory {
    GENERAL("General", ChatColor.WHITE, 0, Material.PAPER, 7),
    MOVEMENT("Movement", ChatColor.GREEN, 9, Material.LEATHER_BOOTS, 5),
    AIM("Aim", ChatColor.GOLD, 18, Material.EYE_OF_ENDER, 1),
    COMBAT("Combat", ChatColor.RED, 27, Material.DIAMOND_SWORD, 14),
    HEALING("Healing", ChatColor.LIGHT_PURPLE, 36, Material.POTION, 10);

    private final String displayName;
    private final ChatColor color;
    private final int headerSlot;
    private final Material material;
    private final short paneDurability;

    BotSettingCategory(String displayName, ChatColor color, int headerSlot,
                       Material material, int paneDurability) {
        this.displayName = displayName;
        this.color = color;
        this.headerSlot = headerSlot;
        this.material = material;
        this.paneDurability = (short) paneDurability;
    }

    public String getDisplayName() { return displayName; }
    public ChatColor getColor() { return color; }
    public int getHeaderSlot() { return headerSlot; }
    public Material getMaterial() { return material; }
    public short getPaneDurability() { return paneDurability; }
}
