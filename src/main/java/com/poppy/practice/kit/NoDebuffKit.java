package com.poppy.practice.kit;

import com.poppy.practice.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

@SuppressWarnings("deprecation")
public final class NoDebuffKit implements Kit {
    private static final int INFINITE_DURATION_TICKS = Integer.MAX_VALUE;

    @Override
    public String getId() {
        return "nodebuff";
    }

    @Override
    public String getDisplayName() {
        return "NoDebuff";
    }

    @Override
    public Material getIcon() {
        return Material.POTION;
    }

    @Override
    public short getIconDurability() {
        // Splash Potion of Healing II in the 1.7/1.8 legacy item format.
        return (short) 16421;
    }

    @Override
    public void apply(Player player) {
        PlayerInventory inventory = player.getInventory();

        inventory.setHelmet(armor(Material.DIAMOND_HELMET, false));
        inventory.setChestplate(armor(Material.DIAMOND_CHESTPLATE, false));
        inventory.setLeggings(armor(Material.DIAMOND_LEGGINGS, false));
        inventory.setBoots(armor(Material.DIAMOND_BOOTS, true));

        inventory.setContents(createInventoryContents());
        player.updateInventory();
    }

    @Override
    public ItemStack[] createInventoryContents() {
        ItemStack[] contents = new ItemStack[36];
        contents[0] = new ItemBuilder(Material.DIAMOND_SWORD)
                .name("&bNoDebuff Sword")
                .enchant(Enchantment.DAMAGE_ALL, 2)
                .enchant(Enchantment.DURABILITY, 3)
                .build();
        contents[1] = new ItemBuilder(Material.ENDER_PEARL).amount(16).build();
        contents[2] = infiniteFireResistancePotion();
        contents[3] = speedTwoPotion();
        for (int slot = 4; slot <= 7; slot++) {
            contents[slot] = healingPotion();
        }
        contents[8] = new ItemBuilder(Material.GOLDEN_CARROT).amount(64).build();

        // Replace two of the original healing potions with Speed II.
        contents[9] = speedTwoPotion();
        contents[10] = speedTwoPotion();
        for (int slot = 11; slot < contents.length; slot++) {
            contents[slot] = healingPotion();
        }
        return contents;
    }

    private ItemStack armor(Material material, boolean boots) {
        ItemBuilder builder = new ItemBuilder(material)
                .enchant(Enchantment.PROTECTION_ENVIRONMENTAL, 2)
                .enchant(Enchantment.DURABILITY, 3);
        if (boots) {
            builder.enchant(Enchantment.PROTECTION_FALL, 4);
        }
        return builder.build();
    }

    private ItemStack healingPotion() {
        return new Potion(PotionType.INSTANT_HEAL, 2, true).toItemStack(1);
    }

    private ItemStack speedTwoPotion() {
        return new Potion(PotionType.SPEED, 2, false).toItemStack(1);
    }

    private ItemStack infiniteFireResistancePotion() {
        ItemStack potion = new Potion(PotionType.FIRE_RESISTANCE, 1, false).toItemStack(1);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        meta.addCustomEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,
                INFINITE_DURATION_TICKS, 0), true);
        potion.setItemMeta(meta);
        return potion;
    }
}
