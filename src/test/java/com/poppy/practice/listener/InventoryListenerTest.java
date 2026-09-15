package com.poppy.practice.listener;

import com.poppy.practice.player.PlayerState;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class InventoryListenerTest {
    @Test
    public void recognizesPotionConsumptionDuringCountdown() {
        assertTrue(InventoryListener.isPotion(new ItemStack(Material.POTION, 1, (short) 8226)));
    }

    @Test
    public void rejectsNonPotionConsumptionDuringCountdown() {
        assertFalse(InventoryListener.isPotion(new ItemStack(Material.GOLDEN_CARROT)));
        assertFalse(InventoryListener.isPotion(null));
    }

    @Test
    public void removesOnlyTheBottleCreatedByTheConsumedPotion() {
        ItemStack[] contents = new ItemStack[36];
        contents[3] = new ItemStack(Material.GLASS_BOTTLE);
        contents[8] = new ItemStack(Material.GLASS_BOTTLE, 2);

        assertTrue(InventoryListener.removeOneGlassBottle(contents, 3));
        assertNull(contents[3]);
        assertEquals(2, contents[8].getAmount());

        assertTrue(InventoryListener.removeOneGlassBottle(contents, 3));
        assertEquals(1, contents[8].getAmount());
    }

    @Test
    public void leavesInventoryAloneWhenNoBottleExists() {
        ItemStack[] contents = new ItemStack[36];
        contents[3] = new ItemStack(Material.POTION);

        assertFalse(InventoryListener.removeOneGlassBottle(contents, 3));
        assertEquals(Material.POTION, contents[3].getType());
    }

    @Test
    public void allowsInventoryReorderingDuringMatchCountdown() {
        assertFalse(InventoryListener.isInventoryMovementProtected(PlayerState.STARTING));
        assertFalse(InventoryListener.isInventoryMovementProtected(PlayerState.FIGHTING));
        assertTrue(InventoryListener.isInventoryMovementProtected(PlayerState.LOBBY));
        assertTrue(InventoryListener.isInventoryMovementProtected(PlayerState.QUEUE));
        assertTrue(InventoryListener.isInventoryMovementProtected(null));
    }

    @Test
    public void refillsConsumedHotbarSlotWithSplashHealingDuringCountdown() {
        ItemStack[] contents = new ItemStack[36];
        contents[9] = new ItemStack(Material.POTION, 1, (short) 16421);

        assertTrue(InventoryListener.moveSplashHealingToHotbar(contents, 3));
        assertEquals(Material.POTION, contents[3].getType());
        assertEquals((short) 16421, contents[3].getDurability());
        assertNull(contents[9]);
    }

    @Test
    public void refillDoesNotMoveDrinkableOrOverwriteAnotherHotbarItem() {
        ItemStack[] contents = new ItemStack[36];
        contents[9] = new ItemStack(Material.POTION, 1, (short) 8226);
        assertFalse(InventoryListener.moveSplashHealingToHotbar(contents, 3));

        contents[9] = new ItemStack(Material.POTION, 1, (short) 16421);
        contents[3] = new ItemStack(Material.DIAMOND_SWORD);
        assertFalse(InventoryListener.moveSplashHealingToHotbar(contents, 3));
        assertEquals(Material.DIAMOND_SWORD, contents[3].getType());
        assertEquals(Material.POTION, contents[9].getType());
    }

    @Test
    public void identifiesSwordsForMatchDropProtection() {
        assertTrue(InventoryListener.isSword(new ItemStack(Material.DIAMOND_SWORD)));
        assertTrue(InventoryListener.isSword(new ItemStack(Material.WOOD_SWORD)));
        assertFalse(InventoryListener.isSword(new ItemStack(Material.ENDER_PEARL)));
        assertFalse(InventoryListener.isSword(null));
    }
}
