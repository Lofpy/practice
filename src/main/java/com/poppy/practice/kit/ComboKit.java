package com.poppy.practice.kit;

import com.poppy.practice.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionType;

/** Combo carries one equipped armor set and one replacement set in storage. */
@SuppressWarnings("deprecation")
public final class ComboKit implements Kit {
    @Override
    public String getId() {
        return "combo";
    }

    @Override
    public String getDisplayName() {
        return "Combo";
    }

    @Override
    public Material getIcon() {
        return Material.GOLDEN_APPLE;
    }

    @Override
    public short getIconDurability() {
        // Enchanted golden apple in the shared 1.7/1.8 legacy item format.
        return 1;
    }

    @Override
    public ItemStack[] createInventoryContents() {
        ItemStack[] contents = new ItemStack[36];
        contents[0] = new ItemBuilder(Material.DIAMOND_SWORD)
                .name("&bCombo Sword")
                .enchant(Enchantment.DAMAGE_ALL, 5)
                .build();
        contents[1] = new ItemStack(Material.ENDER_PEARL, 16);
        contents[2] = new ItemStack(Material.GOLDEN_APPLE, 64, (short) 1);
        contents[3] = speedTwoPotion();
        contents[8] = new ItemStack(Material.GOLDEN_CARROT, 64);
        for (int slot = 9; slot <= 13; slot++) {
            contents[slot] = speedTwoPotion();
        }
        contents[18] = armor(Material.DIAMOND_HELMET);
        contents[19] = armor(Material.DIAMOND_CHESTPLATE);
        contents[20] = armor(Material.DIAMOND_LEGGINGS);
        contents[21] = armor(Material.DIAMOND_BOOTS);
        return contents;
    }

    @Override
    public void apply(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.setHelmet(armor(Material.DIAMOND_HELMET));
        inventory.setChestplate(armor(Material.DIAMOND_CHESTPLATE));
        inventory.setLeggings(armor(Material.DIAMOND_LEGGINGS));
        inventory.setBoots(armor(Material.DIAMOND_BOOTS));
        inventory.setContents(createInventoryContents());
        player.updateInventory();
    }

    private static ItemStack armor(Material material) {
        return new ItemBuilder(material)
                .enchant(Enchantment.PROTECTION_ENVIRONMENTAL, 4)
                .enchant(Enchantment.DURABILITY, 3)
                .build();
    }

    private static ItemStack speedTwoPotion() {
        return new Potion(PotionType.SPEED, 2, false).toItemStack(1);
    }
}
