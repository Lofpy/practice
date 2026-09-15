package com.poppy.practice.kit;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface Kit {
    String getId();

    String getDisplayName();

    Material getIcon();

    short getIconDurability();

    ItemStack[] createInventoryContents();

    void apply(Player player);
}
