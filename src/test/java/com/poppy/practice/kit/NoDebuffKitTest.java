package com.poppy.practice.kit;

import org.bukkit.Material;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NoDebuffKitTest {
    @Test
    public void usesSplashHealingTwoAsQueueIcon() {
        NoDebuffKit kit = new NoDebuffKit();

        assertEquals(Material.POTION, kit.getIcon());
        assertEquals((short) 16421, kit.getIconDurability());
    }
}
