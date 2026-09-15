package com.poppy.practice.listener;

import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RefilledPotionUseGuardTest {
    private static final long MILLIS = 1_000_000L;

    @Test
    public void blocksContinuousRightClickUntilTheInputHasGoneQuiet() {
        RefilledPotionUseGuard guard = new RefilledPotionUseGuard();
        UUID playerId = UUID.randomUUID();
        ItemStack healing = healingPotion();
        guard.guard(playerId, 3, 0L);

        assertTrue(guard.shouldCancel(playerId, 3, healing, Action.RIGHT_CLICK_AIR,
                true, 200L * MILLIS));
        assertTrue(guard.shouldCancel(playerId, 3, healing, Action.RIGHT_CLICK_AIR,
                true, 400L * MILLIS));
        assertFalse(guard.shouldCancel(playerId, 3, healing, Action.RIGHT_CLICK_AIR,
                true, 800L * MILLIS));
    }

    @Test
    public void cannotUnlockWhileTheCountdownIsStillRunning() {
        RefilledPotionUseGuard guard = new RefilledPotionUseGuard();
        UUID playerId = UUID.randomUUID();
        ItemStack healing = healingPotion();
        guard.guard(playerId, 3, 0L);

        assertTrue(guard.shouldCancel(playerId, 3, healing, Action.RIGHT_CLICK_AIR,
                false, 1_000L * MILLIS));
        assertTrue(guard.shouldCancel(playerId, 3, healing, Action.RIGHT_CLICK_AIR,
                true, 1_200L * MILLIS));
        assertFalse(guard.shouldCancel(playerId, 3, healing, Action.RIGHT_CLICK_AIR,
                true, 1_600L * MILLIS));
    }

    @Test
    public void ignoresOtherItemsSlotsAndNonRightClickActions() {
        RefilledPotionUseGuard guard = new RefilledPotionUseGuard();
        UUID playerId = UUID.randomUUID();
        guard.guard(playerId, 3, 0L);

        assertFalse(guard.shouldCancel(playerId, 3,
                new ItemStack(Material.DIAMOND_SWORD), Action.RIGHT_CLICK_AIR,
                true, 100L * MILLIS));
        assertFalse(guard.shouldCancel(playerId, 2, healingPotion(),
                Action.RIGHT_CLICK_AIR, true, 200L * MILLIS));
        assertFalse(guard.shouldCancel(playerId, 3, healingPotion(),
                Action.LEFT_CLICK_AIR, true, 300L * MILLIS));
    }

    private static ItemStack healingPotion() {
        return new ItemStack(Material.POTION, 1, (short) 16421);
    }
}
