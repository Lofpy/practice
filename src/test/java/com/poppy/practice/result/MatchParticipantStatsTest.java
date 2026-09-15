package com.poppy.practice.result;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class MatchParticipantStatsTest {
    @Test
    public void recordsCombatStatistics() {
        MatchParticipantStats stats = new MatchParticipantStats();

        stats.recordMeleeHit(false);
        stats.recordMeleeHit(true);
        stats.recordGuard();

        assertEquals(2, stats.getHits());
        assertEquals(1, stats.getCriticals());
        assertEquals(1, stats.getGuards());
    }

    @Test
    public void limitsGuardsToNineteenPerMatch() {
        MatchParticipantStats stats = new MatchParticipantStats();

        for (int attempt = 0; attempt < 25; attempt++) {
            stats.recordGuard();
        }

        assertEquals(19, stats.getGuards());
    }

    @Test
    public void recordsPotionAccuracyHealingAndOverheal() {
        MatchParticipantStats stats = new MatchParticipantStats();
        stats.recordHealingPotionThrown();
        stats.recordHealingPotionResult(6.0D, 2.0D, 1.5D);
        stats.recordHealingPotionThrown();
        stats.recordHealingPotionResult(0.0D, 0.0D, 0.0D);

        assertEquals(2, stats.getHealingPotionsThrown());
        assertEquals(2, stats.getFinalHealingPotionsMissed());
        assertEquals(37.5D, stats.getPotionAccuracyPercent(), 0.001D);
        assertEquals(16.0D, stats.getExpectedHealingHealth(), 0.001D);
        assertEquals(6.0D, stats.getHealedHealth(), 0.001D);
        assertEquals(2.0D, stats.getOverhealedHealth(), 0.001D);
        assertEquals(1.5D, stats.getOpponentHealedHealth(), 0.001D);
    }

    @Test
    public void unresolvedPotionIsAMissWhenMatchEnds() {
        MatchParticipantStats stats = new MatchParticipantStats();
        stats.recordHealingPotionThrown();

        assertEquals(1, stats.getFinalHealingPotionsMissed());
        assertEquals(0.0D, stats.getPotionAccuracyPercent(), 0.001D);
    }

    @Test
    public void fullEightHealthPotionIsNotAMiss() {
        MatchParticipantStats stats = new MatchParticipantStats();
        stats.recordHealingPotionThrown();
        stats.recordHealingPotionResult(8.0D, 0.0D, 0.0D);

        assertEquals(0, stats.getFinalHealingPotionsMissed());
        assertEquals(100.0D, stats.getPotionAccuracyPercent(), 0.001D);
    }

    @Test
    public void negativeHealingValuesCannotReduceTotals() {
        MatchParticipantStats stats = new MatchParticipantStats();
        stats.recordHealingPotionThrown();
        stats.recordHealingPotionResult(-2.0D, -4.0D, -3.0D);

        assertEquals(0.0D, stats.getHealedHealth(), 0.001D);
        assertEquals(0.0D, stats.getOverhealedHealth(), 0.001D);
        assertEquals(0.0D, stats.getOpponentHealedHealth(), 0.001D);
    }

    @Test
    public void countsOnlySplashHealingTwoPotions() {
        ItemStack[] contents = new ItemStack[] {
                new ItemStack(Material.POTION, 3, (short) 16421),
                new ItemStack(Material.POTION, 2, (short) 8226),
                new ItemStack(Material.ENDER_PEARL, 16)
        };

        assertEquals(3, MatchParticipantSnapshot.countHealingPotions(contents));
    }
}
