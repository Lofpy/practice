package com.poppy.practice.kit;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Boxing uses the normal melee/knockback pipeline, but its match rules prevent health loss. */
public final class BoxingKit implements Kit {
    @Override
    public String getId() {
        return "boxing";
    }

    @Override
    public String getDisplayName() {
        return "Boxing";
    }

    @Override
    public Material getIcon() {
        return Material.DIAMOND_SWORD;
    }

    @Override
    public short getIconDurability() {
        return 0;
    }

    @Override
    public ItemStack[] createInventoryContents() {
        ItemStack[] contents = new ItemStack[36];
        contents[0] = new ItemStack(Material.DIAMOND_SWORD);
        return contents;
    }

    @Override
    public void apply(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setContents(createInventoryContents());
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1), true);
        player.updateInventory();
    }
}
