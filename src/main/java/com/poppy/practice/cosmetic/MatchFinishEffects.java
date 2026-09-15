package com.poppy.practice.cosmetic;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Uses only visual lightning and particles: never creates an explosion or damages an entity. */
public final class MatchFinishEffects {
    private static final int VIEW_RADIUS = 48;
    private final PreferencesService preferences;

    public MatchFinishEffects(PreferencesService preferences) {
        this.preferences = preferences;
    }

    public void play(Player winner, Location loserLocation) {
        if (loserLocation == null || loserLocation.getWorld() == null
                || !Double.isFinite(loserLocation.getX()) || !Double.isFinite(loserLocation.getY())
                || !Double.isFinite(loserLocation.getZ())) return;
        Location location = loserLocation.clone();
        World world = location.getWorld();
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return;
        KillEffect effect = preferences.getKillEffect(winner == null ? null : winner.getUniqueId());
        if (effect == KillEffect.LIGHTNING) {
            // The silent effect overload avoids broadcasting thunder across distant arenas.
            world.spigot().strikeLightningEffect(location, true);
            playNearbySound(world, location, Sound.AMBIENCE_THUNDER, 1.0F);
        } else if (effect == KillEffect.EXPLOSION) {
            world.spigot().playEffect(location.clone().add(0, 0.8D, 0), Effect.EXPLOSION_LARGE,
                    0, 0, 0.25F, 0.4F, 0.25F, 0.0F, 3, VIEW_RADIUS);
            playNearbySound(world, location, Sound.EXPLODE, 0.9F);
        } else {
            // In the 1.8 protocol, zero particle speed gives coloured dust its default red colour.
            world.spigot().playEffect(location.clone().add(0, 1.0D, 0), Effect.COLOURED_DUST,
                    0, 0, 0.5F, 0.75F, 0.5F, 0.0F, 65, VIEW_RADIUS);
        }
    }

    private void playNearbySound(World world, Location location, Sound sound, float pitch) {
        for (Player viewer : world.getPlayers()) {
            Location viewedFrom = viewer.getLocation();
            if (viewedFrom.getWorld() == world
                    && viewedFrom.distanceSquared(location) <= VIEW_RADIUS * VIEW_RADIUS) {
                viewer.playSound(location, sound, 1.0F, pitch);
            }
        }
    }
}
