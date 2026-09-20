package com.poppy.practice.bot;

import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.ItemPotion;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.Items;
import net.minecraft.server.v1_8_R3.MobEffect;
import net.minecraft.server.v1_8_R3.MobEffectList;

import java.util.List;

/** Finite NoDebuff inventory; native item use owns consumption and effects. */
final class BotNodebuffConsumables {
    static final int REFILL_TICKS = 10;
    private static final int SPEED_REFRESH_TICKS = 40;
    private static final int RETRY_TICKS = 20;
    private static final int STALLED_USE_GRACE_TICKS = 5;
    private final EntityPlayer bot;
    private ItemStack consuming;
    private int consumingSlot = -1;
    private int useTicks;
    private int retryTicks;
    private int refillTicks;

    BotNodebuffConsumables(EntityPlayer bot, int maximumHealingPotions) {
        this.bot = bot;
        // Honor the configured cap, without creating items missing from the kit.
        int remaining = Math.max(0, maximumHealingPotions);
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = bot.inventory.getItem(slot);
            if (isHealingPotion(stack)) {
                int retained = Math.min(remaining, stack.count);
                remaining -= retained;
                if (retained == 0) bot.inventory.setItem(slot, null);
                else stack.count = retained;
            }
        }
    }

    int countHealingPotions() {
        return countHealingPotions(0, 36);
    }

    int countHotbarHealingPotions() {
        return countHealingPotions(0, 9);
    }

    private int countHealingPotions(int first, int end) {
        int count = 0;
        for (int slot = first; slot < end; slot++) {
            ItemStack stack = bot.inventory.getItem(slot);
            if (isHealingPotion(stack)) count += stack.count;
        }
        return count;
    }

    int healingSlot() {
        for (int slot = 0; slot < 9; slot++) {
            if (isHealingPotion(bot.inventory.getItem(slot))) return slot;
        }
        return -1;
    }

    void consumeThrownHealingPotion(int slot) {
        ItemStack stack = bot.inventory.getItem(slot);
        if (!isHealingPotion(stack)) return;
        if (--stack.count <= 0) bot.inventory.setItem(slot, null);
    }

    boolean beginRefill() {
        if (refillTicks > 0) return true;
        if (healingSlot() >= 0 || countHealingPotions(9, 36) == 0
                || refillDestination() < 0) return false;
        interruptUse();
        refillTicks = REFILL_TICKS;
        return true;
    }

    /** Returns true for all ten inventory-management ticks, including the last. */
    boolean tickRefill() {
        if (refillTicks == 0) return false;
        if (--refillTicks == 0) {
            for (int source = 9; source < 36; source++) {
                ItemStack potion = bot.inventory.getItem(source);
                if (!isHealingPotion(potion)) continue;
                int destination = refillDestination();
                if (destination < 0) break;
                ItemStack displaced = bot.inventory.getItem(destination);
                bot.inventory.setItem(destination, potion);
                bot.inventory.setItem(source, displaced);
            }
            bot.inventory.update();
        }
        return true;
    }

    private int refillDestination() {
        for (int slot = 2; slot < 9; slot++) {
            ItemStack stack = bot.inventory.getItem(slot);
            if (stack == null || stack.count <= 0 || stack.getItem() == Items.GLASS_BOTTLE) {
                return slot;
            }
        }
        return -1;
    }

    boolean isUsing() {
        return consuming != null;
    }

    void interruptUse() {
        if (consuming != null) finishUse();
    }

    /** Healing takes priority, including interrupting an unfinished drink/meal. */
    boolean tickUse(boolean healingNeeded) {
        if (retryTicks > 0) retryTicks--;
        if (healingNeeded) {
            interruptUse();
            return false;
        }
        if (consuming != null) {
            if (!bot.bS() || bot.inventory.itemInHandIndex != consumingSlot
                    || bot.inventory.getItem(consumingSlot) != consuming || consuming.count <= 0
                    || ++useTicks > consuming.l() + STALLED_USE_GRACE_TICKS) {
                finishUse();
            } else {
                return true;
            }
        }
        if (retryTicks > 0 || bot.dead || bot.getHealth() <= 0.0F) return false;
        int food = bot.getBukkitEntity().getFoodLevel();
        if (food <= 14 && beginFood()) return true;
        MobEffect speed = bot.getEffect(MobEffectList.FASTER_MOVEMENT);
        if (speed == null || speed.getAmplifier() < 1 || speed.getDuration() <= SPEED_REFRESH_TICKS) {
            for (int slot = 0; slot < 36; slot++) {
                if (isSpeedTwoPotion(bot.inventory.getItem(slot))) {
                    return beginUse(slot, 3);
                }
            }
        }
        return false;
    }

    private boolean beginFood() {
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = bot.inventory.getItem(slot);
            if (stack != null && stack.count > 0 && stack.getItem() == Items.GOLDEN_CARROT) {
                return beginUse(slot, 8);
            }
        }
        return false;
    }

    private boolean beginUse(int source, int preferredHotbarSlot) {
        int slot = source < 9 ? source : preferredHotbarSlot;
        ItemStack stack = bot.inventory.getItem(source);
        if (slot != source) {
            bot.inventory.setItem(source, bot.inventory.getItem(slot));
            bot.inventory.setItem(slot, stack);
        }
        if (bot.bS()) bot.bV();
        consuming = stack;
        consumingSlot = slot;
        useTicks = 0;
        bot.inventory.itemInHandIndex = slot;
        // The native 32-tick countdown also publishes drinking/eating metadata.
        bot.a(stack, stack.l());
        return true;
    }

    private void finishUse() {
        if (bot.bS()) bot.bV();
        ItemStack remainder = consumingSlot < 0 ? null : bot.inventory.getItem(consumingSlot);
        // Native drinking replaces its potion with a bottle. Never remove the
        // unconsumed potion when healing or another action interrupts the use.
        if (remainder != null && remainder.getItem() == Items.GLASS_BOTTLE) {
            if (--remainder.count <= 0) bot.inventory.setItem(consumingSlot, null);
        }
        consuming = null;
        consumingSlot = -1;
        useTicks = 0;
        retryTicks = RETRY_TICKS;
        bot.inventory.itemInHandIndex = 0;
    }

    static boolean isHealingPotion(ItemStack stack) {
        return hasEffect(stack, MobEffectList.HEAL.id, true, 1);
    }

    static boolean isSpeedTwoPotion(ItemStack stack) {
        return hasEffect(stack, MobEffectList.FASTER_MOVEMENT.id, false, 1);
    }

    private static boolean hasEffect(ItemStack stack, int effectId, boolean splash, int amplifier) {
        if (stack == null || stack.count <= 0 || stack.getItem() != Items.POTION
                || ItemPotion.f(stack.getData()) != splash) return false;
        List<MobEffect> effects = ((ItemPotion) stack.getItem()).h(stack);
        if (effects != null) {
            for (MobEffect effect : effects) {
                if (effect.getEffectId() == effectId && effect.getAmplifier() >= amplifier) return true;
            }
        }
        return false;
    }
}
