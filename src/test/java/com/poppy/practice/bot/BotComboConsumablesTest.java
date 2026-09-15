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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.plugin.PluginManager;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BotComboConsumablesTest {
    private static Field serverField;
    private static Object previousServer;

    @BeforeClass
    public static void initializeNativeItems() throws Exception {
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

    @AfterClass
    public static void restoreBukkitServer() throws Exception {
        if (serverField != null) {
            serverField.set(null, previousServer);
        }
    }

    @Test
    public void comboPearlCooldownIsEightSecondsWithoutChangingOtherKits() {
        BotSettings settings = BotSettings.load(new YamlConfiguration());
        assertEquals(160, BotController.pearlCooldownTicks("combo", settings));
        assertEquals(160, BotController.pearlCooldownTicks("COMBO", settings));
        assertEquals(settings.getEnderPearlCooldownTicks(),
                BotController.pearlCooldownTicks("nodebuff", settings));
        assertEquals(settings.getEnderPearlCooldownTicks(),
                BotController.pearlCooldownTicks("boxing", settings));
    }

    @Test
    public void startsUsingActualEnchantedAppleForItsNativeThirtyTwoTicks() {
        Fixture fixture = new Fixture();
        ItemStack apples = new ItemStack(Items.GOLDEN_APPLE, 64, 1);
        fixture.bot.inventory.setItem(2, apples);

        assertTrue(fixture.consumables.tick());

        assertTrue(fixture.consumables.isEating());
        assertEquals(2, fixture.bot.inventory.itemInHandIndex);
        assertEquals(32, apples.l());
        verify(fixture.bot).a(apples, 32);
        assertSame(apples, fixture.bot.inventory.getItem(2));
        assertEquals(64, apples.count);
        verify(fixture.bot, never()).setHealth(anyFloat());
        verify(fixture.bot, never()).addEffect(any(MobEffect.class));
    }

    @Test
    public void ongoingNativeUseIsNotRestartedOrManuallyConsumed() {
        Fixture fixture = new Fixture();
        ItemStack apples = new ItemStack(Items.GOLDEN_APPLE, 64, 1);
        fixture.bot.inventory.setItem(2, apples);
        fixture.consumables.tick();

        for (int tick = 0; tick < 31; tick++) {
            assertTrue(fixture.consumables.tick());
        }

        verify(fixture.bot, times(1)).a(apples, 32);
        assertEquals(64, apples.count);
        verify(fixture.bot, never()).bV();
    }

    @Test
    public void nativeCompletionRestoresSwordWithoutConsumingASecondApple() {
        Fixture fixture = new Fixture();
        ItemStack apples = new ItemStack(Items.GOLDEN_APPLE, 64, 1);
        fixture.bot.inventory.setItem(2, apples);
        fixture.consumables.tick();
        // Model the native completion performed by EntityPlayer.l().
        apples.count--;
        fixture.using = false;

        assertFalse(fixture.consumables.tick());

        assertFalse(fixture.consumables.isEating());
        assertEquals(0, fixture.bot.inventory.itemInHandIndex);
        assertEquals(63, apples.count);
        verify(fixture.bot, never()).setHealth(anyFloat());
    }

    @Test
    public void absentEmptyOrOrdinaryAppleNeverCreatesAConsumable() {
        Fixture fixture = new Fixture();
        assertFalse(fixture.consumables.tick());
        fixture.bot.inventory.setItem(2, new ItemStack(Items.GOLDEN_APPLE, 0, 1));
        assertFalse(fixture.consumables.tick());
        fixture.bot.inventory.setItem(2, new ItemStack(Items.GOLDEN_APPLE, 64, 0));
        assertFalse(fixture.consumables.tick());
        fixture.bot.inventory.setItem(2, new ItemStack(Items.POTION, 1, 16421));
        assertFalse(fixture.consumables.tick());
        verify(fixture.bot, never()).a(any(ItemStack.class), anyInt());
    }

    @Test
    public void finalAppleCannotBeReplenishedAfterNativeConsumption() {
        Fixture fixture = new Fixture();
        ItemStack lastApple = new ItemStack(Items.GOLDEN_APPLE, 1, 1);
        fixture.bot.inventory.setItem(2, lastApple);
        fixture.consumables.tick();
        lastApple.count = 0;
        fixture.bot.inventory.setItem(2, null);
        fixture.using = false;

        for (int tick = 0; tick < 200; tick++) {
            assertFalse(fixture.consumables.tick());
        }

        assertNull(fixture.bot.inventory.getItem(2));
        verify(fixture.bot, times(1)).a(lastApple, 32);
    }

    @Test
    public void stalledCancelledConsumeReleasesUseAndWaitsBeforeRetrying() {
        Fixture fixture = new Fixture();
        ItemStack apples = new ItemStack(Items.GOLDEN_APPLE, 64, 1);
        fixture.bot.inventory.setItem(2, apples);
        fixture.consumables.tick();
        for (int tick = 0; tick < 37; tick++) {
            assertTrue(fixture.consumables.tick());
        }

        assertFalse(fixture.consumables.tick());
        verify(fixture.bot, times(1)).bV();
        assertEquals(64, apples.count);
        for (int tick = 0; tick < 79; tick++) {
            assertFalse(fixture.consumables.tick());
        }
        assertTrue(fixture.consumables.tick());
        verify(fixture.bot, times(2)).a(apples, 32);
    }

    @Test
    public void refreshesRegenerationBeforeExpiryAndOnlyReeatsEarlyInAnEmergency() {
        assertTrue(BotComboConsumables.shouldEatApple(0, 20.0F, 0.0F));
        assertTrue(BotComboConsumables.shouldEatApple(64, 20.0F, 4.0F));
        assertFalse(BotComboConsumables.shouldEatApple(65, 20.0F, 4.0F));
        assertFalse(BotComboConsumables.shouldEatApple(600, 8.0F, 4.0F));
        assertTrue(BotComboConsumables.shouldEatApple(600, 8.0F, 0.0F));
        assertFalse(BotComboConsumables.shouldEatApple(600, 9.0F, 0.0F));
    }

    @Test
    public void strongFreshRegenerationDoesNotWasteApples() {
        Fixture fixture = new Fixture();
        fixture.bot.inventory.setItem(2, new ItemStack(Items.GOLDEN_APPLE, 64, 1));
        when(fixture.bot.getEffect(MobEffectList.REGENERATION))
                .thenReturn(new MobEffect(MobEffectList.REGENERATION.id, 600, 4));

        assertFalse(fixture.consumables.tick());
        verify(fixture.bot, never()).a(any(ItemStack.class), anyInt());
    }

    @Test
    public void nativeCarrotUseConsumesOnlyExistingFoodWhenHungry() {
        Fixture fixture = new Fixture();
        ItemStack carrots = new ItemStack(Items.GOLDEN_CARROT, 64);
        fixture.bot.inventory.setItem(8, carrots);
        when(fixture.player.getFoodLevel()).thenReturn(12);

        assertTrue(fixture.consumables.tick());

        assertEquals(8, fixture.bot.inventory.itemInHandIndex);
        verify(fixture.bot).a(carrots, 32);
        assertEquals(64, carrots.count);
        assertNull(fixture.bot.inventory.getItem(2));
    }

    @Test
    public void armorReplacementMovesTheRealSpareAndKeepsTheWornPiece() {
        Fixture fixture = new Fixture();
        ItemStack worn = new ItemStack(Items.DIAMOND_HELMET);
        worn.setData(worn.j() - 10);
        ItemStack spare = new ItemStack(Items.DIAMOND_HELMET);
        fixture.bot.inventory.armor[3] = worn;
        fixture.bot.inventory.setItem(18, spare);

        fixture.consumables.replaceWornArmor();

        assertSame(spare, fixture.bot.inventory.armor[3]);
        assertSame(worn, fixture.bot.inventory.getItem(18));
        assertEquals(worn.j() - 10, worn.getData());
        assertEquals(0, spare.getData());
        fixture.consumables.replaceWornArmor();
        assertSame(spare, fixture.bot.inventory.armor[3]);
        assertSame(worn, fixture.bot.inventory.getItem(18));
    }

    @Test
    public void brokenArmorIsReplacedOnlyOnceFromTheMatchingStoredSlot() {
        Fixture fixture = new Fixture();
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
        ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        ItemStack legs = new ItemStack(Items.DIAMOND_LEGGINGS);
        ItemStack boots = new ItemStack(Items.DIAMOND_BOOTS);
        fixture.bot.inventory.setItem(18, helmet);
        fixture.bot.inventory.setItem(19, chest);
        fixture.bot.inventory.setItem(20, legs);
        fixture.bot.inventory.setItem(21, boots);

        fixture.consumables.replaceWornArmor();

        assertSame(helmet, fixture.bot.inventory.armor[3]);
        assertSame(chest, fixture.bot.inventory.armor[2]);
        assertSame(legs, fixture.bot.inventory.armor[1]);
        assertSame(boots, fixture.bot.inventory.armor[0]);
        for (int slot = 18; slot <= 21; slot++) {
            assertNull(fixture.bot.inventory.getItem(slot));
        }
        fixture.bot.inventory.armor[3] = null;
        fixture.consumables.replaceWornArmor();
        assertNull(fixture.bot.inventory.armor[3]);
    }

    @Test
    public void healthyArmorIsNotRepairedAndWornSpareIsNotRecycled() {
        Fixture fixture = new Fixture();
        ItemStack worn = new ItemStack(Items.DIAMOND_BOOTS);
        worn.setData(worn.j() - 11);
        ItemStack spare = new ItemStack(Items.DIAMOND_BOOTS);
        fixture.bot.inventory.armor[0] = worn;
        fixture.bot.inventory.setItem(21, spare);
        fixture.consumables.replaceWornArmor();
        assertSame(worn, fixture.bot.inventory.armor[0]);
        assertSame(spare, fixture.bot.inventory.getItem(21));
        spare.setData(spare.j() - 9);
        worn.setData(worn.j() - 1);
        fixture.consumables.replaceWornArmor();
        assertSame(worn, fixture.bot.inventory.armor[0]);
        assertSame(spare, fixture.bot.inventory.getItem(21));
    }

    private static final class Fixture {
        private final EntityPlayer bot = mock(EntityPlayer.class);
        private final CraftPlayer player = mock(CraftPlayer.class);
        private final BotComboConsumables consumables;
        private boolean using;

        private Fixture() {
            bot.inventory = new PlayerInventory(bot);
            when(bot.getBukkitEntity()).thenReturn(player);
            when(player.getHealth()).thenReturn(20.0D);
            when(player.getFoodLevel()).thenReturn(20);
            when(bot.bS()).thenAnswer(invocation -> using);
            doAnswer(invocation -> {
                using = true;
                return null;
            }).when(bot).a(any(ItemStack.class), anyInt());
            doAnswer(invocation -> {
                using = false;
                return null;
            }).when(bot).bV();
            consumables = new BotComboConsumables(bot);
        }
    }
}
