package com.poppy.practice.combat;

import com.poppy.practice.config.ComboConfig;
import dev.cobblesword.nachospigot.knockback.KnockbackProfile;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Main-thread lifecycle for temporary Combo combat settings. */
public final class ComboCombatService {
    private final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<UUID, Snapshot>();
    private ComboConfig config;

    public ComboCombatService(ComboConfig config) {
        reload(config);
    }

    /** Existing matches retain their own profile and counter until they end. */
    public void reload(ComboConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Combo configuration must not be null");
        }
        this.config = config;
    }

    /** Effective immutable settings for future matches, including the last successful reload. */
    public ComboConfig getConfiguration() {
        return config;
    }

    /** Main-thread snapshot of participants; changes to the returned list do not affect tracking. */
    public List<Player> getParticipants() {
        List<Player> participants = new ArrayList<Player>(snapshots.size());
        for (Snapshot snapshot : snapshots.values()) {
            participants.add(snapshot.player);
        }
        return participants;
    }

    /** Async-safe identity token, replaced when this player enters a new Combo match. */
    public Object getParticipantToken(UUID playerId) {
        return playerId == null ? null : snapshots.get(playerId);
    }

    /** Height captured for this active participant; ordinary players have no fall restriction. */
    public double getFallHeight(UUID playerId) {
        Snapshot snapshot = playerId == null ? null : snapshots.get(playerId);
        return snapshot == null ? 0.0D : snapshot.fallHeight;
    }

    /** Captured slow-fall speed in blocks per tick; zero means no tracked Combo participant. */
    public double getFallSpeed(UUID playerId) {
        Snapshot snapshot = playerId == null ? null : snapshots.get(playerId);
        return snapshot == null ? 0.0D : snapshot.fallSpeed;
    }

    /**
     * Updates all tracked Combo participants on the main thread. The returned handle may be
     * rolled back immediately, on that same thread, if persisting the settings fails.
     * Original pre-match combat settings are deliberately not modified.
     */
    public LiveUpdate updateLive(ComboConfig updated) {
        if (updated == null) {
            throw new IllegalArgumentException("Combo configuration must not be null");
        }
        List<LiveState> states = new ArrayList<LiveState>();
        // Capture every participant before any mutation, including potentially failing getters.
        for (Snapshot snapshot : snapshots.values()) {
            Player player = snapshot.player;
            states.add(new LiveState(snapshot, player.getKnockbackProfile(),
                    player.getMaximumNoDamageTicks(), readHurtCounter(player),
                    updated.newKnockbackProfile()));
        }
        LiveUpdate update = new LiveUpdate(config, states);
        try {
            int newMaximum = updated.getMaximumNoDamageTicks();
            for (LiveState state : states) {
                // A setter can mutate the entity and then throw, so include it in rollback first.
                state.touched = true;
                state.player.setKnockbackProfile(state.updatedProfile);
                if (state.maximumNoDamageTicks != newMaximum) {
                    state.player.setMaximumNoDamageTicks(newMaximum);
                    state.player.setNoDamageTicks(remapHurtCounter(state.maximumNoDamageTicks,
                            state.noDamageTicks, newMaximum));
                }
                state.snapshot.fallHeight = updated.getFallHeight();
                state.snapshot.fallSpeed = updated.getFallSpeed();
            }
            config = updated;
            return update;
        } catch (RuntimeException failure) {
            try {
                update.rollback();
            } catch (RuntimeException restorationFailure) {
                if (failure != restorationFailure) {
                    failure.addSuppressed(restorationFailure);
                }
            }
            throw failure;
        }
    }

    private static int readHurtCounter(Player player) {
        if (player instanceof CraftPlayer) {
            // CraftPlayer's getter also includes spawn protection, which is not a hit timer.
            // Never copy that separate protection into the timer we adjust or restore.
            return ((CraftPlayer) player).getHandle().noDamageTicks;
        }
        return player.getNoDamageTicks();
    }

    private static int remapHurtCounter(int oldMaximum, int oldRemaining, int newMaximum) {
        if (oldRemaining <= 0 || oldMaximum <= 0 || newMaximum <= 0) {
            return 0;
        }
        long elapsed = Math.max(0L, (long) oldMaximum - oldRemaining);
        int remaining = (int) Math.max(0L, (long) newMaximum - elapsed);
        // An already hittable entity must not become immune merely by changing the interval.
        if (oldRemaining <= oldMaximum / 2) {
            remaining = Math.min(remaining, newMaximum / 2);
        }
        return remaining;
    }

    public final class LiveUpdate {
        private final ComboConfig previousConfig;
        private final List<LiveState> states;
        private boolean rolledBack;

        private LiveUpdate(ComboConfig previousConfig, List<LiveState> states) {
            this.previousConfig = previousConfig;
            this.states = states;
        }

        /** Restores exact active profiles and counters; successful rollback is idempotent. */
        public void rollback() {
            if (rolledBack) {
                return;
            }
            config = previousConfig;
            RuntimeException failure = null;
            for (LiveState state : states) {
                if (!state.touched) {
                    continue;
                }
                state.snapshot.fallHeight = state.fallHeight;
                state.snapshot.fallSpeed = state.fallSpeed;
                try {
                    state.player.setKnockbackProfile(state.knockbackProfile);
                } catch (RuntimeException exception) {
                    failure = aggregate(failure, exception);
                }
                try {
                    state.player.setMaximumNoDamageTicks(state.maximumNoDamageTicks);
                } catch (RuntimeException exception) {
                    failure = aggregate(failure, exception);
                }
                try {
                    state.player.setNoDamageTicks(state.noDamageTicks);
                } catch (RuntimeException exception) {
                    failure = aggregate(failure, exception);
                }
            }
            if (failure != null) {
                throw failure;
            }
            rolledBack = true;
        }
    }

    private static RuntimeException aggregate(RuntimeException first, RuntimeException next) {
        if (first == null) {
            return next;
        }
        if (first != next) {
            first.addSuppressed(next);
        }
        return first;
    }

    public void apply(Player player) {
        if (player == null) {
            throw new IllegalArgumentException("Combo participant must not be null");
        }
        UUID playerId = player.getUniqueId();
        if (snapshots.containsKey(playerId)) {
            return;
        }
        Snapshot previous = new Snapshot(player, player.getKnockbackProfile(),
                player.getMaximumNoDamageTicks(), config.getFallHeight(), config.getFallSpeed());
        KnockbackProfile profile = config.newKnockbackProfile();
        snapshots.put(playerId, previous);
        try {
            player.setKnockbackProfile(profile);
            player.setMaximumNoDamageTicks(config.getMaximumNoDamageTicks());
            player.setNoDamageTicks(0);
        } catch (RuntimeException failure) {
            try {
                restore(playerId);
            } catch (RuntimeException restorationFailure) {
                failure.addSuppressed(restorationFailure);
            }
            throw failure;
        }
    }

    public boolean isApplied(UUID playerId) {
        return playerId != null && snapshots.containsKey(playerId);
    }

    public void restore(Player player) {
        if (player != null) {
            restore(player.getUniqueId());
        }
    }

    /** Uses the captured entity even if Bukkit no longer finds the disconnected player. */
    public void restore(UUID playerId) {
        if (playerId == null) {
            return;
        }
        Snapshot previous = snapshots.get(playerId);
        if (previous == null) {
            return;
        }
        RuntimeException failure = null;
        try {
            previous.player.setKnockbackProfile(previous.knockbackProfile);
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            previous.player.setMaximumNoDamageTicks(previous.maximumNoDamageTicks);
            // A Combo counter must not leak into the lobby or the next kit.
            previous.player.setNoDamageTicks(0);
        } catch (RuntimeException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            // Retain the snapshot so shutdown/another cleanup path can retry.
            throw failure;
        }
        snapshots.remove(playerId);
    }

    public void shutdown() {
        RuntimeException failure = null;
        for (UUID playerId : new ArrayList<UUID>(snapshots.keySet())) {
            try {
                restore(playerId);
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static final class LiveState {
        private final Snapshot snapshot;
        private final Player player;
        private final KnockbackProfile knockbackProfile;
        private final int maximumNoDamageTicks;
        private final int noDamageTicks;
        private final KnockbackProfile updatedProfile;
        private final double fallHeight;
        private final double fallSpeed;
        private boolean touched;

        private LiveState(Snapshot snapshot, KnockbackProfile knockbackProfile,
                          int maximumNoDamageTicks, int noDamageTicks,
                          KnockbackProfile updatedProfile) {
            this.snapshot = snapshot;
            this.player = snapshot.player;
            this.knockbackProfile = knockbackProfile;
            this.maximumNoDamageTicks = maximumNoDamageTicks;
            this.noDamageTicks = noDamageTicks;
            this.updatedProfile = updatedProfile;
            this.fallHeight = snapshot.fallHeight;
            this.fallSpeed = snapshot.fallSpeed;
        }
    }

    private static final class Snapshot {
        private final Player player;
        private final KnockbackProfile knockbackProfile;
        private final int maximumNoDamageTicks;
        private volatile double fallHeight;
        private volatile double fallSpeed;

        private Snapshot(Player player, KnockbackProfile knockbackProfile,
                         int maximumNoDamageTicks, double fallHeight, double fallSpeed) {
            this.player = player;
            this.knockbackProfile = knockbackProfile;
            this.maximumNoDamageTicks = maximumNoDamageTicks;
            this.fallHeight = fallHeight;
            this.fallSpeed = fallSpeed;
        }
    }
}
