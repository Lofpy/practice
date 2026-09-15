package com.poppy.practice.listener;

import com.poppy.practice.PracticePlugin;
import com.poppy.practice.bot.BotMatch;
import com.poppy.practice.bot.BotService;
import com.poppy.practice.match.Match;
import com.poppy.practice.match.MatchManager;
import com.poppy.practice.player.PlayerProfile;
import com.poppy.practice.player.PlayerState;
import com.poppy.practice.player.ProfileManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.Test;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** The player's actual bot match, not their lobby selection, controls pearl use. */
public class ComboBotPearlCooldownTest {
    @Test
    public void comboBotMatchUsesEightSecondsWithoutAnOrdinaryMatchEntry() throws Exception {
        Fixture fixture = new Fixture();
        fixture.profile.setSelectedKitId("nodebuff");
        fixture.botMatches.put(fixture.player.getUniqueId(), fixture.botMatch("combo"));

        assertNull(fixture.matches.getByPlayer(fixture.player.getUniqueId()));
        assertEquals(8, fixture.listener.configuredCooldownSeconds(fixture.player));
    }

    @Test
    public void noDebuffBotMatchRetainsConfiguredDefaultDespiteComboLobbySelection() throws Exception {
        Fixture fixture = new Fixture();
        fixture.config.set("match.ender-pearl-cooldown-seconds", 23);
        fixture.profile.setSelectedKitId("combo");
        fixture.botMatches.put(fixture.player.getUniqueId(), fixture.botMatch("nodebuff"));

        assertEquals(23, fixture.listener.configuredCooldownSeconds(fixture.player));
    }

    @Test
    public void endingComboBotMatchDoesNotLeakEightSecondsIntoLobbyOrNextKit() throws Exception {
        Fixture fixture = new Fixture();
        fixture.botMatches.put(fixture.player.getUniqueId(), fixture.botMatch("combo"));
        assertEquals(8, fixture.listener.configuredCooldownSeconds(fixture.player));

        fixture.botMatches.clear();
        fixture.profile.setSelectedKitId("combo");
        fixture.profile.setState(PlayerState.LOBBY);
        assertEquals(16, fixture.listener.configuredCooldownSeconds(fixture.player));

        fixture.botMatches.put(fixture.player.getUniqueId(), fixture.botMatch("nodebuff"));
        fixture.profile.setState(PlayerState.FIGHTING);
        assertEquals(16, fixture.listener.configuredCooldownSeconds(fixture.player));
    }

    @Test
    public void comboBotMatchDoesNotChangeAnotherPlayersNormalMatchCooldown() throws Exception {
        Fixture fixture = new Fixture();
        fixture.botMatches.put(fixture.player.getUniqueId(), fixture.botMatch("combo"));
        Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        fixture.matches.register(new Match(other.getUniqueId(), UUID.randomUUID(), "nodebuff", "other"));

        assertEquals(8, fixture.listener.configuredCooldownSeconds(fixture.player));
        assertEquals(16, fixture.listener.configuredCooldownSeconds(other));
    }

    private static final class Fixture {
        private final Player player = mock(Player.class);
        private final YamlConfiguration config = new YamlConfiguration();
        private final MatchManager matches = new MatchManager();
        private final Map<UUID, BotMatch> botMatches = new HashMap<UUID, BotMatch>();
        private final PlayerProfile profile;
        private final EnderPearlCooldownListener listener;

        private Fixture() throws Exception {
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            ProfileManager profiles = new ProfileManager();
            profile = profiles.create(player.getUniqueId());
            profile.setState(PlayerState.FIGHTING);
            PracticePlugin plugin = new ObjenesisStd().newInstance(PracticePlugin.class);
            setField(JavaPlugin.class, plugin, "newConfig", config);
            BotService bots = new ObjenesisStd().newInstance(BotService.class);
            setField(BotService.class, bots, "matchesByPlayer", botMatches);
            listener = new EnderPearlCooldownListener(plugin, profiles, matches, bots);
        }

        private BotMatch botMatch(String kitId) {
            BotMatch match = new BotMatch(player.getUniqueId(), UUID.randomUUID(), kitId, "bot-arena");
            match.markFighting();
            return match;
        }
    }

    private static void setField(Class<?> type, Object instance, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }
}
