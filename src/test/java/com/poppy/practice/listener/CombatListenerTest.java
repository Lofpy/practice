package com.poppy.practice.listener;

import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CombatListenerTest {
    @Test
    public void identifiesOnlySwordRightClicksAsGuardAttempts() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);

        assertTrue(CombatListener.isSwordGuardAttempt(Action.RIGHT_CLICK_AIR, sword));
        assertTrue(CombatListener.isSwordGuardAttempt(Action.RIGHT_CLICK_BLOCK, sword));
        assertFalse(CombatListener.isSwordGuardAttempt(Action.LEFT_CLICK_AIR, sword));
        assertFalse(CombatListener.isSwordGuardAttempt(Action.RIGHT_CLICK_AIR,
                new ItemStack(Material.ENDER_PEARL)));
    }
}
