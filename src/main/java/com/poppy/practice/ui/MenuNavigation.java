package com.poppy.practice.ui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

public final class MenuNavigation {
    private MenuNavigation() {
    }

    public static boolean isTopSlot(Inventory inventory, int rawSlot) {
        return inventory != null && isTopSlot(inventory.getSize(), rawSlot);
    }

    public static boolean isTopSlot(int size, int rawSlot) {
        return rawSlot >= 0 && rawSlot < size;
    }

    public static boolean sameMenu(Inventory expected, Inventory current) {
        if (expected == null || current == null) {
            return false;
        }
        InventoryHolder holder = expected.getHolder();
        return holder instanceof MenuHolder && current.getHolder() == holder;
    }

    /** InventoryClickEvent must finish before opening or closing another screen. */
    public static void nextTick(Plugin plugin, final Player player,
                                final Inventory expected, final Runnable action) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()
                        && sameMenu(expected, player.getOpenInventory().getTopInventory())) {
                    action.run();
                }
            }
        });
    }
}
