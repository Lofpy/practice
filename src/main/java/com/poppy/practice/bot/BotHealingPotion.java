package com.poppy.practice.bot;

import net.minecraft.server.v1_8_R3.Entity;
import net.minecraft.server.v1_8_R3.EntityLiving;
import net.minecraft.server.v1_8_R3.EntityPotion;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.MovingObjectPosition;
import net.minecraft.server.v1_8_R3.World;

/** Exposes an immediate self-impact for emergency airborne healing. */
final class BotHealingPotion extends EntityPotion {
    BotHealingPotion(World world, EntityLiving shooter, ItemStack item) {
        super(world, shooter, item);
    }

    void splashOn(Entity entity) {
        a(new MovingObjectPosition(entity));
    }
}
