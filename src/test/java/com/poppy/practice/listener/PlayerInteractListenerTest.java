package com.poppy.practice.listener;

import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlayerInteractListenerTest {
    @Test
    public void allowsDrinkingPotionDuringCountdown() {
        ItemStack speedTwo = new ItemStack(Material.POTION, 1, (short) 8226);

        assertTrue(PlayerInteractListener.isDrinkablePotion(Action.RIGHT_CLICK_AIR, speedTwo));
        assertTrue(PlayerInteractListener.isDrinkablePotion(Action.RIGHT_CLICK_BLOCK, speedTwo));
    }

    @Test
    public void rejectsSplashPotionDuringCountdown() {
        ItemStack splashHealingTwo = new ItemStack(Material.POTION, 1, (short) 16421);

        assertFalse(PlayerInteractListener.isDrinkablePotion(Action.RIGHT_CLICK_AIR, splashHealingTwo));
    }

    @Test
    public void rejectsNonPotionAndNonRightClickActions() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemStack speedTwo = new ItemStack(Material.POTION, 1, (short) 8226);

        assertFalse(PlayerInteractListener.isDrinkablePotion(Action.RIGHT_CLICK_AIR, sword));
        assertFalse(PlayerInteractListener.isDrinkablePotion(Action.LEFT_CLICK_AIR, speedTwo));
        assertFalse(PlayerInteractListener.isDrinkablePotion(Action.RIGHT_CLICK_AIR, null));
    }
}
