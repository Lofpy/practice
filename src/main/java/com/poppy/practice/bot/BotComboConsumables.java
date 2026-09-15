package com.poppy.practice.bot;

import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.Item;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.Items;
import net.minecraft.server.v1_8_R3.MobEffect;
import net.minecraft.server.v1_8_R3.MobEffectList;

/** Uses only the finite items supplied by ComboKit; it never manufactures healing. */
final class BotComboConsumables {
    private static final int APPLE_SLOT = 2;
    private static final int CARROT_SLOT = 8;
    private static final int REFRESH_REGENERATION_TICKS = 64;
    private static final int APPLE_RETRY_TICKS = 80;
    private static final int STALLED_USE_GRACE_TICKS = 5;
    private static final int ARMOR_REPLACEMENT_DURABILITY = 10;

    private final EntityPlayer bot;
    private ItemStack consuming;
    private int consumingSlot = -1;
    private int useTicks;
    private int retryTicks;

    BotComboConsumables(EntityPlayer bot) {
        this.bot = bot;
    }

    boolean isEating() {
        return consuming != null;
    }

    /**
     * Starts native use and waits for EntityPlayer.l() to complete its 32-tick
     * countdown. That path consumes one actual item and applies vanilla effects,
     * including PlayerItemConsumeEvent cancellation, exactly as for players.
     */
    boolean tick() {
        if (retryTicks > 0) {
            retryTicks--;
        }
        if (consuming != null) {
            if (!bot.bS() || bot.inventory.itemInHandIndex != consumingSlot
                    || bot.inventory.getItem(consumingSlot) != consuming
                    || consuming.count <= 0) {
                finishUse();
            } else if (++useTicks > consuming.l() + STALLED_USE_GRACE_TICKS) {
                // A cancelled native consume event can leave use active at zero
                // ticks. Release it and retry later, never synthesize its effects.
                bot.bV();
                finishUse();
            } else {
                return true;
            }
        }
        if (retryTicks > 0 || bot.dead || bot.getHealth() <= 0.0F) {
            return false;
        }
        ItemStack apple = bot.inventory.getItem(APPLE_SLOT);
        MobEffect regeneration = bot.getEffect(MobEffectList.REGENERATION);
        int regenerationTicks = regeneration == null || regeneration.getAmplifier() < 4
                ? 0 : regeneration.getDuration();
        if (isEnchantedApple(apple) && shouldEatApple(regenerationTicks,
                bot.getHealth(), bot.getAbsorptionHearts())) {
            beginUse(APPLE_SLOT, apple);
            return true;
        }
        ItemStack carrot = bot.inventory.getItem(CARROT_SLOT);
        if (carrot != null && carrot.count > 0 && carrot.getItem() == Items.GOLDEN_CARROT
                && bot.getBukkitEntity().getFoodLevel() <= 12) {
            beginUse(CARROT_SLOT, carrot);
            return true;
        }
        return false;
    }

    static boolean shouldEatApple(int regenerationTicks, float health, float absorption) {
        return regenerationTicks <= REFRESH_REGENERATION_TICKS
                || (health <= 8.0F && absorption <= 0.0F);
    }

    private static boolean isEnchantedApple(ItemStack stack) {
        return stack != null && stack.count > 0 && stack.getItem() == Items.GOLDEN_APPLE
                && stack.getData() == 1;
    }

    private void beginUse(int slot, ItemStack stack) {
        // Clear sword blocking without ever interrupting an active food use.
        if (bot.bS()) {
            bot.bV();
        }
        consuming = stack;
        consumingSlot = slot;
        useTicks = 0;
        bot.inventory.itemInHandIndex = slot;
        bot.a(stack, stack.l());
    }

    private void finishUse() {
        // Native completion already updates the inventory and effect durations.
        // Switching away also allows a cancelled / externally interrupted use
        // to recover without duplicating or subtracting an item ourselves.
        if (bot.bS()) {
            bot.bV();
        }
        consuming = null;
        consumingSlot = -1;
        useTicks = 0;
        retryTicks = APPLE_RETRY_TICKS;
        bot.inventory.itemInHandIndex = 0;
    }

    /** Moves a better real spare into each armor slot, retaining the worn piece. */
    void replaceWornArmor() {
        for (int armorSlot = 0; armorSlot < bot.inventory.armor.length; armorSlot++) {
            ItemStack worn = bot.inventory.armor[armorSlot];
            if (!needsReplacement(worn)) {
                continue;
            }
            Item expected = armorItem(armorSlot);
            for (int slot = 9; slot < 36; slot++) {
                ItemStack spare = bot.inventory.getItem(slot);
                if (spare == null || spare.count <= 0 || spare.getItem() != expected
                        || needsReplacement(spare)) {
                    continue;
                }
                bot.inventory.armor[armorSlot] = spare;
                bot.inventory.setItem(slot, worn != null && worn.count > 0 ? worn : null);
                bot.inventory.update();
                break;
            }
        }
    }

    static boolean needsReplacement(ItemStack armor) {
        return armor == null || armor.count <= 0
                || armor.j() - armor.getData() <= ARMOR_REPLACEMENT_DURABILITY;
    }

    private static Item armorItem(int armorSlot) {
        switch (armorSlot) {
            case 0: return Items.DIAMOND_BOOTS;
            case 1: return Items.DIAMOND_LEGGINGS;
            case 2: return Items.DIAMOND_CHESTPLATE;
            case 3: return Items.DIAMOND_HELMET;
            default: throw new IllegalArgumentException("Invalid armor slot: " + armorSlot);
        }
    }
}
