package com.poppy.practice.bot;

import net.minecraft.server.v1_8_R3.DispenserRegistry;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.Items;
import net.minecraft.server.v1_8_R3.MobEffect;
import net.minecraft.server.v1_8_R3.MobEffectList;
import net.minecraft.server.v1_8_R3.PlayerInventory;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.plugin.PluginManager;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.Collections;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BotNodebuffConsumablesTest {
    private static Field serverField;
    private static Object previousServer;

    @BeforeClass public static void initializeNativeItems() throws Exception {
        DispenserRegistry.c();
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        Server server = mock(Server.class);
        PluginManager plugins = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(plugins);
        when(plugins.getDefaultPermissions(anyBoolean())).thenReturn(Collections.emptySet());
        serverField.set(null, server);
    }

    @AfterClass public static void restoreServer() throws Exception {
        serverField.set(null, previousServer);
    }

    @Test public void speedStartsNativeThirtyTwoTickUseWithoutInstantEffects() {
        Fixture f = new Fixture();
        ItemStack speed = speed();
        f.bot.inventory.setItem(3, speed);
        assertTrue(f.consumables.tickUse(false));
        assertTrue(f.consumables.isUsing());
        assertEquals(3, f.bot.inventory.itemInHandIndex);
        verify(f.bot).a(speed, 32);
        verify(f.bot, never()).addEffect(any(MobEffect.class));
        assertEquals(1, speed.count);
        for (int tick = 0; tick < 31; tick++) assertTrue(f.consumables.tickUse(false));
        verify(f.bot, times(1)).a(speed, 32);
    }

    @Test public void nativeCompletionDiscardsEmptyBottleAndDoesNotGenerateAnotherDrink() {
        Fixture f = new Fixture();
        f.bot.inventory.setItem(3, speed());
        f.consumables.tickUse(false);
        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
        f.bot.inventory.setItem(3, bottle);
        f.using = false;
        for (int tick = 0; tick < 100; tick++) assertFalse(f.consumables.tickUse(false));
        assertFalse(f.consumables.isUsing());
        assertNull(f.bot.inventory.getItem(3));
        assertEquals(0, f.bot.inventory.itemInHandIndex);
        verify(f.bot, times(1)).a(any(ItemStack.class), eq(32));
    }

    @Test public void completionRemovesOnlyOneBottleFromTheUsedSlot() {
        Fixture f = new Fixture();
        f.bot.inventory.setItem(3, speed());
        f.consumables.tickUse(false);
        ItemStack bottles = new ItemStack(Items.GLASS_BOTTLE, 3);
        ItemStack unrelatedBottle = new ItemStack(Items.GLASS_BOTTLE);
        f.bot.inventory.setItem(3, bottles);
        f.bot.inventory.setItem(12, unrelatedBottle);
        f.using = false;
        assertFalse(f.consumables.tickUse(false));
        assertSame(bottles, f.bot.inventory.getItem(3));
        assertEquals(2, bottles.count);
        assertSame(unrelatedBottle, f.bot.inventory.getItem(12));
        assertFalse(f.consumables.tickUse(false));
        assertEquals(2, bottles.count);
    }

    @Test public void speedRefreshNeedsAnExpiredOrNearlyExpiredEffect() {
        Fixture f = new Fixture();
        f.bot.inventory.setItem(3, speed());
        when(f.bot.getEffect(MobEffectList.FASTER_MOVEMENT))
                .thenReturn(new MobEffect(MobEffectList.FASTER_MOVEMENT.id, 41, 1));
        assertFalse(f.consumables.tickUse(false));
        when(f.bot.getEffect(MobEffectList.FASTER_MOVEMENT))
                .thenReturn(new MobEffect(MobEffectList.FASTER_MOVEMENT.id, 40, 1));
        assertTrue(f.consumables.tickUse(false));
    }

    @Test public void storedSpeedSwapsWithExistingHotbarItemInsteadOfDeletingIt() {
        Fixture f = new Fixture();
        ItemStack spare = speed();
        ItemStack healing = heal();
        f.bot.inventory.setItem(3, healing);
        f.bot.inventory.setItem(9, spare);
        assertTrue(f.consumables.tickUse(false));
        assertSame(spare, f.bot.inventory.getItem(3));
        assertSame(healing, f.bot.inventory.getItem(9));
        verify(f.bot).a(spare, 32);
    }

    @Test public void hungerDoesNotDelaySpeedDrinkingOrConsumeCarrots() {
        Fixture f = new Fixture();
        ItemStack food = new ItemStack(Items.GOLDEN_CARROT, 64);
        ItemStack speed = speed();
        f.bot.inventory.setItem(8, food);
        f.bot.inventory.setItem(3, speed);
        when(f.player.getFoodLevel()).thenReturn(14);
        assertTrue(f.consumables.tickUse(false));
        verify(f.bot).a(speed, 32);
        verify(f.bot, never()).a(eq(food), anyInt());
        assertEquals(64, food.count);
        assertSame(food, f.bot.inventory.getItem(8));
        verify(f.player, never()).setFoodLevel(anyInt());
    }

    @Test public void hungryBotNeverStartsEatingEvenWithoutSpeedPotions() {
        Fixture f = new Fixture();
        ItemStack food = new ItemStack(Items.GOLDEN_CARROT, 64);
        f.bot.inventory.setItem(8, food);
        assertFalse(f.consumables.tickUse(false));
        when(f.player.getFoodLevel()).thenReturn(6);
        for (int tick = 0; tick < 100; tick++) assertFalse(f.consumables.tickUse(false));
        assertSame(food, f.bot.inventory.getItem(8));
        assertEquals(64, food.count);
        f.bot.inventory.setItem(8, null);
        assertFalse(f.consumables.tickUse(false));
        verify(f.bot, never()).a(any(ItemStack.class), anyInt());
    }

    @Test public void healingInterruptsAnUnfinishedDrinkWithoutConsumingIt() {
        Fixture f = new Fixture();
        ItemStack speed = speed();
        f.bot.inventory.setItem(3, speed);
        assertTrue(f.consumables.tickUse(false));
        assertFalse(f.consumables.tickUse(true));
        assertFalse(f.consumables.isUsing());
        verify(f.bot).bV();
        assertSame(speed, f.bot.inventory.getItem(3));
        assertEquals(1, speed.count);
        assertEquals(0, f.bot.inventory.itemInHandIndex);
    }

    @Test public void aCancelledNativeUseTimesOutAndWaitsBeforeRetrying() {
        Fixture f = new Fixture();
        ItemStack speed = speed();
        f.bot.inventory.setItem(3, speed);
        f.consumables.tickUse(false);
        for (int tick = 0; tick < 37; tick++) assertTrue(f.consumables.tickUse(false));
        assertFalse(f.consumables.tickUse(false));
        for (int tick = 0; tick < 19; tick++) assertFalse(f.consumables.tickUse(false));
        assertTrue(f.consumables.tickUse(false));
        assertEquals(1, speed.count);
        verify(f.bot, times(2)).a(speed, 32);
    }

    @Test public void refillTakesExactlyTenTicksAndMovesOnlyRealReservePotions() {
        Fixture f = new Fixture();
        ItemStack first = heal();
        ItemStack second = heal();
        f.bot.inventory.setItem(11, first);
        f.bot.inventory.setItem(12, second);
        assertTrue(f.consumables.beginRefill());
        for (int tick = 0; tick < 9; tick++) {
            assertTrue(f.consumables.tickRefill());
            assertEquals(0, f.consumables.countHotbarHealingPotions());
            assertSame(first, f.bot.inventory.getItem(11));
        }
        assertTrue(f.consumables.tickRefill());
        assertFalse(f.consumables.tickRefill());
        assertEquals(2, f.consumables.countHotbarHealingPotions());
        assertEquals(2, f.consumables.countHealingPotions());
        assertSame(first, f.bot.inventory.getItem(2));
        assertSame(second, f.bot.inventory.getItem(3));
        assertNull(f.bot.inventory.getItem(11));
        assertNull(f.bot.inventory.getItem(12));
    }

    @Test public void refillNeverDeletesFoodPearlsDrinksOrBottles() {
        Fixture f = new Fixture();
        ItemStack fire = new ItemStack(Items.POTION, 1, 8195);
        ItemStack speed = speed();
        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
        ItemStack food = new ItemStack(Items.GOLDEN_CARROT, 64);
        ItemStack pearl = new ItemStack(Items.ENDER_PEARL, 16);
        ItemStack healing = heal();
        f.bot.inventory.setItem(1, pearl);
        f.bot.inventory.setItem(2, fire);
        f.bot.inventory.setItem(3, speed);
        f.bot.inventory.setItem(4, bottle);
        f.bot.inventory.setItem(8, food);
        f.bot.inventory.setItem(11, healing);
        assertTrue(f.consumables.beginRefill());
        for (int tick = 0; tick < 10; tick++) f.consumables.tickRefill();
        assertSame(pearl, f.bot.inventory.getItem(1));
        assertSame(fire, f.bot.inventory.getItem(2));
        assertSame(speed, f.bot.inventory.getItem(3));
        assertSame(healing, f.bot.inventory.getItem(4));
        assertSame(food, f.bot.inventory.getItem(8));
        assertSame(bottle, f.bot.inventory.getItem(11));
    }

    @Test public void refillNeedsEmptyHotbarHealingAndNonemptyReserve() {
        Fixture f = new Fixture();
        assertFalse(f.consumables.beginRefill());
        f.bot.inventory.setItem(4, heal());
        f.bot.inventory.setItem(11, heal());
        assertFalse(f.consumables.beginRefill());
        assertFalse(f.consumables.tickRefill());
    }

    @Test public void thrownPotionsConsumeOneActualHotbarItemAndCannotReappear() {
        Fixture f = new Fixture();
        f.bot.inventory.setItem(4, heal());
        f.consumables.consumeThrownHealingPotion(4);
        assertNull(f.bot.inventory.getItem(4));
        assertEquals(0, f.consumables.countHealingPotions());
        assertEquals(-1, f.consumables.healingSlot());
        assertFalse(f.consumables.beginRefill());
        f.consumables.consumeThrownHealingPotion(4);
        assertEquals(0, f.consumables.countHealingPotions());
    }

    @Test public void configuredCountCapsTheRealKitWithoutManufacturingMore() {
        Fixture f = new Fixture();
        f.bot.inventory.setItem(4, heal());
        f.bot.inventory.setItem(11, heal());
        f.bot.inventory.setItem(12, heal());
        BotNodebuffConsumables limited = new BotNodebuffConsumables(f.bot, 2);
        assertEquals(2, limited.countHealingPotions());
        assertNotNull(f.bot.inventory.getItem(4));
        assertNull(f.bot.inventory.getItem(12));
        assertEquals(2, new BotNodebuffConsumables(f.bot, 36).countHealingPotions());
        assertEquals(0, new BotNodebuffConsumables(f.bot, 0).countHealingPotions());
    }

    private static ItemStack heal() { return new ItemStack(Items.POTION, 1, 16421); }
    private static ItemStack speed() { return new ItemStack(Items.POTION, 1, 8226); }

    private static final class Fixture {
        final EntityPlayer bot = mock(EntityPlayer.class);
        final CraftPlayer player = mock(CraftPlayer.class);
        final BotNodebuffConsumables consumables;
        boolean using;

        Fixture() {
            bot.inventory = new PlayerInventory(bot);
            when(bot.getBukkitEntity()).thenReturn(player);
            when(player.getHealth()).thenReturn(20.0D);
            when(player.getFoodLevel()).thenReturn(20);
            when(bot.bS()).thenAnswer(invocation -> using);
            doAnswer(invocation -> { using = true; return null; })
                    .when(bot).a(any(ItemStack.class), anyInt());
            doAnswer(invocation -> { using = false; return null; }).when(bot).bV();
            consumables = new BotNodebuffConsumables(bot, 29);
        }
    }
}
