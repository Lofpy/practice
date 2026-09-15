package com.poppy.practice.bot;

import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.DispenserRegistry;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.Items;
import net.minecraft.server.v1_8_R3.PlayerInventory;
import net.minecraft.server.v1_8_R3.World;
import net.minecraft.server.v1_8_R3.WorldServer;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.plugin.PluginManager;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Exercises Combo wiring without substituting the existing movement / KB path. */
public class BotComboControllerTest {
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
    public static void restoreServer() throws Exception {
        if (serverField != null) serverField.set(null, previousServer);
    }

    @Test
    public void comboStartsWithNoVirtualHealingPotionsAndEightSecondPearlDelay() throws Exception {
        Fixture fixture = new Fixture();
        assertEquals(0, fixture.controller.getRemainingHealingPotions());
        Field cooldown = BotController.class.getDeclaredField("enderPearlCooldownTicks");
        cooldown.setAccessible(true);
        assertEquals(160, cooldown.getInt(fixture.controller));
    }

    @Test
    public void comboEatingDoesNotAttackOrManufacturePotions() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.player.getHealth()).thenReturn(4.0D);
        for (int tick = 0; tick < 10; tick++) fixture.controller.tick();

        verify(fixture.bot, never()).attack(fixture.target);
        verify(fixture.world, never()).addEntity(any(net.minecraft.server.v1_8_R3.Entity.class));
        assertEquals(0, fixture.controller.getRemainingHealingPotions());
        assertSame(fixture.apples, fixture.bot.inventory.getItem(2));
        assertEquals(64, fixture.apples.count);
        assertEquals(16, fixture.bot.inventory.getItem(1).count);
        verify(fixture.bot, times(1)).a(fixture.apples, 32);
        verify(fixture.bot, times(10)).l();
    }

    @Test
    public void eatingUsesNormalItemMovementSlowdownWithoutStartingSprint() throws Exception {
        Fixture fixture = new Fixture();
        fixture.bot.yaw = 180.0F;
        fixture.controller.tick();

        assertEquals(0.2F, fixture.bot.ba, 0.00001F);
        verify(fixture.bot, never()).setSprinting(true);
        verify(fixture.bot, never()).setExtraKnockback(true);
        verify(fixture.bot, times(1)).l();
        verify(fixture.bot, times(1)).a(0.0D, true);
    }

    @Test
    public void startingComboAppleRetreatClearsPreviousMeleeFacing() throws Exception {
        Fixture fixture = new Fixture();
        fixture.bot.yaw = -180.0F;
        fixture.bot.pitch = 8.0F;
        when(fixture.player.getHealth()).thenReturn(4.0D);
        Field facing = BotController.class.getDeclaredField("directCombatFacing");
        facing.setAccessible(true);
        facing.setBoolean(fixture.controller, true);

        fixture.controller.tick();

        assertFalse(facing.getBoolean(fixture.controller));
        assertEquals(-180.0F, fixture.bot.yaw, 0.00001F);
        assertEquals(8.0F, fixture.bot.pitch, 0.00001F);
        assertEquals(0.2F, fixture.bot.ba, 0.00001F);
        verify(fixture.bot).a(fixture.apples, 32);
        verify(fixture.bot, never()).attack(fixture.target);
    }

    @Test
    public void comboEatingStillRunsContinuousGentleFallBeforeAndAfterNativeGravity() throws Exception {
        Fixture fixture = new Fixture();
        fixture.bot.onGround = false;
        fixture.bot.locY = 3.0D;
        fixture.npc.setVerticalVelocityController(vertical -> -0.12D);
        doAnswer(invocation -> {
            assertEquals(-0.12D, fixture.bot.motY, 0.000001D);
            fixture.bot.locY += fixture.bot.motY;
            fixture.bot.motY = (fixture.bot.motY - 0.08D) * 0.98D;
            return null;
        }).when(fixture.bot).l();

        for (int tick = 0; tick < 10; tick++) {
            fixture.controller.tick();
            assertEquals(-0.12D, fixture.bot.motY, 0.000001D);
        }

        assertEquals(1.8D, fixture.bot.locY, 0.000001D);
        verify(fixture.bot, never()).attack(fixture.target);
        verify(fixture.bot, times(10)).l();
    }

    private static final class Fixture {
        private final EntityPlayer bot = mock(EntityPlayer.class);
        private final EntityPlayer target = mock(EntityPlayer.class);
        private final CraftPlayer player = mock(CraftPlayer.class);
        private final CraftPlayer opponent = mock(CraftPlayer.class);
        private final World world = mock(World.class);
        private final ItemStack apples = new ItemStack(Items.GOLDEN_APPLE, 64, 1);
        private final BotNpc npc;
        private final BotController controller;
        private boolean using;

        private Fixture() throws Exception {
            bot.inventory = new PlayerInventory(bot);
            bot.inventory.setItem(0, new ItemStack(Items.DIAMOND_SWORD));
            bot.inventory.setItem(1, new ItemStack(Items.ENDER_PEARL, 16));
            bot.inventory.setItem(2, apples);
            bot.world = world;
            bot.valid = true;
            bot.onGround = true;
            target.onGround = true;
            target.locZ = 2.0D;
            target.yaw = 180.0F;
            when(bot.getBukkitEntity()).thenReturn(player);
            when(player.getHealth()).thenReturn(20.0D);
            when(opponent.getHandle()).thenReturn(target);
            when(player.getFoodLevel()).thenReturn(20);
            when(player.getActivePotionEffects()).thenReturn(Collections.singletonList(
                    new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1)));
            when(bot.bS()).thenAnswer(invocation -> using);
            doAnswer(invocation -> { using = true; return null; })
                    .when(bot).a(any(ItemStack.class), anyInt());
            doAnswer(invocation -> { using = false; return null; }).when(bot).bV();
            IBlockData ground = mock(IBlockData.class);
            Block block = mock(Block.class);
            block.frictionFactor = 0.6F;
            when(ground.getBlock()).thenReturn(block);
            when(world.getType(anyInt(), anyInt(), anyInt())).thenReturn(ground);
            BotNetworkManager network = new BotNetworkManager();
            network.bind(bot);
            Constructor<BotNpc> constructor = BotNpc.class.getDeclaredConstructor(
                    EntityPlayer.class, WorldServer.class, BotNetworkManager.class);
            constructor.setAccessible(true);
            npc = constructor.newInstance(bot, null, network);
            Field spawned = BotNpc.class.getDeclaredField("spawned");
            spawned.setAccessible(true);
            spawned.setBoolean(npc, true);
            controller = new BotController(npc, opponent,
                    BotSettings.load(new YamlConfiguration()), null, "combo");
        }
    }
}
