package com.poppy.practice.bot;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BotListenerTest {
    @Test
    public void identifiesOnlyInstantHealthTwoOrHigher() {
        assertTrue(BotListener.isHealingTwo(Collections.singletonList(
                new PotionEffect(PotionEffectType.HEAL, 1, 1))));
        assertFalse(BotListener.isHealingTwo(Arrays.asList(
                new PotionEffect(PotionEffectType.HEAL, 1, 0),
                new PotionEffect(PotionEffectType.SPEED, 200, 1))));
        assertFalse(BotListener.isHealingTwo(Collections.<PotionEffect>emptyList()));
    }
}
