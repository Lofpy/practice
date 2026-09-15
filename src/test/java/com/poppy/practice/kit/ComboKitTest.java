package com.poppy.practice.kit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemFactory;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionType;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("deprecation")
public class ComboKitTest {
    private static Field serverField;
    private static Object previousServer;

    @BeforeClass
    public static void installItemFactory() throws Exception {
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        Server server = mock(Server.class);
        when(server.getItemFactory()).thenReturn(CraftItemFactory.instance());
        serverField.set(null, server);
    }

    @AfterClass
    public static void restoreServer() throws Exception {
        if (serverField != null) serverField.set(null, previousServer);
    }

    @Test
    public void registersComboAfterExistingKitsWithoutChangingTheirOrder() {
        KitManager manager = new KitManager();
        List<Kit> kits = new ArrayList<Kit>(manager.all());

        assertEquals(3, kits.size());
        assertEquals("nodebuff", kits.get(0).getId());
        assertEquals("boxing", kits.get(1).getId());
        assertEquals("combo", kits.get(2).getId());
        assertTrue(manager.get("COMBO") instanceof ComboKit);
        assertEquals("Combo", kits.get(2).getDisplayName());
    }

    @Test
    public void usesTheLegacyEnchantedGoldenAppleIcon() {
        ComboKit kit = new ComboKit();

        assertEquals(Material.GOLDEN_APPLE, kit.getIcon());
        assertEquals(1, kit.getIconDurability());
    }

    @Test
    public void providesTheExactRequestedConsumablesAndNoHealingSplashPotions() {
        ItemStack[] contents = new ComboKit().createInventoryContents();

        assertEquals(36, contents.length);
        assertEquals(16, total(contents, Material.ENDER_PEARL));
        assertEquals(64, total(contents, Material.GOLDEN_APPLE));
        assertEquals(64, total(contents, Material.GOLDEN_CARROT));
        assertEquals(6, total(contents, Material.POTION));
        assertEquals(1, contents[2].getDurability());
        for (ItemStack item : contents) {
            if (item == null || item.getType() != Material.POTION) continue;
            // Decode the legacy wire bits directly: Potion.fromItemStack also
            // queries the live potion-effect registry, absent in this test JVM.
            int data = item.getDurability();
            assertEquals(PotionType.SPEED.getDamageValue(), data & 0x0F);
            assertEquals(2, ((data & 0x20) >> 5) + 1);
            assertEquals(0, data & 0x4000);
            assertEquals(1, item.getAmount());
        }
    }

    @Test
    public void swordHasOnlySharpnessFive() {
        ItemStack sword = new ComboKit().createInventoryContents()[0];

        assertEquals(Material.DIAMOND_SWORD, sword.getType());
        assertEquals(1, sword.getAmount());
        assertEquals(5, sword.getEnchantmentLevel(Enchantment.DAMAGE_ALL));
        assertEquals(1, sword.getEnchantments().size());
        assertEquals(0, sword.getDurability());
    }

    @Test
    public void storageContainsExactlyOneSpareSetWithProtectionFourAndUnbreakingThree() {
        ItemStack[] contents = new ComboKit().createInventoryContents();
        Material[] armor = armorMaterials();

        for (int index = 0; index < armor.length; index++) {
            assertEquals(1, total(contents, armor[index]));
            assertArmor(contents[18 + index], armor[index]);
        }
        int occupied = 0;
        for (ItemStack item : contents) if (item != null) occupied++;
        assertEquals(14, occupied);
    }

