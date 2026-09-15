package com.poppy.practice.ui;

import com.poppy.practice.kit.BoxingKit;
import com.poppy.practice.kit.ComboKit;
import com.poppy.practice.kit.NoDebuffKit;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KitSelectionMenuTest {
    @Test
    public void botSelectorHasItsOwnTitleAndExplainsTheTwoStepSetup() {
        assertEquals("Bot Fight | Select a Kit", KitSelectionMenu.titleFor(KitSelectionMenu.Purpose.BOT));
        String footer = Arrays.toString(KitSelectionMenu.footerLoreFor(KitSelectionMenu.Purpose.BOT));
        assertTrue(footer.contains("Step 1: Choose NoDebuff, Boxing or Combo"));
        assertTrue(footer.contains("Step 2: Adjust the bot settings"));
        assertTrue(footer.contains("Then click Start"));
        assertTrue(footer.contains("Choosing a kit does not start a match"));
    }

    @Test
    public void botChoicesLeadToSettingsInsteadOfStartingMatchesOrJoiningQueues() {
        for (com.poppy.practice.kit.Kit kit : Arrays.asList(new NoDebuffKit(), new BoxingKit(), new ComboKit())) {
            String[] lore = KitSelectionMenu.loreFor(KitSelectionMenu.Purpose.BOT, kit);
            assertEquals("&eLeft Click: &fContinue to bot settings", lore[lore.length - 1]);
            assertFalse(Arrays.toString(lore).contains("Join queue"));
        }
        String boxing = Arrays.toString(KitSelectionMenu.loreFor(KitSelectionMenu.Purpose.BOT, new BoxingKit()));
        assertTrue(boxing.contains("100 hits"));
        assertTrue(boxing.contains("No health damage"));
        assertTrue(boxing.contains("No healing potions or ender pearls"));
        String nodebuff = Arrays.toString(KitSelectionMenu.loreFor(KitSelectionMenu.Purpose.BOT, new NoDebuffKit()));
        assertTrue(nodebuff.contains("Healing potions and ender pearls enabled"));
    }

    @Test
    public void comboBotChoiceExplainsAppleHealingAndItsIndependentCombatRules() {
        String[] lore = KitSelectionMenu.loreFor(KitSelectionMenu.Purpose.BOT, new ComboKit());
        String text = Arrays.toString(lore);

        assertTrue(text.contains("dedicated knockback"));
        assertTrue(text.contains("Separate hit-invulnerability settings"));
        assertTrue(text.contains("enchanted golden apples to heal"));
        assertTrue(text.contains("Two diamond armor sets"));
        assertTrue(text.contains("Sharpness V"));
        assertTrue(text.contains("8 seconds"));
        assertFalse(text.contains("NoDebuff"));
        assertFalse(text.contains("Join queue"));
        assertEquals("&eLeft Click: &fContinue to bot settings", lore[lore.length - 1]);
    }

    @Test
    public void existingSelectorTitlesAndFootersRemainUnchanged() {
        assertEquals("Queue | Select a Kit", KitSelectionMenu.titleFor(KitSelectionMenu.Purpose.QUEUE));
        assertEquals("Kit Editor | Select a Kit", KitSelectionMenu.titleFor(KitSelectionMenu.Purpose.EDIT));
        assertEquals("&7Choose a kit to enter matchmaking.",
                KitSelectionMenu.footerLoreFor(KitSelectionMenu.Purpose.QUEUE)[0]);
        assertEquals("&7Item types and amounts stay unchanged.",
                KitSelectionMenu.footerLoreFor(KitSelectionMenu.Purpose.EDIT)[1]);
    }

    @Test
    public void boxingQueueExplainsVictoryConditionAndEquipment() {
        String[] lore = KitSelectionMenu.loreFor(KitSelectionMenu.Purpose.QUEUE, new BoxingKit());

        assertTrue(Arrays.toString(lore).contains("100 hits"));
        assertTrue(Arrays.toString(lore).contains("No health damage"));
        assertTrue(Arrays.toString(lore).contains("Knockback is enabled"));
        assertTrue(Arrays.toString(lore).contains("permanent Speed II"));
        assertEquals("&eLeft Click: &fJoin queue", lore[lore.length - 1]);
        assertEquals(18, KitSelectionMenu.sizeFor(2));
    }

    @Test
    public void boxingEditorExplainsThatOnlyPositionsCanChange() {
        String[] lore = KitSelectionMenu.loreFor(KitSelectionMenu.Purpose.EDIT, new BoxingKit());

        assertTrue(Arrays.toString(lore).contains("Change item positions only"));
        assertEquals("&eLeft Click: &fEdit layout", lore[lore.length - 1]);
    }

    @Test
    public void noDebuffRetainsItsExistingDescriptions() {
        String[] lore = KitSelectionMenu.loreFor(KitSelectionMenu.Purpose.QUEUE, new NoDebuffKit());

        assertEquals(3, lore.length);
        assertEquals("&7Find a player using this kit.", lore[0]);
        assertEquals("&eLeft Click: &fJoin queue", lore[2]);
    }

    @Test
    public void comboQueueExplainsItsIndependentCombatRulesAndEquipment() {
        String[] lore = KitSelectionMenu.loreFor(KitSelectionMenu.Purpose.QUEUE, new ComboKit());
        String text = Arrays.toString(lore);

        assertTrue(text.contains("dedicated knockback"));
        assertTrue(text.contains("Separate hit-invulnerability settings"));
        assertTrue(text.contains("Two diamond armor sets"));
        assertTrue(text.contains("Sharpness V"));
        assertTrue(text.contains("Speed II"));
        assertTrue(text.contains("enchanted golden apples"));
        assertTrue(text.contains("8 seconds"));
        assertEquals("&eLeft Click: &fJoin queue", lore[lore.length - 1]);
        assertEquals(18, KitSelectionMenu.sizeFor(3));
    }

    @Test
    public void comboEditorOnlyDescribesItsLayoutAndPreservesFixedEquipment() {
        String[] lore = KitSelectionMenu.loreFor(KitSelectionMenu.Purpose.EDIT, new ComboKit());
        String text = Arrays.toString(lore);

        assertTrue(text.contains("Change item positions only"));
        assertTrue(text.contains("Item types, amounts and enchants stay unchanged"));
        assertFalse(text.contains("Join queue"));
        assertFalse(text.contains("knockback"));
        assertEquals("&eLeft Click: &fEdit layout", lore[lore.length - 1]);
    }
}
