package com.poppy.practice.ui;

import com.poppy.practice.player.PlayerState;
import org.bukkit.Material;

/** Protected lobby hotbar slots identify actions; item names are presentation only. */
public enum LobbyAction {
    QUEUE(0, Material.DIAMOND_SWORD),
    EDIT_KIT(2, Material.BOOK),
    BOT_SETTINGS(4, Material.IRON_SWORD),
    TIER_TEST(6, Material.EXP_BOTTLE),
    SETTINGS(8, Material.REDSTONE_COMPARATOR),
    LEAVE_QUEUE(8, Material.REDSTONE);

    private final int slot;
    private final Material material;

    LobbyAction(int slot, Material material) {
        this.slot = slot;
        this.material = material;
    }

    public int getSlot() {
        return slot;
    }

    public Material getMaterial() {
        return material;
    }

    public static LobbyAction forSlot(PlayerState state, int slot, Material material) {
        for (LobbyAction action : values()) {
            boolean available = action == LEAVE_QUEUE
                    ? state == PlayerState.QUEUE : state == PlayerState.LOBBY;
            if (available && action.slot == slot && action.material == material) {
                return action;
            }
        }
        return null;
    }
}
