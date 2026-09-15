package com.poppy.practice.combat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Main-thread height tracking with a read-only filter for WindSpigot's async entity tracker. */
public final class ComboFallController implements Listener, Runnable {
    private static final long GROUNDED_ARM_TIMEOUT_TICKS = 20L;
    private final ComboCombatService combat;
    private final Logger logger;
    private final BooleanSupplier mainThread;
    private final ComboSlowFallMotion motion;
    private final Map<UUID, Entry> entries = new HashMap<UUID, Entry>();
    private final Map<UUID, Limit> publishedLimits = new ConcurrentHashMap<UUID, Limit>();
    private volatile long tick;
    private volatile boolean stopped;

    public ComboFallController(ComboCombatService combat, Logger logger) {
        this(combat, logger, Bukkit::isPrimaryThread);
    }

    ComboFallController(ComboCombatService combat, Logger logger, BooleanSupplier mainThread) {
        this(combat, logger, mainThread, new ComboSlowFallMotion());
    }

    ComboFallController(ComboCombatService combat, Logger logger, BooleanSupplier mainThread,
                        ComboSlowFallMotion motion) {
        this.combat = combat;
        this.logger = logger;
        this.mainThread = mainThread;
        this.motion = motion;
    }

    @Override
    public void run() {
        if (stopped) {
            return;
        }
        tick++;
        Set<UUID> participants = new HashSet<UUID>();
        for (Player player : combat.getParticipants()) {
            UUID id = player.getUniqueId();
            participants.add(id);
            try {
                Entry entry = sample(player);
                if (entry != null && entry.state.isFalling() && player.isOnline()) {
                    // Client gravity keeps advancing even without hits; send one gentle correction per tick.
                    // NPCs apply the same Y-only control inside their native movement tick instead.
                    motion.apply(player, entry.state.limitVertical(0.0D), tick);
                }
            } catch (RuntimeException failure) {
                forget(id);
                logger.log(Level.WARNING, "Could not sample Combo fall height for " + id, failure);
            }
        }
        entries.keySet().retainAll(participants);
        publishedLimits.keySet().retainAll(participants);
        motion.retain(participants);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (stopped || event.isCancelled() || !(event.getEntity() instanceof Player)
                || !mainThread.getAsBoolean()) {
            return;
        }
        // Fully absorbed damage (Combo gapples) still produces native knockback.
        Player player = (Player) event.getEntity();
        Entry entry = sample(player);
        if (entry != null && combat.getFallHeight(player.getUniqueId()) > 0.0D) {
            entry.state.arm();
            entry.lastKnockbackTick = tick;
            // A first hit may arrive when the participant is already above the threshold.
            sample(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        if (stopped || event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (mainThread.getAsBoolean()) {
            sample(player);
        }
        // Tracker callbacks may be off-thread. Never touch locations, worlds, or mutable state here.
        Limit limit = publishedLimits.get(id);
        if (limit == null || limit.token != combat.getParticipantToken(id)
                || combat.getFallHeight(id) <= 0.0D) {
            return;
        }
        Vector incoming = event.getVelocity();
        double vertical = -combat.getFallSpeed(id);
        if (Double.isFinite(incoming.getY()) && vertical < 0.0D && incoming.getY() != vertical) {
            Vector adjusted = incoming.clone();
            adjusted.setY(vertical);
            // Preserve the packet's real horizontal KB, including the bot's pending client motion.
            event.setVelocity(adjusted);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void rememberVelocity(PlayerVelocityEvent event) {
        if (stopped || event.isCancelled() || !combat.isApplied(event.getPlayer().getUniqueId())) {
            return;
        }
        Player player = event.getPlayer();
        int ping = player instanceof CraftPlayer ? ((CraftPlayer) player).getHandle().ping : 0;
        Vector velocity = event.getVelocity();
        motion.recordVelocity(player.getUniqueId(), velocity.getX(), velocity.getZ(), tick, ping);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (stopped || event.isCancelled() || event instanceof PlayerTeleportEvent
                || !mainThread.getAsBoolean() || !combat.isApplied(event.getPlayer().getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || from.getWorld() != to.getWorld()) {
            return;
        }
        sample(event.getPlayer());
        // Rotation-only packets must not erase the last actual client horizontal movement.
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            motion.recordMove(event.getPlayer().getUniqueId(), to.getX() - from.getX(),
                    to.getZ() - from.getZ(), tick);
        }
    }

    /** Called inside an NPC's movement tick, after pending knockback and before/after gravity. */
    public double controlledVerticalVelocity(Player player, double proposedY) {
        if (stopped || !mainThread.getAsBoolean()) {
            return proposedY;
        }
        Entry entry = sample(player);
        return entry == null ? proposedY : entry.state.limitVertical(proposedY);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!event.isCancelled()) {
            forget(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        stopped = true;
        entries.clear();
        publishedLimits.clear();
        motion.clear();
    }

    private Entry sample(Player player) {
        UUID id = player.getUniqueId();
        Object token = combat.getParticipantToken(id);
        if (token == null) {
            forget(id);
            return null;
        }
        Location location = player.getLocation();
        if (location == null || location.getWorld() == null || !Double.isFinite(location.getY())) {
            forget(id);
            return null;
        }
        UUID world = location.getWorld().getUID();
        Entry entry = entries.get(id);
        if (entry == null || entry.token != token || !entry.world.equals(world)) {
            motion.forget(id);
            entry = new Entry(token, world, initialGroundHeight(location));
            entries.put(id, entry);
        }
        boolean grounded = player.isOnGround();
        if (grounded && !entry.state.isFalling()
                && tick - entry.lastKnockbackTick > GROUNDED_ARM_TIMEOUT_TICKS) {
            // A hit that never caused takeoff must not arm a much later ordinary jump.
            entry.state.reset(location.getY());
        }
        entry.state.sample(location.getY(), grounded, combat.getFallHeight(id), combat.getFallSpeed(id), tick);
        if (entry.state.isFalling()) {
            publishedLimits.put(id, new Limit(token));
        } else {
            publishedLimits.remove(id);
        }
        return entry;
    }

    private void forget(UUID id) {
        entries.remove(id);
        publishedLimits.remove(id);
        motion.forget(id);
    }

    private static double initialGroundHeight(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int z = location.getBlockZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return location.getY();
        }
        int top = Math.min(world.getMaxHeight() - 1, (int) Math.floor(location.getY() - 0.001D));
        // Only initialize the baseline; ordinary tracking uses actual grounded feet height.
        for (int y = top; y >= 0 && top - y <= 64; y--) {
            if (world.getBlockAt(x, y, z).getType().isSolid()) {
                return Math.min(location.getY(), y + 1.0D);
            }
        }
        return location.getY();
    }

    private static final class Entry {
        private final Object token;
        private final UUID world;
        private final ComboFallState state;
        private long lastKnockbackTick;

        private Entry(Object token, UUID world, double groundY) {
            this.token = token;
            this.world = world;
            this.state = new ComboFallState(groundY);
        }
    }

    private static final class Limit {
        private final Object token;

        private Limit(Object token) {
            this.token = token;
        }
    }
}
