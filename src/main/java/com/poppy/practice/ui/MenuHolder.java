package com.poppy.practice.ui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Identifies a plugin screen independently of its title and displayed items. */
public abstract class MenuHolder implements InventoryHolder {
    private static final InventoryFactory BUKKIT_INVENTORIES = new InventoryFactory() {
        @Override
        public Inventory create(InventoryHolder holder, int size, String title) {
            return Bukkit.createInventory(holder, size, title);
        }
    };
    private final Inventory inventory;

    protected MenuHolder(int size, String title) {
        this(size, title, BUKKIT_INVENTORIES);
    }

    MenuHolder(int size, String title, InventoryFactory factory) {
        inventory = factory.create(this, size,
                title.length() <= 32 ? title : title.substring(0, 32));
    }

    @Override
    public final Inventory getInventory() {
        return inventory;
    }

    public final boolean owns(Inventory candidate) {
        return candidate != null && candidate.getHolder() == this;
    }

    interface InventoryFactory {
        Inventory create(InventoryHolder holder, int size, String title);
    }
}
