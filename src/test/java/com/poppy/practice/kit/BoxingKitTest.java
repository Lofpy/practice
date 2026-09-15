package com.poppy.practice.kit;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BoxingKitTest {
    @Test
    public void registersBoxingImmediatelyAfterNoDebuff() {
        KitManager manager = new KitManager();
        List<Kit> kits = new ArrayList<Kit>(manager.all());

        assertEquals(3, kits.size());
        assertEquals("nodebuff", kits.get(0).getId());
        assertEquals("boxing", kits.get(1).getId());
        assertTrue(manager.get("BOXING") instanceof BoxingKit);
        assertEquals(Material.DIAMOND_SWORD, kits.get(1).getIcon());
        assertEquals(0, kits.get(1).getIconDurability());
    }

    @Test
    public void providesExactlyOneSwordAndNoOtherItems() {
        BoxingKit kit = new BoxingKit();
        ItemStack[] contents = kit.createInventoryContents();

        assertEquals(36, contents.length);
        assertEquals(Material.DIAMOND_SWORD, contents[0].getType());
        assertEquals(1, contents[0].getAmount());
        for (int slot = 1; slot < contents.length; slot++) {
            assertNull(contents[slot]);
        }

        ItemStack[] another = kit.createInventoryContents();
        assertNotSame(contents, another);
        assertNotSame(contents[0], another[0]);
    }

    @Test
    public void appliesPermanentSpeedTwoAndClearsArmor() {
        final ItemStack[][] armor = new ItemStack[1][];
        final ItemStack[][] contents = new ItemStack[1][];
        final PotionEffect[] speed = new PotionEffect[1];
        final boolean[] inventoryUpdated = {false};
        PlayerInventory inventory = Mockito.mock(PlayerInventory.class, (Answer<Object>) invocation -> {
                    Method method = invocation.getMethod();
                    Object[] arguments = invocation.getArguments();
                    if ("setArmorContents".equals(method.getName())) {
                        armor[0] = (ItemStack[]) arguments[0];
                        return null;
                    }
                    if ("setContents".equals(method.getName())) {
                        contents[0] = (ItemStack[]) arguments[0];
                        return null;
                    }
                    throw new AssertionError("Unexpected inventory call: " + method.getName());
                });
        Player player = Mockito.mock(Player.class, (Answer<Object>) invocation -> {
                    Method method = invocation.getMethod();
                    Object[] arguments = invocation.getArguments();
                    if ("getInventory".equals(method.getName())) {
                        return inventory;
                    }
                    if ("addPotionEffect".equals(method.getName())) {
                        speed[0] = (PotionEffect) arguments[0];
                        assertEquals(Boolean.TRUE, arguments[1]);
                        return true;
                    }
                    if ("updateInventory".equals(method.getName())) {
                        inventoryUpdated[0] = true;
                        return null;
                    }
                    throw new AssertionError("Unexpected player call: " + method.getName());
                });

        new BoxingKit().apply(player);

        assertEquals(4, armor[0].length);
        for (ItemStack item : armor[0]) {
            assertNull(item);
        }
        assertEquals(Material.DIAMOND_SWORD, contents[0][0].getType());
        assertEquals(1, speed[0].getType().getId());
        assertEquals(1, speed[0].getAmplifier());
        assertEquals(Integer.MAX_VALUE, speed[0].getDuration());
        assertTrue(inventoryUpdated[0]);
    }

    @Test
    public void editorCanMoveTheSwordButCannotDuplicateOrRemoveIt() {
        ItemStack[] defaults = new BoxingKit().createInventoryContents();
        ItemStack[] edited = new ItemStack[36];
        edited[8] = defaults[0].clone();
        int[] permutation = KitLayoutService.createPermutation(defaults, edited);
        ItemStack[] saved = KitLayoutService.reorder(defaults, permutation);

        assertNull(saved[0]);
        assertEquals(Material.DIAMOND_SWORD, saved[8].getType());
        edited[7] = defaults[0].clone();
        assertNull(KitLayoutService.createPermutation(defaults, edited));
        assertNull(KitLayoutService.createPermutation(defaults, new ItemStack[36]));
    }
}
