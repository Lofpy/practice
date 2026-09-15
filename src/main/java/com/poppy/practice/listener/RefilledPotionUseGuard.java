package com.poppy.practice.listener;

import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Prevents a held use-item input from carrying over from a consumed drinkable
 * potion to a splash potion that is moved into the same hotbar slot.
 */
final class RefilledPotionUseGuard {
    static final long RELEASE_GAP_NANOS = 350_000_000L;
    private static final short SPLASH_HEALING_TWO_DATA = (short) 16421;

    private final Map<UUID, GuardedRefill> guardedRefills =
            new HashMap<UUID, GuardedRefill>();

    void guard(UUID playerId, int hotbarSlot, long nowNanos) {
        if (playerId == null || hotbarSlot < 0 || hotbarSlot > 8) {
            return;
        }
        guardedRefills.put(playerId, new GuardedRefill(hotbarSlot, nowNanos));
    }

    boolean shouldCancel(UUID playerId, int heldSlot, ItemStack item, Action action,
                         boolean canUnlock, long nowNanos) {
        GuardedRefill refill = guardedRefills.get(playerId);
        if (refill == null || !isRightClick(action)) {
            return false;
        }

        boolean guardedItem = heldSlot == refill.hotbarSlot && isSplashHealingTwo(item);
        if (!guardedItem) {
            // Right-click is a global input. Remember it even when the player has
            // temporarily selected another slot, but do not block that other item.
            refill.lastRightClickNanos = nowNanos;
            return false;
        }

        long quietNanos = Math.max(0L, nowNanos - refill.lastRightClickNanos);
        if (canUnlock && quietNanos >= RELEASE_GAP_NANOS) {
            guardedRefills.remove(playerId);
            return false;
        }

        refill.lastRightClickNanos = nowNanos;
        return true;
    }

    void clear(UUID playerId) {
        guardedRefills.remove(playerId);
    }

    private static boolean isRightClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    private static boolean isSplashHealingTwo(ItemStack item) {
        return item != null && item.getType() == Material.POTION
                && item.getDurability() == SPLASH_HEALING_TWO_DATA;
    }

    private static final class GuardedRefill {
        private final int hotbarSlot;
        private long lastRightClickNanos;

        private GuardedRefill(int hotbarSlot, long lastRightClickNanos) {
            this.hotbarSlot = hotbarSlot;
            this.lastRightClickNanos = lastRightClickNanos;
        }
    }
}
