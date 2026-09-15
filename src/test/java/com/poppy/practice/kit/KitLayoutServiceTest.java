package com.poppy.practice.kit;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

public final class KitLayoutServiceTest {
    @Test
    public void createsAndAppliesAPlacementOnlyPermutation() {
        ItemStack[] defaults = filledDefaults();
        ItemStack[] edited = cloneContents(defaults);
        ItemStack first = edited[0];
        edited[0] = edited[8];
        edited[8] = first;

        int[] permutation = KitLayoutService.createPermutation(defaults, edited);
        assertNotNull(permutation);
        ItemStack[] reordered = KitLayoutService.reorder(defaults, permutation);
        assertNotNull(reordered);
        assertEquals(Material.GOLDEN_CARROT, reordered[0].getType());
        assertEquals(Material.DIAMOND_SWORD, reordered[8].getType());
    }

    @Test
    public void rejectsChangedAmountsAndMissingItems() {
        ItemStack[] defaults = filledDefaults();
        ItemStack[] changedAmount = cloneContents(defaults);
        changedAmount[1].setAmount(15);
        assertNull(KitLayoutService.createPermutation(defaults, changedAmount));

        ItemStack[] missingItem = cloneContents(defaults);
        missingItem[4] = null;
        assertNull(KitLayoutService.createPermutation(defaults, missingItem));
    }

    @Test
    public void editorPlacesTheHotbarOnItsBottomRow() {
        ItemStack[] storage = filledDefaults();
        ItemStack[] editor = KitLayoutService.toEditorContents(storage);
        assertEquals(Material.DIAMOND_SWORD, editor[27].getType());
        assertEquals(Material.GOLDEN_CARROT, editor[35].getType());

        ItemStack[] restored = KitLayoutService.fromEditorContents(editor);
        assertEquals(Material.DIAMOND_SWORD, restored[0].getType());
        assertEquals(Material.GOLDEN_CARROT, restored[8].getType());
    }

    @Test
    public void editorSwapsWholeStacksWithoutMergingOrChangingAmounts() {
        ItemStack[] defaults = new ItemStack[36];
        defaults[0] = new ItemStack(Material.ENDER_PEARL, 16);
        defaults[1] = new ItemStack(Material.ENDER_PEARL, 8);
        ItemStack[] edited = cloneContents(defaults);

        ItemStack cursor = KitLayoutService.swapWholeStack(edited, 0, null);
        assertNull(edited[0]);
        assertEquals(16, cursor.getAmount());
        cursor = KitLayoutService.swapWholeStack(edited, 1, cursor);
        assertEquals(16, edited[1].getAmount());
        assertEquals(8, cursor.getAmount());
        cursor = KitLayoutService.swapWholeStack(edited, 0, cursor);
        assertNull(cursor);
        assertEquals(8, edited[0].getAmount());
        assertNotNull(KitLayoutService.createPermutation(defaults, edited));
    }

    @Test
    public void editorRejectsOutsideSlotsAndDoesNotShareStackReferences() {
        ItemStack[] contents = new ItemStack[36];
        ItemStack cursor = new ItemStack(Material.ENDER_PEARL, 16);
        org.junit.Assert.assertSame(cursor, KitLayoutService.swapWholeStack(contents, -999, cursor));
        org.junit.Assert.assertSame(cursor, KitLayoutService.swapWholeStack(contents, 36, cursor));
        KitLayoutService.swapWholeStack(contents, 0, cursor);
        cursor.setAmount(1);
        assertEquals(16, contents[0].getAmount());
    }

    private static ItemStack[] filledDefaults() {
        ItemStack[] contents = new ItemStack[36];
        contents[0] = new ItemStack(Material.DIAMOND_SWORD);
        contents[1] = new ItemStack(Material.ENDER_PEARL, 16);
        for (int slot = 2; slot < 35; slot++) {
            contents[slot] = new ItemStack(Material.POTION, 1, (short) (16400 + slot));
        }
        contents[35] = new ItemStack(Material.GOLDEN_CARROT, 64);
        contents[8] = new ItemStack(Material.GOLDEN_CARROT, 64);
        return contents;
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] result = new ItemStack[contents.length];
        for (int slot = 0; slot < contents.length; slot++) {
            result[slot] = contents[slot] == null ? null : contents[slot].clone();
        }
        return result;
    }
}
