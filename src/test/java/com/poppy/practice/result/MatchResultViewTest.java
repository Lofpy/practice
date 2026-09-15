package com.poppy.practice.result;

import org.junit.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class MatchResultViewTest {
    @Test
    public void allInventoryItemsRemainUniqueAndHotbarAppearsBelowStorage() {
        Set<Integer> displayed = new HashSet<Integer>();
        for (int slot = 0; slot < 36; slot++) {
            int target = MatchResultView.inventorySlot(slot);
            assertTrue(target >= 0 && target < 36);
            assertTrue(displayed.add(target));
            if (slot < 9) {
                assertEquals(27 + slot, target);
            }
        }
        assertEquals(36, displayed.size());
    }

    @Test
    public void englishStatisticsUseStableDecimalFormattingAcrossServerLocales() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("37.5", MatchResultView.format(37.5D));
            assertEquals("01:05", MatchResultView.duration(65));
            assertEquals("00:00", MatchResultView.duration(-1));
        } finally {
            Locale.setDefault(original);
        }
    }
}
