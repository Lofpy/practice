package com.poppy.practice.combat;

import net.minecraft.server.v1_8_R3.DamageSource;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

/** Applies vanilla-strength ender pearl impact damage to a server-side bot. */
public final class EnderPearlDamage {
    public static final float DAMAGE = 5.0F;

    private EnderPearlDamage() {
    }

    public static boolean apply(Player victim) {
        if (victim == null || victim.isDead() || victim.getHealth() <= 0.0D) {
            return false;
        }
        EntityPlayer handle = ((CraftPlayer) victim).getHandle();
        handle.fallDistance = 0.0F;
        handle.invulnerableTicks = 0;
        handle.noDamageTicks = 0;
        handle.lastDamage = 0.0F;
        return handle.damageEntity(DamageSource.FALL, DAMAGE);
    }
}
