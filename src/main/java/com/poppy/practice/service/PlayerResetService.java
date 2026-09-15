package com.poppy.practice.service;

import com.poppy.practice.combat.ComboCombatService;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

public final class PlayerResetService {
    private final ComboCombatService comboCombatService;

    public PlayerResetService() {
        this(null);
    }

    public PlayerResetService(ComboCombatService comboCombatService) {
        this.comboCombatService = comboCombatService;
    }

    public void reset(Player player, GameMode gameMode) {
        // Restore before inventory/teleport cleanup: later failures cannot leak Combo KB.
        if (comboCombatService != null) {
            comboCombatService.restore(player);
        }
        player.closeInventory();
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.setItemOnCursor(null);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.setNoDamageTicks(0);
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setExhaustion(0.0F);
        player.setHealth(player.getMaxHealth());
        player.setExp(0.0F);
        player.setLevel(0);
        player.setTotalExperience(0);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setGameMode(gameMode);
        player.updateInventory();
    }
}
