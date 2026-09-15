package com.poppy.practice.cosmetic;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MatchFinishEffectsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void defaultLightningIsVisualAndSilentWithOnlyNearbyThunder() {
        Fixture fixture = new Fixture();
        Location input = new Location(fixture.world, 0, 64, 0);
        fixture.effects.play(null, input);
        assertEquals(1, fixture.lightning);
        assertTrue(fixture.silent);
        assertEquals(Arrays.asList(Sound.AMBIENCE_THUNDER), fixture.nearSounds);
        assertTrue(fixture.farSounds.isEmpty());
        assertEquals(64, input.getY(), 0);
        assertNull(fixture.particle);
    }

    @Test
    public void explosionUsesParticlesNotAWorldExplosion() {
        Fixture fixture = new Fixture();
        assertTrue(fixture.preferences.setKillEffect(fixture.winnerId, KillEffect.EXPLOSION));
        fixture.effects.play(fixture.winner, new Location(fixture.world, 0, 64, 0));
        assertEquals(0, fixture.lightning);
        assertEquals(Effect.EXPLOSION_LARGE, fixture.particle);
        assertEquals(3, fixture.particleCount);
        assertEquals(48, fixture.radius);
        assertEquals(Arrays.asList(Sound.EXPLODE), fixture.nearSounds);
        assertTrue(fixture.farSounds.isEmpty());
    }

    @Test
    public void redstoneUsesACompactCloudOfDefaultRedDust() {
        Fixture fixture = new Fixture();
        assertTrue(fixture.preferences.setKillEffect(fixture.winnerId, KillEffect.REDSTONE));
        Location input = new Location(fixture.world, 0, 64, 0);
        fixture.effects.play(fixture.winner, input);
        assertEquals(Effect.COLOURED_DUST, fixture.particle);
        assertEquals(65, fixture.particleCount);
        assertEquals(0.0F, fixture.particleSpeed, 0.0F);
        assertEquals(48, fixture.radius);
        assertEquals(0, fixture.lightning);
        assertTrue(fixture.nearSounds.isEmpty());
        assertEquals(64, input.getY(), 0);
    }

    @Test
    public void invalidLocationsAndUnloadedChunksDoNotCreateEffectsOrLoadTerrain() {
        Fixture fixture = new Fixture();
        fixture.effects.play(null, null);
        fixture.effects.play(null, new Location(null, 0, 64, 0));
        fixture.effects.play(null, new Location(fixture.world, Double.NaN, 64, 0));
        fixture.loaded = false;
        fixture.effects.play(null, new Location(fixture.world, 0, 64, 0));
        assertEquals(0, fixture.lightning);
        assertNull(fixture.particle);
        assertTrue(fixture.nearSounds.isEmpty());
    }

    private final class Fixture {
        private final UUID winnerId = UUID.randomUUID();
        private final PreferencesService preferences = new PreferencesService(temporary.getRoot(), Logger.getAnonymousLogger());
        private final MatchFinishEffects effects = new MatchFinishEffects(preferences);
        private int lightning;
        private boolean silent;
        private boolean loaded = true;
        private Effect particle;
        private int particleCount;
        private float particleSpeed;
        private int radius;
        private final List<Sound> nearSounds = new ArrayList<Sound>();
        private final List<Sound> farSounds = new ArrayList<Sound>();
        private final World.Spigot spigot = new World.Spigot() {
            @Override public LightningStrike strikeLightningEffect(Location location, boolean isSilent) {
                lightning++;
                silent = isSilent;
                return null;
            }
            @Override public void playEffect(Location location, Effect effect, int id, int data,
                                             float offsetX, float offsetY, float offsetZ, float speed,
                                             int count, int viewRadius) {
                particle = effect;
                particleCount = count;
                particleSpeed = speed;
                radius = viewRadius;
            }
        };
        private final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[] { World.class }, (proxy, method, arguments) -> {
                    if (method.getName().equals("spigot")) return spigot;
                    if (method.getName().equals("isChunkLoaded")) return loaded;
                    if (method.getName().equals("getPlayers")) return Arrays.asList(viewer(2, nearSounds), viewer(1000, farSounds));
                    if (method.getName().equals("equals")) return proxy == arguments[0];
                    throw new AssertionError("Unexpected world mutation/access: " + method.getName());
                });
        private final Player winner = createWinner();

        private Player createWinner() {
            Player mocked = mock(Player.class);
            when(mocked.getUniqueId()).thenReturn(winnerId);
            return mocked;
        }

        private Player viewer(final double x, final List<Sound> sounds) {
            Player mocked = mock(Player.class);
            when(mocked.getLocation()).thenReturn(new Location(world, x, 64, 0));
            doAnswer(invocation -> {
                sounds.add((Sound) invocation.getArguments()[1]);
                return null;
            }).when(mocked).playSound(any(Location.class), any(Sound.class), anyFloat(), anyFloat());
            return mocked;
        }
    }
}
