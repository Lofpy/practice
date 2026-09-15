package com.poppy.practice.result;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public final class MatchCombatStatistics {
    private MatchCombatStatistics() {
    }

    public static void recordMeleeHit(MatchParticipantStats attackerStats,
                                      MatchParticipantStats victimStats,
                                      Player attacker, Player victim) {
        if (attackerStats == null || victimStats == null || attacker == null || victim == null) {
            return;
        }
        attackerStats.recordMeleeHit(isCritical(attacker));
        if (victim.isBlocking()) {
            victimStats.recordGuard();
        }
    }

    static boolean isCritical(Player attacker) {
        if (attacker.getFallDistance() <= 0.0F || attacker.isOnGround()
                || attacker.isInsideVehicle()
                || attacker.hasPotionEffect(PotionEffectType.BLINDNESS)) {
            return false;
        }
        Material feet = attacker.getLocation().getBlock().getType();
        Material eyes = attacker.getEyeLocation().getBlock().getType();
        return !isLiquid(feet) && !isLiquid(eyes) && !isClimbable(feet);
    }

    private static boolean isLiquid(Material material) {
        return material == Material.WATER || material == Material.STATIONARY_WATER
                || material == Material.LAVA || material == Material.STATIONARY_LAVA;
    }

    private static boolean isClimbable(Material material) {
        return material == Material.LADDER || material == Material.VINE;
    }
}
