package com.poppy.practice.listener;

import com.poppy.practice.PracticePlugin;
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
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ComboPearlCooldownTest {
    @Test
    public void actualMatchDeterminesCooldownNotTheSelectedLobbyKit() throws Exception {
        Fixture fixture = new Fixture();
        fixture.profile.setSelectedKitId("combo");
        fixture.profile.setState(PlayerState.FIGHTING);
        Match noDebuff = fixture.match("nodebuff");
        assertTrue(fixture.matches.register(noDebuff));
        assertEquals(16, fixture.listener.configuredCooldownSeconds(fixture.player));

        fixture.matches.remove(noDebuff);
        fixture.profile.setSelectedKitId("nodebuff");
        assertTrue(fixture.matches.register(fixture.match("combo")));
        assertEquals(8, fixture.listener.configuredCooldownSeconds(fixture.player));
    }

    @Test
    public void leavingComboRestoresDefaultCooldownWithoutChangingOtherParticipants() throws Exception {
        Fixture fixture = new Fixture();
        Match combo = fixture.match("combo");
        assertTrue(fixture.matches.register(combo));
        assertEquals(8, fixture.listener.configuredCooldownSeconds(fixture.player));
        Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        assertEquals(16, fixture.listener.configuredCooldownSeconds(other));

        fixture.matches.remove(combo);
        fixture.profile.setSelectedKitId("combo");
        assertEquals(16, fixture.listener.configuredCooldownSeconds(fixture.player));
        assertTrue(fixture.matches.register(fixture.match("nodebuff")));
        assertEquals(16, fixture.listener.configuredCooldownSeconds(fixture.player));
    }

    private static final class Fixture {
        private final Player player = mock(Player.class);
        private final MatchManager matches = new MatchManager();
        private final PlayerProfile profile;
        private final EnderPearlCooldownListener listener;

        private Fixture() throws Exception {
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            ProfileManager profiles = new ProfileManager();
            profile = profiles.create(player.getUniqueId());
            PracticePlugin plugin = new ObjenesisStd().newInstance(PracticePlugin.class);
            Field config = JavaPlugin.class.getDeclaredField("newConfig");
            config.setAccessible(true);
            config.set(plugin, new YamlConfiguration());
            listener = new EnderPearlCooldownListener(plugin, profiles, matches);
        }

        private Match match(String kitId) {
            return new Match(player.getUniqueId(), UUID.randomUUID(), kitId, "one");
        }
    }
}
