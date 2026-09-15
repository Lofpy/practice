package com.poppy.practice.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class DamageDebugServiceTest {
    @Test
    public void actualDamageCannotExceedRemainingHealth() {
        assertEquals(4.0D, DamageDebugService.actualDamage(7.0D, 4.0D), 0.0001D);
        assertEquals(2.5D, DamageDebugService.actualDamage(2.5D, 20.0D), 0.0001D);
    }

    @Test
    public void actualDamageRejectsNegativeValues() {
        assertEquals(0.0D, DamageDebugService.actualDamage(-1.0D, 20.0D), 0.0001D);
        assertEquals(0.0D, DamageDebugService.actualDamage(4.0D, -1.0D), 0.0001D);
    }

    @Test
    public void healthFormattingUsesAtMostTwoDecimals() {
        assertEquals("20.0", DamageDebugService.formatHealth(20.0D));
        assertEquals("3.5", DamageDebugService.formatHealth(3.5D));
        assertEquals("2.35", DamageDebugService.formatHealth(2.345D));
    }
}
