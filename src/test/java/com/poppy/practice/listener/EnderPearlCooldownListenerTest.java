package com.poppy.practice.listener;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.Test;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

public class EnderPearlCooldownListenerTest {
    @Test
    public void comboHasIndependentEightSecondCooldownAndExperienceProgress() {
        YamlConfiguration config = new YamlConfiguration();
        assertEquals(8, EnderPearlCooldownListener.configuredCooldownSeconds(config, "combo"));
        assertEquals(8, EnderPearlCooldownListener.configuredCooldownSeconds(config, "COMBO"));
        assertEquals(16, EnderPearlCooldownListener.configuredCooldownSeconds(config, "nodebuff"));
        assertEquals(16, EnderPearlCooldownListener.configuredCooldownSeconds(config, "boxing"));
        assertEquals(16, EnderPearlCooldownListener.configuredCooldownSeconds(config, null));
        assertEquals(8000L, EnderPearlCooldownListener.cooldownMillis(8));
        assertEquals(8L, EnderPearlCooldownListener.remainingSeconds(7999L));
        assertEquals(0.5F, EnderPearlCooldownListener.cooldownProgress(4000L, 8000L), 0.0001F);
    }

    @Test
    public void existingCooldownCustomizationDoesNotChangeCombo() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("match.ender-pearl-cooldown-seconds", 27);
        assertEquals(27, EnderPearlCooldownListener.configuredCooldownSeconds(config, "nodebuff"));
        assertEquals(8, EnderPearlCooldownListener.configuredCooldownSeconds(config, "combo"));
        config.set("match.ender-pearl-cooldown-seconds", -10);
        assertEquals(0, EnderPearlCooldownListener.configuredCooldownSeconds(config, "nodebuff"));
        assertEquals(8, EnderPearlCooldownListener.configuredCooldownSeconds(config, "combo"));
        config.set("match.ender-pearl-cooldown-seconds", 999);
        assertEquals(300, EnderPearlCooldownListener.configuredCooldownSeconds(config, null));
        assertEquals(8, EnderPearlCooldownListener.configuredCooldownSeconds(config, "combo"));
    }

    @Test
    public void deferredPearlCannotSurviveAMatchChangeOrReconnect() {
        UUID playerId = UUID.randomUUID();
        PlayerProfile original = new PlayerProfile(playerId);
        original.setState(PlayerState.FIGHTING);
        long session = original.getCombatSessionVersion();
        assertTrue(EnderPearlCooldownListener.isCurrentSession(original, original, session));
        PlayerProfile reconnected = new PlayerProfile(playerId);
        reconnected.setState(PlayerState.FIGHTING);
        assertFalse(EnderPearlCooldownListener.isCurrentSession(reconnected, original, session));
        original.setState(PlayerState.LOBBY);
        original.setState(PlayerState.FIGHTING);
        assertFalse(EnderPearlCooldownListener.isCurrentSession(original, original, session));
        assertFalse(EnderPearlCooldownListener.isCurrentSession(null, original, session));
    }

    @Test
    public void identifiesOnlyRightClickWithEnderPearl() {
        ItemStack pearl = new ItemStack(Material.ENDER_PEARL);

        assertTrue(EnderPearlCooldownListener.isEnderPearlUse(Action.RIGHT_CLICK_AIR, pearl));
        assertTrue(EnderPearlCooldownListener.isEnderPearlUse(Action.RIGHT_CLICK_BLOCK, pearl));
        assertFalse(EnderPearlCooldownListener.isEnderPearlUse(Action.LEFT_CLICK_AIR, pearl));
        assertFalse(EnderPearlCooldownListener.isEnderPearlUse(Action.RIGHT_CLICK_AIR,
                new ItemStack(Material.DIAMOND_SWORD)));
    }

    @Test
    public void calculatesSixteenSecondCooldownAndRoundsDisplayUp() {
        assertEquals(16000L, EnderPearlCooldownListener.cooldownMillis(16));
        assertEquals(16000L, EnderPearlCooldownListener.remainingMillis(26000L, 10000L));
        assertEquals(16L, EnderPearlCooldownListener.remainingSeconds(15999L));
        assertEquals(1L, EnderPearlCooldownListener.remainingSeconds(1L));
        assertEquals(0L, EnderPearlCooldownListener.remainingSeconds(0L));
        assertEquals(1.0F, EnderPearlCooldownListener.cooldownProgress(16000L, 16000L), 0.0001F);
        assertEquals(0.5F, EnderPearlCooldownListener.cooldownProgress(8000L, 16000L), 0.0001F);
        assertEquals(0.0F, EnderPearlCooldownListener.cooldownProgress(0L, 16000L), 0.0001F);
    }

    @Test
    public void convertsCapturedClientLookIntoPearlVelocity() {
        Vector south = EnderPearlCooldownListener.directionFor(0.0F, 0.0F, 1.5D);
        assertEquals(0.0D, south.getX(), 0.000001D);
        assertEquals(0.0D, south.getY(), 0.000001D);
        assertEquals(1.5D, south.getZ(), 0.000001D);

        Vector up = EnderPearlCooldownListener.directionFor(90.0F, -90.0F, 1.5D);
        assertEquals(0.0D, up.getX(), 0.000001D);
        assertEquals(1.5D, up.getY(), 0.000001D);
        assertEquals(0.0D, up.getZ(), 0.000001D);
    }

    @Test
    public void restoresPearlConsumedByCooldownCancellation() {
        ItemStack[] contents = new ItemStack[36];
        contents[1] = new ItemStack(Material.ENDER_PEARL, 15);
        assertTrue(EnderPearlCooldownListener.restoreEnderPearl(contents, 1));
        assertEquals(16, contents[1].getAmount());

        contents[1] = null;
        assertTrue(EnderPearlCooldownListener.restoreEnderPearl(contents, 1));
        assertEquals(1, contents[1].getAmount());
        assertEquals(Material.ENDER_PEARL, contents[1].getType());
        assertNull(contents[2]);
    }
}
