package com.poppy.practice.cosmetic;

import org.bukkit.Material;

/** Visual-only match victory cosmetics; the default is intentionally stable. */
public enum KillEffect {
    LIGHTNING("Lightning", Material.BLAZE_ROD, 11),
    EXPLOSION("Explosion", Material.TNT, 13),
    REDSTONE("Redstone", Material.REDSTONE, 15);

    private final String displayName;
    private final Material icon;
    private final int slot;

    KillEffect(String displayName, Material icon, int slot) {
        this.displayName = displayName;
        this.icon = icon;
        this.slot = slot;
    }

    public String getDisplayName() { return displayName; }
    public Material getIcon() { return icon; }
    public int getSlot() { return slot; }

    public static KillEffect atSlot(int rawSlot) {
        for (KillEffect effect : values()) {
            if (effect.slot == rawSlot) return effect;
        }
        return null;
    }
}