    @Test
    public void applyEquipsOneSetAndKeepsOneSpareSetAsSeparateItems() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);

        new ComboKit().apply(player);

        ArgumentCaptor<ItemStack> helmet = ArgumentCaptor.forClass(ItemStack.class);
        ArgumentCaptor<ItemStack> chestplate = ArgumentCaptor.forClass(ItemStack.class);
        ArgumentCaptor<ItemStack> leggings = ArgumentCaptor.forClass(ItemStack.class);
        ArgumentCaptor<ItemStack> boots = ArgumentCaptor.forClass(ItemStack.class);
        ArgumentCaptor<ItemStack[]> storage = ArgumentCaptor.forClass(ItemStack[].class);
        verify(inventory).setHelmet(helmet.capture());
        verify(inventory).setChestplate(chestplate.capture());
        verify(inventory).setLeggings(leggings.capture());
        verify(inventory).setBoots(boots.capture());
        verify(inventory).setContents(storage.capture());
        verify(player).updateInventory();
        ItemStack[] equipped = {helmet.getValue(), chestplate.getValue(), leggings.getValue(), boots.getValue()};
        Material[] armor = armorMaterials();
        for (int index = 0; index < armor.length; index++) {
            assertArmor(equipped[index], armor[index]);
            assertArmor(storage.getValue()[18 + index], armor[index]);
            assertNotSame(equipped[index], storage.getValue()[18 + index]);
            assertEquals(2, total(equipped, armor[index]) + total(storage.getValue(), armor[index]));
        }
    }

    @Test
    public void eachLoadoutOwnsItsItemStacks() {
        ComboKit kit = new ComboKit();
        ItemStack[] first = kit.createInventoryContents();
        ItemStack[] second = kit.createInventoryContents();

        assertNotSame(first, second);
        for (int slot = 0; slot < first.length; slot++) {
            if (first[slot] != null) assertNotSame(first[slot], second[slot]);
        }
        first[2].setAmount(1);
        first[18].setDurability((short) 10);
        assertEquals(64, second[2].getAmount());
        assertEquals(0, second[18].getDurability());
    }

    @Test
    public void editorCanRearrangeAllConsumablesAndSpareArmorWithoutChangingThem() {
        ItemStack[] defaults = new ComboKit().createInventoryContents();
        ItemStack[] edited = new ItemStack[36];
        for (int slot = 0; slot < defaults.length; slot++) {
            edited[35 - slot] = defaults[slot] == null ? null : defaults[slot].clone();
        }

        int[] permutation = KitLayoutService.createPermutation(defaults, edited);
        assertNotNull(permutation);
        ItemStack[] restored = KitLayoutService.reorder(defaults, permutation);
        assertNotNull(restored);
        for (int slot = 0; slot < edited.length; slot++) {
            assertEquals(edited[slot], restored[slot]);
        }
        assertEquals(6, total(restored, Material.POTION));
        for (Material armor : armorMaterials()) assertEquals(1, total(restored, armor));
    }

    @Test
    public void editorRejectsMissingDuplicatedOrModifiedConsumables() {
        ItemStack[] defaults = new ComboKit().createInventoryContents();
        ItemStack[] edited = new ComboKit().createInventoryContents();
        edited[2].setAmount(63);
        assertNull(KitLayoutService.createPermutation(defaults, edited));
        edited = new ComboKit().createInventoryContents();
        edited[9] = null;
        assertNull(KitLayoutService.createPermutation(defaults, edited));
        edited = new ComboKit().createInventoryContents();
        edited[4] = edited[3].clone();
        assertNull(KitLayoutService.createPermutation(defaults, edited));
    }

    @Test
    public void editorRejectsMissingDuplicatedOrWeakenedSpareArmor() {
        ItemStack[] defaults = new ComboKit().createInventoryContents();
        ItemStack[] edited = new ComboKit().createInventoryContents();
        edited[18] = null;
        assertNull(KitLayoutService.createPermutation(defaults, edited));
        edited = new ComboKit().createInventoryContents();
        edited[22] = edited[18].clone();
        assertNull(KitLayoutService.createPermutation(defaults, edited));
        edited = new ComboKit().createInventoryContents();
        edited[18].removeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL);
        assertNull(KitLayoutService.createPermutation(defaults, edited));
    }

    private static int total(ItemStack[] contents, Material material) {
        int result = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() == material) result += item.getAmount();
        }
        return result;
    }

    private static Material[] armorMaterials() {
        return new Material[] {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
                Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS};
    }

    private static void assertArmor(ItemStack armor, Material material) {
        assertEquals(material, armor.getType());
        assertEquals(1, armor.getAmount());
        assertEquals(0, armor.getDurability());
        assertEquals(4, armor.getEnchantmentLevel(Enchantment.PROTECTION_ENVIRONMENTAL));
        assertEquals(3, armor.getEnchantmentLevel(Enchantment.DURABILITY));
        assertEquals(2, armor.getEnchantments().size());
    }
}
