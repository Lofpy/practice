package com.poppy.practice.combat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class EnderPearlDamageTest {
    @Test
    public void usesVanillaPearlDamageAmount() {
        assertEquals(5.0F, EnderPearlDamage.DAMAGE, 0.0F);
    }
}
